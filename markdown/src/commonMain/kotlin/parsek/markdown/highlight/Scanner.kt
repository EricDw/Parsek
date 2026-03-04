package parsek.markdown.highlight

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.markdown.ast.Block
import parsek.markdown.ast.Inline
import parsek.markdown.parser.collectLinkRefDefs
import parsek.markdown.parser.extractRawContent
import parsek.markdown.parser.block.parseLinkDestination
import parsek.markdown.parser.block.parseLinkTitle
import parsek.markdown.parser.block.canInterruptParagraph
import parsek.markdown.parser.block.setextUnderlineLevel
import parsek.markdown.parser.inline.LinkRefResolver
import parsek.markdown.parser.inline.parseInlineContent
import parsek.markdown.parser.inline.splitExtendedAutolinks
import parsek.pMany

// ---------------------------------------------------------------------------
// LocatedBlock — pairs a Block with its start index in the document
// ---------------------------------------------------------------------------

internal data class LocatedBlock(val block: Block, val startIndex: Int)

// ---------------------------------------------------------------------------
// pLocatedBlockHighlight — wraps pBlockHighlight to capture start index
// ---------------------------------------------------------------------------

internal fun pLocatedBlockHighlight(): Parser<Char, LocatedBlock, SpanSink> =
    Parser { input ->
        val start = input.index
        when (val result = pBlockHighlight()(input)) {
            is Success -> Success(LocatedBlock(result.value, start), result.nextIndex, result.input)
            is Failure -> result
        }
    }

// ---------------------------------------------------------------------------
// Line reading helpers (replicated from ParagraphParser — they are private)
// ---------------------------------------------------------------------------

private fun isBlankLine(content: String): Boolean =
    content.all { it == ' ' || it == '\t' }

private fun advancePastLineEnding(chars: List<Char>, idx: Int): Int = when {
    idx >= chars.size -> idx
    chars[idx] == '\r' && idx + 1 < chars.size && chars[idx + 1] == '\n' -> idx + 2
    chars[idx] == '\r' || chars[idx] == '\n' -> idx + 1
    else -> idx
}

private fun readLineEnd(chars: List<Char>, startIdx: Int): Int {
    var i = startIdx
    while (i < chars.size && chars[i] != '\n' && chars[i] != '\r') i++
    return i
}

// ---------------------------------------------------------------------------
// Source map builders
// ---------------------------------------------------------------------------

/**
 * Builds a [SourceMap] for a paragraph starting at [startIdx].
 *
 * Replicates the line-walking logic of `pParagraph`:
 * - Each line's content is obtained by stripping all leading whitespace
 *   (matching [String.trimStart]).
 * - Lines are joined with `\n`.
 * - The source map records where each stripped line begins in the document.
 */
internal fun buildParagraphSourceMap(chars: List<Char>, startIdx: Int): SourceMap {
    val entries = mutableListOf<SourceMap.LineMapping>()
    var idx = startIdx
    var rawOffset = 0
    var isFirst = true

    while (idx < chars.size) {
        if (!isFirst && canInterruptParagraph(chars, idx)) break

        val lineEnd = readLineEnd(chars, idx)
        val lineContent = chars.subList(idx, lineEnd).joinToString("")
        if (isBlankLine(lineContent)) break

        // Find where content starts after trimStart (skip all leading whitespace)
        var contentStart = idx
        while (contentStart < lineEnd && chars[contentStart].isWhitespace()) contentStart++

        entries.add(SourceMap.LineMapping(rawOffset, contentStart))

        val strippedLen = lineEnd - contentStart
        rawOffset += strippedLen + 1 // +1 for the \n separator in joinToString("\n")

        idx = advancePastLineEnding(chars, lineEnd)
        isFirst = false
    }

    return SourceMap(entries)
}

