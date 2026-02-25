package parsek.markdown.highlight.block

import parsek.Parser
import parsek.Success
import parsek.markdown.ast.Block
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.emit
import parsek.markdown.parser.block.pAtxHeading

/**
 * Highlight wrapper for a CommonMark ATX heading.
 *
 * On success, emits:
 * - [TokenType.HeadingMarker] for the opening `#` run
 * - [TokenType.HeadingText] for the heading content (if non-empty)
 * - [TokenType.HeadingMarker] for the optional closing `#` run
 */
fun pAtxHeadingHighlight(): Parser<Char, Block.Heading, SpanSink> =
    Parser { input ->
        val start = input.index
        val result = pAtxHeading<SpanSink>()(input)
        if (result !is Success) return@Parser result

        val chars = input.input
        val sink = input.userContext
        var idx = start

        // Skip 0–3 leading spaces.
        var spaces = 0
        while (spaces < 3 && idx < chars.size && chars[idx] == ' ') { spaces++; idx++ }

        // Opening '#' run.
        val hashStart = idx
        while (idx < chars.size && chars[idx] == '#') idx++
        sink.emit(TokenType.HeadingMarker, hashStart, idx)

        // Skip the space/tab after the '#' run (if present).
        val afterHash = idx
        while (idx < chars.size && (chars[idx] == ' ' || chars[idx] == '\t')) idx++

        // Find end of line (before line ending).
        var lineEnd = idx
        while (lineEnd < result.nextIndex && chars[lineEnd] != '\n' && chars[lineEnd] != '\r') lineEnd++

        // Scan backwards from lineEnd to find trailing whitespace, then closing '#' run.
        var contentEnd = lineEnd
        // Strip trailing spaces/tabs.
        while (contentEnd > idx && (chars[contentEnd - 1] == ' ' || chars[contentEnd - 1] == '\t')) contentEnd--

        // Check for closing '#' run.
        var closingHashEnd = contentEnd
        while (closingHashEnd > idx && chars[closingHashEnd - 1] == '#') closingHashEnd--
        val hasClosingHash = closingHashEnd < contentEnd &&
            closingHashEnd > idx &&
            (chars[closingHashEnd - 1] == ' ' || chars[closingHashEnd - 1] == '\t')

        if (hasClosingHash) {
            // Strip trailing spaces/tabs before the closing hash run.
            var textEnd = closingHashEnd
            while (textEnd > idx && (chars[textEnd - 1] == ' ' || chars[textEnd - 1] == '\t')) textEnd--
            sink.emit(TokenType.HeadingText, idx, textEnd)
            sink.emit(TokenType.HeadingMarker, closingHashEnd, contentEnd)
        } else if (closingHashEnd == idx && contentEnd > idx) {
            // Entire content is '#' chars. Check if there was whitespace between
            // opening hashes and these hashes (meaning they are a closing sequence).
            if (afterHash < idx) {
                // There was space — this is a closing hash run, no text.
                sink.emit(TokenType.HeadingMarker, idx, contentEnd)
            } else {
                // No space between opening hashes and content — treat as text.
                sink.emit(TokenType.HeadingText, idx, contentEnd)
            }
        } else {
            sink.emit(TokenType.HeadingText, idx, contentEnd)
        }

        result
    }
