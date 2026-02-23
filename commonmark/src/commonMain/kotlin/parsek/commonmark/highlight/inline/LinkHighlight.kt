package parsek.commonmark.highlight.inline

import parsek.Parser
import parsek.Success
import parsek.commonmark.ast.Inline
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import parsek.commonmark.highlight.emit
import parsek.commonmark.parser.block.parseLinkDestination
import parsek.commonmark.parser.block.parseLinkLabel
import parsek.commonmark.parser.block.parseLinkTitle
import parsek.commonmark.parser.inline.LinkRefResolver
import parsek.commonmark.parser.inline.pImage
import parsek.commonmark.parser.inline.pLink

/**
 * Highlight wrapper for a CommonMark inline link.
 *
 * On success, emits:
 * - [TokenType.LinkBracket] for the opening `[` and closing `]`
 * - For inline links: [TokenType.LinkParen] for `(` and `)`,
 *   [TokenType.LinkDestination] for the destination,
 *   [TokenType.LinkTitle] for the title (if present)
 * - For reference links: [TokenType.LinkBracket] for the reference `[label]`
 */
fun pLinkHighlight(
    contentParser: (chars: List<Char>, userContext: SpanSink) -> List<Inline>,
    resolveRef: LinkRefResolver = { null },
): Parser<Char, Inline, SpanSink> =
    Parser { input ->
        val start = input.index
        val result = pLink(contentParser, resolveRef)(input)
        if (result !is Success) return@Parser result

        val chars = input.input
        val sink = input.userContext

        // Opening '['.
        sink.emit(TokenType.LinkBracket, start, start + 1)

        // Find the closing ']' by scanning for balanced brackets.
        val closeBracket = findClosingBracketIdx(chars, start + 1)
        if (closeBracket != null) {
            sink.emit(TokenType.LinkBracket, closeBracket, closeBracket + 1)

            val afterClose = closeBracket + 1
            if (afterClose < result.nextIndex) {
                val c = chars.getOrNull(afterClose)
                if (c == '(') {
                    // Inline link: [text](dest "title")
                    emitInlineLinkSpans(chars, afterClose, result.nextIndex, sink)
                } else if (c == '[') {
                    // Full or collapsed reference: [text][label] or [text][]
                    emitReferenceLinkSpans(chars, afterClose, result.nextIndex, sink)
                }
                // Shortcut reference: [text] — no additional spans needed.
            }
        }

        result
    }

/**
 * Highlight wrapper for a CommonMark image.
 *
 * On success, emits:
 * - [TokenType.ImageMarker] for the `!`
 * - [TokenType.LinkBracket] for `[` and `]`
 * - For inline images: [TokenType.LinkParen], [TokenType.LinkDestination],
 *   [TokenType.LinkTitle]
 * - For reference images: [TokenType.LinkBracket] for the reference `[label]`
 */
fun pImageHighlight(
    contentParser: (chars: List<Char>, userContext: SpanSink) -> List<Inline>,
    resolveRef: LinkRefResolver = { null },
): Parser<Char, Inline, SpanSink> =
    Parser { input ->
        val start = input.index
        val result = pImage(contentParser, resolveRef)(input)
        if (result !is Success) return@Parser result

        val chars = input.input
        val sink = input.userContext

        // '!' marker.
        sink.emit(TokenType.ImageMarker, start, start + 1)

        // Opening '[' (at start + 1).
        sink.emit(TokenType.LinkBracket, start + 1, start + 2)

        // Find the closing ']'.
        val closeBracket = findClosingBracketIdx(chars, start + 2)
        if (closeBracket != null) {
            sink.emit(TokenType.LinkBracket, closeBracket, closeBracket + 1)

            val afterClose = closeBracket + 1
            if (afterClose < result.nextIndex) {
                val c = chars.getOrNull(afterClose)
                if (c == '(') {
                    emitInlineLinkSpans(chars, afterClose, result.nextIndex, sink)
                } else if (c == '[') {
                    emitReferenceLinkSpans(chars, afterClose, result.nextIndex, sink)
                }
            }
        }

        result
    }

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

/**
 * Finds the index of the closing `]` that matches an opening `[`, handling
 * nesting and backslash escapes. [start] is the index just past the `[`.
 */
private fun findClosingBracketIdx(chars: List<Char>, start: Int): Int? {
    var depth = 1
    var i = start
    while (i < chars.size) {
        when {
            chars[i] == '\\' && i + 1 < chars.size -> i += 2
            chars[i] == '[' -> { depth++; i++ }
            chars[i] == ']' -> {
                depth--
                if (depth == 0) return i
                i++
            }
            else -> i++
        }
    }
    return null
}

/**
 * Emits spans for an inline link suffix: `(destination "title")`.
 * [start] points at the `(` character, [end] is the position after `)`.
 */
private fun emitInlineLinkSpans(chars: List<Char>, start: Int, end: Int, sink: SpanSink) {
    // Opening '('.
    sink.emit(TokenType.LinkParen, start, start + 1)

    var idx = start + 1
    // Skip whitespace.
    idx = skipWs(chars, idx)

    // Try to find destination.
    if (idx < end - 1) {
        val destResult = parseLinkDestination(chars, idx)
        if (destResult != null) {
            val (_, afterDest) = destResult
            sink.emit(TokenType.LinkDestination, idx, afterDest)
            idx = afterDest

            // Skip whitespace, then try title.
            val posAfterDest = idx
            idx = skipWs(chars, idx)

            val c = chars.getOrNull(idx)
            if (c == '"' || c == '\'' || c == '(') {
                val titleResult = parseLinkTitle(chars, idx)
                if (titleResult != null) {
                    val (_, afterTitle) = titleResult
                    sink.emit(TokenType.LinkTitle, idx, afterTitle)
                }
            }
        }
    }

    // Closing ')'.
    sink.emit(TokenType.LinkParen, end - 1, end)
}

/**
 * Emits spans for a reference link suffix: `[label]` or `[]`.
 */
private fun emitReferenceLinkSpans(chars: List<Char>, start: Int, end: Int, sink: SpanSink) {
    // The entire [label] or [] is a LinkBracket pair.
    sink.emit(TokenType.LinkBracket, start, start + 1) // [
    sink.emit(TokenType.LinkBracket, end - 1, end)     // ]
}

private fun skipWs(chars: List<Char>, startIdx: Int): Int {
    var i = startIdx
    while (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) i++
    if (i < chars.size && (chars[i] == '\n' || chars[i] == '\r')) {
        if (chars[i] == '\r' && i + 1 < chars.size && chars[i + 1] == '\n') i += 2 else i++
        while (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) i++
    }
    return i
}