/**
 * Builds a [SourceMap] for an ATX heading starting at [startIdx].
 *
 * Replicates the content extraction of `pAtxHeading` + `normalizeAtxContent`:
 * - Skip 0–3 leading spaces
 * - Skip 1–6 `#` characters
 * - Skip leading spaces/tabs after the `#` run
 * - Content starts at the first non-whitespace character
 */
internal fun buildAtxHeadingSourceMap(chars: List<Char>, startIdx: Int): SourceMap {
    var idx = startIdx
    // Skip 0–3 leading spaces
    var spaces = 0
    while (spaces < 3 && idx < chars.size && chars[idx] == ' ') { spaces++; idx++ }
    // Skip # characters
    while (idx < chars.size && chars[idx] == '#') idx++
    // Skip leading spaces/tabs after # run (matches normalizeAtxContent's trimStart)
    while (idx < chars.size && chars[idx] != '\n' && chars[idx] != '\r' &&
        (chars[idx] == ' ' || chars[idx] == '\t')
    ) idx++
    return SourceMap.simple(idx)
}

/**
 * Builds a [SourceMap] for a setext heading starting at [startIdx].
 *
 * Replicates the content extraction of `pSetextHeading`:
 * - Content lines have 0–3 leading spaces stripped (via `stripUpTo3Spaces`)
 * - Lines are joined with `\n`
 * - Stops at the setext underline
 */
internal fun buildSetextHeadingSourceMap(chars: List<Char>, startIdx: Int): SourceMap {
    val entries = mutableListOf<SourceMap.LineMapping>()
    var idx = startIdx
    var rawOffset = 0
    var isFirst = true

    while (idx < chars.size) {
        // After the first line, check for setext underline
        if (!isFirst) {
            val level = setextUnderlineLevel(chars, idx)
            if (level != null) break
        }

        if (!isFirst && canInterruptParagraph(chars, idx)) break

        val lineEnd = readLineEnd(chars, idx)
        val lineContent = chars.subList(idx, lineEnd).joinToString("")
        if (isBlankLine(lineContent)) break

        // stripUpTo3Spaces: skip up to 3 leading space characters
        var contentStart = idx
        var stripped = 0
        while (stripped < 3 && contentStart < lineEnd && chars[contentStart] == ' ') {
            stripped++
            contentStart++
        }

        entries.add(SourceMap.LineMapping(rawOffset, contentStart))

        val strippedLen = lineEnd - contentStart
        rawOffset += strippedLen + 1 // +1 for \n separator

        idx = advancePastLineEnding(chars, lineEnd)
        isFirst = false
    }

    return SourceMap(entries)
}

/**
 * Determines the correct source map builder for a [Block.Heading] at [startIdx].
 *
 * ATX headings start with (0–3 spaces +) `#` characters followed by space/tab/EOL.
 * Everything else is a setext heading.
 */
internal fun buildHeadingSourceMap(chars: List<Char>, startIdx: Int): SourceMap {
    var i = startIdx
    var spaces = 0
    while (spaces < 3 && i < chars.size && chars[i] == ' ') { spaces++; i++ }
    if (i < chars.size && chars[i] == '#') {
        var hashes = 0
        var j = i
        while (j < chars.size && chars[j] == '#') { hashes++; j++ }
        if (hashes in 1..6) {
            val after = chars.getOrNull(j)
            if (after == null || after == ' ' || after == '\t' || after == '\n' || after == '\r') {
                return buildAtxHeadingSourceMap(chars, startIdx)
            }
        }
    }
    return buildSetextHeadingSourceMap(chars, startIdx)
}

// ---------------------------------------------------------------------------
// ASCII punctuation helper
// ---------------------------------------------------------------------------

/** Returns `true` if [c] is an ASCII punctuation character per CommonMark §2.4. */
private fun isAsciiPunctuation(c: Char): Boolean =
    c in '!'..'/' || c in ':'..'@' || c in '['..'`' || c in '{'..'~'

// ---------------------------------------------------------------------------
// Task marker detection
// ---------------------------------------------------------------------------

/**
 * Detects a GFM task list marker at the start of [text].
 * Returns `(checked, endIndex)` or `null` if no valid marker found.
 */
private fun detectTaskMarker(text: String): Pair<Boolean, Int>? {
    if (text.length < 4) return null
    if (text[0] != '[') return null
    val isChecked = when (text[1]) {
        ' ' -> false
        'x', 'X' -> true
        else -> return null
    }
    if (text[2] != ']') return null
    if (text[3] != ' ' && text[3] != '\t') return null
    return Pair(isChecked, 4)
}

// ---------------------------------------------------------------------------
// Inline pass — AST-guided source walking
// ---------------------------------------------------------------------------

/**
 * Emits inline spans for all located blocks by parsing inline AST and
 * walking it against the raw source text.
 */
internal fun scanInlines(
    locatedBlocks: List<LocatedBlock>,
    chars: List<Char>,
    sink: SpanSink,
    resolveRef: LinkRefResolver,
) {
    for ((block, startIdx) in locatedBlocks) {
        processBlockInlines(block, chars, startIdx, sink, resolveRef, topLevel = true)
    }
}

private fun processBlockInlines(
    block: Block,
    chars: List<Char>,
    startIdx: Int,
    sink: SpanSink,
    resolveRef: LinkRefResolver,
    topLevel: Boolean,
) {
    when (block) {
        is Block.Paragraph -> {
            val raw = extractRawContent(block.inlines) ?: return
            if (raw.isEmpty()) return
            val sourceMap = if (topLevel) buildParagraphSourceMap(chars, startIdx) else SourceMap.simple(0)
            val inlines = parseAndSplitInlines(raw, resolveRef)
            emitInlineSpans(inlines, raw, sourceMap, sink)
        }
        is Block.Heading -> {
            val raw = extractRawContent(block.inlines) ?: return
            if (raw.isEmpty()) return
            val sourceMap = if (topLevel) buildHeadingSourceMap(chars, startIdx) else SourceMap.simple(0)
            val inlines = parseAndSplitInlines(raw, resolveRef)
            emitInlineSpans(inlines, raw, sourceMap, sink)
        }
        is Block.BlockQuote -> {
            for (inner in block.blocks) {
                processBlockInlines(inner, chars, startIdx, sink, resolveRef, topLevel = false)
            }
        }
        is Block.BulletList -> {
            for (item in block.items) {
                processBlockInlines(item, chars, startIdx, sink, resolveRef, topLevel = false)
            }
        }
        is Block.OrderedList -> {
            for (item in block.items) {
                processBlockInlines(item, chars, startIdx, sink, resolveRef, topLevel = false)
            }
        }
        is Block.ListItem -> {
            val blocks = block.blocks
            val firstParagraphIdx = blocks.indexOfFirst { it is Block.Paragraph }
            if (firstParagraphIdx != -1) {
                val para = blocks[firstParagraphIdx] as Block.Paragraph
                val raw = extractRawContent(para.inlines)
                if (raw != null) {
                    val taskResult = detectTaskMarker(raw)
                    if (taskResult != null) {
                        sink.emit(TokenType.TaskMarker, 0, 3)
                        val stripped = raw.substring(taskResult.second)
                        val inlines = parseAndSplitInlines(stripped, resolveRef)
                        emitInlineSpans(inlines, stripped, SourceMap.simple(0), sink)
                        for ((i, inner) in blocks.withIndex()) {
                            if (i != firstParagraphIdx) {
                                processBlockInlines(inner, chars, startIdx, sink, resolveRef, topLevel = false)
                            }
                        }
                        return
                    }
                }
            }
            for (inner in blocks) {
                processBlockInlines(inner, chars, startIdx, sink, resolveRef, topLevel = false)
            }
        }
        else -> {} // Leaf blocks already have block-level spans from the block pass
    }
}

private fun parseAndSplitInlines(raw: String, resolveRef: LinkRefResolver): List<Inline> {
    val inlines = parseInlineContent(raw.toList(), Unit, resolveRef)
    return splitExtendedAutolinks(inlines)
}

// ---------------------------------------------------------------------------
// AST-guided walker — emits spans at correct absolute positions
// ---------------------------------------------------------------------------

/**
 * Walks a list of [Inline] nodes against the [raw] source text, emitting
 * highlight spans at absolute document positions via [sourceMap].
 *
 * @param inlines the inline AST nodes to walk.
 * @param raw the raw content string that the inline parser consumed.
 * @param sourceMap maps raw-content offsets to absolute document positions.
 * @param sink the span accumulator.
 * @param startPos the starting position in the raw content.
 * @return the position in the raw content after all inlines have been consumed.
 */
internal fun emitInlineSpans(
    inlines: List<Inline>,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int = 0,
): Int {
    var pos = startPos
    for (inline in inlines) {
        pos = emitSingleInlineSpan(inline, raw, sourceMap, sink, pos)
    }
    return pos
}

private fun emitSingleInlineSpan(
    inline: Inline,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    pos: Int,
): Int = when (inline) {
    is Inline.Text -> emitTextSpans(inline, raw, sourceMap, sink, pos)
    is Inline.SoftBreak -> emitSoftBreakSpan(raw, sourceMap, sink, pos)
    is Inline.HardBreak -> emitHardBreakSpan(raw, sourceMap, sink, pos)
    is Inline.CodeSpan -> emitCodeSpanSpans(inline, raw, sourceMap, sink, pos)
    is Inline.Emphasis -> emitEmphasisSpans(inline, raw, sourceMap, sink, pos)
    is Inline.StrongEmphasis -> emitStrongEmphasisSpans(inline, raw, sourceMap, sink, pos)
    is Inline.Strikethrough -> emitStrikethroughSpans(inline, raw, sourceMap, sink, pos)
    is Inline.Link -> emitLinkSpans(inline, raw, sourceMap, sink, pos)
    is Inline.Image -> emitImageSpans(inline, raw, sourceMap, sink, pos)
    is Inline.Autolink -> emitAutolinkSpans(inline, raw, sourceMap, sink, pos)
    is Inline.RawHtml -> emitRawHtmlSpan(inline, raw, sourceMap, sink, pos)
    is Inline.HtmlEntity -> emitHtmlEntitySpan(inline, sourceMap, sink, pos)
    is Inline.ExtendedAutolink -> emitExtendedAutolinkSpan(inline, sourceMap, sink, pos)
}

// ---------------------------------------------------------------------------
// Text — dual-pointer walk detecting backslash escapes
// ---------------------------------------------------------------------------

private fun emitTextSpans(
    text: Inline.Text,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos
    var litIdx = 0
    val literal = text.literal
    var textRunStart = pos

    while (litIdx < literal.length && pos < raw.length) {
        if (raw[pos] == '\\' && pos + 1 < raw.length && isAsciiPunctuation(raw[pos + 1])) {
            // Flush preceding plain text run
            if (pos > textRunStart) {
                sink.emit(TokenType.Text, sourceMap.toAbsolute(textRunStart), sourceMap.toAbsolute(pos))
            }
            // Emit escape sequence (2 source chars → 1 literal char)
            sink.emit(TokenType.EscapeSequence, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 2))
            pos += 2
            litIdx += 1
            textRunStart = pos
        } else {
            pos += 1
            litIdx += 1
        }
    }

    // Flush remaining plain text
    if (pos > textRunStart) {
        sink.emit(TokenType.Text, sourceMap.toAbsolute(textRunStart), sourceMap.toAbsolute(pos))
    }

    return pos
}

// ---------------------------------------------------------------------------
// Soft break — optional space/tab + newline
// ---------------------------------------------------------------------------

private fun emitSoftBreakSpan(
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos
    val start = pos
    // Skip optional trailing space/tab (pLineBreak consumes at most 1)
    if (pos < raw.length && (raw[pos] == ' ' || raw[pos] == '\t')) pos++
    // Skip the line ending
    if (pos < raw.length && raw[pos] == '\n') pos++
    sink.emit(TokenType.SoftBreak, sourceMap.toAbsolute(start), sourceMap.toAbsolute(pos))
    return pos
}

// ---------------------------------------------------------------------------
// Hard break — (2+ spaces | backslash) + newline
// ---------------------------------------------------------------------------

private fun emitHardBreakSpan(
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos
    val start = pos
    // Skip spaces or backslash before the newline
    if (pos < raw.length && raw[pos] == '\\') {
        pos++
    } else {
        while (pos < raw.length && raw[pos] == ' ') pos++
    }
    // Skip the line ending
    if (pos < raw.length && raw[pos] == '\n') pos++
    sink.emit(TokenType.HardBreak, sourceMap.toAbsolute(start), sourceMap.toAbsolute(pos))
    return pos
}

// ---------------------------------------------------------------------------
// Code span — backtick delimiters + content
// ---------------------------------------------------------------------------

private fun emitCodeSpanSpans(
    codeSpan: Inline.CodeSpan,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos

    // Count opening backtick run
    var runLen = 0
    while (pos + runLen < raw.length && raw[pos + runLen] == '`') runLen++

    // Emit opening delimiter
    sink.emit(TokenType.CodeSpanDelimiter, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + runLen))
    pos += runLen

    // Find closing backtick run of the same length
    val contentStart = pos
    val contentEnd = findClosingBackticks(raw, pos, runLen)

    // Emit content
    sink.emit(TokenType.CodeSpanContent, sourceMap.toAbsolute(contentStart), sourceMap.toAbsolute(contentEnd))
    pos = contentEnd

    // Emit closing delimiter
    sink.emit(TokenType.CodeSpanDelimiter, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + runLen))
    pos += runLen

    return pos
}

/**
 * Scans forward from [startPos] looking for a closing backtick run of exactly
 * [runLen] backticks. Returns the index of the first backtick in the closing run.
 */
private fun findClosingBackticks(raw: String, startPos: Int, runLen: Int): Int {
    var i = startPos
    while (i < raw.length) {
        if (raw[i] == '`') {
            val runStart = i
            while (i < raw.length && raw[i] == '`') i++
            if (i - runStart == runLen) return runStart
        } else {
            i++
        }
    }
    return raw.length // shouldn't happen if AST parsed correctly
}

// ---------------------------------------------------------------------------
// Emphasis — * or _ delimiters (1 char each)
// ---------------------------------------------------------------------------

private fun emitEmphasisSpans(
    emphasis: Inline.Emphasis,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos

    // Opening marker (1 char)
    sink.emit(TokenType.EmphasisMarker, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1

    // Recurse children
    pos = emitInlineSpans(emphasis.children, raw, sourceMap, sink, pos)

    // Closing marker (1 char)
    sink.emit(TokenType.EmphasisMarker, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1

    return pos
}

// ---------------------------------------------------------------------------
// Strong emphasis — ** or __ delimiters (2 chars each)
// ---------------------------------------------------------------------------

private fun emitStrongEmphasisSpans(
    strong: Inline.StrongEmphasis,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos

    // Opening marker (2 chars)
    sink.emit(TokenType.StrongMarker, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 2))
    pos += 2

    // Recurse children
    pos = emitInlineSpans(strong.children, raw, sourceMap, sink, pos)

    // Closing marker (2 chars)
    sink.emit(TokenType.StrongMarker, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 2))
    pos += 2

    return pos
}

// ---------------------------------------------------------------------------
// Strikethrough — ~~ delimiters (2 chars each)
// ---------------------------------------------------------------------------

private fun emitStrikethroughSpans(
    strikethrough: Inline.Strikethrough,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos

    // Opening marker (2 chars)
    sink.emit(TokenType.StrikethroughMarker, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 2))
    pos += 2

    // Recurse children
    pos = emitInlineSpans(strikethrough.children, raw, sourceMap, sink, pos)

    // Closing marker (2 chars)
    sink.emit(TokenType.StrikethroughMarker, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 2))
    pos += 2

    return pos
}

// ---------------------------------------------------------------------------
// Link — [children](url "title") or [children][ref] or [children]
// ---------------------------------------------------------------------------

private fun emitLinkSpans(
    link: Inline.Link,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos

    // Opening [
    sink.emit(TokenType.LinkBracket, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1

    // Recurse children
    pos = emitInlineSpans(link.children, raw, sourceMap, sink, pos)

    // Closing ]
    sink.emit(TokenType.LinkBracket, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1

    // Check suffix
    pos = emitLinkSuffix(raw, sourceMap, sink, pos)

    return pos
}

// ---------------------------------------------------------------------------
// Image — ![alt](url "title") or ![alt][ref] or ![alt]
// ---------------------------------------------------------------------------

private fun emitImageSpans(
    image: Inline.Image,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos

    // ! prefix
    sink.emit(TokenType.ImageMarker, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1

    // Opening [
    sink.emit(TokenType.LinkBracket, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1

    // Recurse children (alt text inlines)
    pos = emitInlineSpans(image.children, raw, sourceMap, sink, pos)

    // Closing ]
    sink.emit(TokenType.LinkBracket, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1

    // Check suffix
    pos = emitLinkSuffix(raw, sourceMap, sink, pos)

    return pos
}

// ---------------------------------------------------------------------------
// Link/Image suffix — inline (url), reference [ref], or shortcut
// ---------------------------------------------------------------------------

private fun emitLinkSuffix(
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    if (startPos >= raw.length) return startPos
    return when (raw[startPos]) {
        '(' -> emitInlineLinkSuffix(raw, sourceMap, sink, startPos)
        '[' -> emitReferenceLinkSuffix(raw, sourceMap, sink, startPos)
        else -> startPos // shortcut reference — no suffix
    }
}

private fun emitInlineLinkSuffix(
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos

    // (
    sink.emit(TokenType.LinkParen, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1

    // Optional whitespace (including newlines)
    pos = skipLinkWhitespace(raw, pos)

    // Destination (if not immediately at closing paren)
    if (pos < raw.length && raw[pos] != ')') {
        val destResult = parseLinkDestination(raw.toList(), pos)
        if (destResult != null) {
            val (_, afterDest) = destResult
            sink.emit(TokenType.LinkDestination, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(afterDest))
            pos = afterDest
        }

        // Optional whitespace
        val posBeforeWs = pos
        pos = skipLinkWhitespace(raw, pos)

        // Optional title
        if (pos < raw.length && (raw[pos] == '"' || raw[pos] == '\'' || raw[pos] == '(')) {
            val titleResult = parseLinkTitle(raw.toList(), pos)
            if (titleResult != null) {
                val (_, afterTitle) = titleResult
                sink.emit(TokenType.LinkTitle, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(afterTitle))
                pos = afterTitle
            }
        }

        // Optional whitespace before closing paren
        pos = skipLinkWhitespace(raw, pos)
    }

    // )
    if (pos < raw.length && raw[pos] == ')') {
        sink.emit(TokenType.LinkParen, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
        pos += 1
    }

    return pos
}

private fun emitReferenceLinkSuffix(
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos

    // [
    sink.emit(TokenType.LinkBracket, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1

    // Scan to closing ]
    while (pos < raw.length && raw[pos] != ']') pos++

    // ]
    if (pos < raw.length) {
        sink.emit(TokenType.LinkBracket, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
        pos += 1
    }

    return pos
}

private fun skipLinkWhitespace(raw: String, pos: Int): Int {
    var p = pos
    while (p < raw.length && (raw[p] == ' ' || raw[p] == '\t' || raw[p] == '\n' || raw[p] == '\r')) p++
    return p
}

// ---------------------------------------------------------------------------
// Autolink — <url>
// ---------------------------------------------------------------------------

private fun emitAutolinkSpans(
    autolink: Inline.Autolink,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos
    // Skip <
    pos += 1
    // URL content
    val urlEnd = pos + autolink.url.length
    sink.emit(TokenType.AutolinkUrl, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(urlEnd))
    pos = urlEnd
    // Skip >
    pos += 1
    return pos
}

// ---------------------------------------------------------------------------
// Raw HTML — inline HTML tag
// ---------------------------------------------------------------------------

private fun emitRawHtmlSpan(
    rawHtml: Inline.RawHtml,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    val len = rawHtml.literal.length
    sink.emit(TokenType.HtmlInline, sourceMap.toAbsolute(startPos), sourceMap.toAbsolute(startPos + len))
    return startPos + len
}

// ---------------------------------------------------------------------------
// HTML entity — &amp; &#42; &#x2A; etc.
// ---------------------------------------------------------------------------

private fun emitHtmlEntitySpan(
    entity: Inline.HtmlEntity,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    // HtmlEntity.literal stores the full original text (e.g. "&amp;")
    val len = entity.literal.length
    sink.emit(TokenType.EntityRef, sourceMap.toAbsolute(startPos), sourceMap.toAbsolute(startPos + len))
    return startPos + len
}

// ---------------------------------------------------------------------------
// Extended autolink — bare URL (GFM)
// ---------------------------------------------------------------------------

private fun emitExtendedAutolinkSpan(
    autolink: Inline.ExtendedAutolink,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    val sourceLen = computeExtendedAutolinkSourceLength(autolink.url)
    sink.emit(
        TokenType.ExtendedAutolinkUrl,
        sourceMap.toAbsolute(startPos),
        sourceMap.toAbsolute(startPos + sourceLen),
    )
    return startPos + sourceLen
}

private fun computeExtendedAutolinkSourceLength(url: String): Int = when {
    url.startsWith("http://www.") -> url.length - 7  // "http://" was prepended to "www.…"
    url.startsWith("mailto:") -> url.length - 7       // "mailto:" was prepended
    else -> url.length                                  // URL autolinks have the scheme in source
}

// ---------------------------------------------------------------------------
// scanDocument — top-level entry point
// ---------------------------------------------------------------------------

/**
 * Scans a markdown document and returns a flat list of [Span]s with
 * absolute document offsets for top-level paragraphs and headings.
 *
 * This is the efficient alternative to [pDocumentHighlight] for consumers
 * that only need token positions (e.g. syntax highlighting in editors).
 * Unlike [pDocumentHighlight], the returned [Span]s for top-level inline
 * content use absolute document offsets instead of 0-based paragraph-relative
 * offsets.
 *
 * The inline pass uses AST-guided source walking: inline content is parsed
 * into an AST, then the AST is walked against the raw source text to emit
 * spans at correct absolute positions. This avoids the position corruption
 * that occurs with recursive highlight parsers and bulk span shifting.
 *
 * **Phase 1 limitation**: inline spans inside container blocks (blockquotes,
 * list items) remain at container-relative offsets.
 */
fun scanDocument(text: String): List<Span> {
    val chars = text.toList()
    val sink = SpanSink()
    val input = ParserInput.of(chars, sink)

    // Block pass: parse blocks, emit block-level spans, capture start indices
    val blockResult = pMany(pLocatedBlockHighlight())(input) as Success
    val locatedBlocks = blockResult.value

    // Collect link reference definitions for inline resolution
    val refMap = mutableMapOf<String, Pair<String, String?>>()
    for ((block, _) in locatedBlocks) {
        collectLinkRefDefs(block, refMap)
    }
    val resolveRef: LinkRefResolver = { refMap[it] }

    // Inline pass: AST-guided source walking with absolute offsets
    scanInlines(locatedBlocks, chars, sink, resolveRef)

    return sink.spans
}
