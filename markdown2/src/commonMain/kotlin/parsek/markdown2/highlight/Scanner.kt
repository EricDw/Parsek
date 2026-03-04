package parsek.markdown2.highlight

import parsek.markdown.ast.Block
import parsek.markdown.ast.Inline
import parsek.markdown.highlight.Span
import parsek.markdown.highlight.SourceMap
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.emit
import parsek.markdown2.lexeme.Lexeme
import parsek.markdown2.lexeme.SourceRange
import parsek.markdown2.lexer.*
import parsek.markdown2.parser.*

// ---------------------------------------------------------------------------
// scanDocument — top-level entry point
// ---------------------------------------------------------------------------

/**
 * Scans a markdown document and returns a flat list of [Span]s with
 * absolute document offsets for syntax highlighting.
 *
 * Uses `:markdown2`'s pipeline:
 * 1. Parse document to get link ref defs
 * 2. Scan text into lexemes (with [SourceRange])
 * 3. Walk lines emitting block-level AND inline-level spans
 */
fun scanDocument(text: String): List<Span> {
    val sink = SpanSink()

    // Stage 1: Parse document for link ref defs
    val document = parseDocument(text)
    val refDefs = mutableMapOf<String, Pair<String, String?>>()
    collectRefDefs(document.blocks, refDefs)
    val resolver: LinkRefResolver? = if (refDefs.isNotEmpty()) {
        { label -> refDefs[label] }
    } else null

    // Stage 2: Scan into lexemes and split into lines
    val lexemes = parsek.markdown2.scanner.scanDocument(text)
    val rawLines = splitLines(lexemes)
    val lines = rawLines.map { Line.from(it) }

    // Stage 3: Walk lines emitting both block and inline spans
    scanBlockSpans(lines, sink, resolver)

    return sink.spans
}

// ---------------------------------------------------------------------------
// Block-level span emission — walks lines detecting blocks
// ---------------------------------------------------------------------------

private fun scanBlockSpans(
    lines: List<Line>,
    sink: SpanSink,
    resolver: LinkRefResolver?,
) {
    var i = 0
    while (i < lines.size) {
        i = scanBlockLine(lines, i, sink, resolver)
    }
}

private fun scanBlockLine(
    lines: List<Line>,
    i: Int,
    sink: SpanSink,
    resolver: LinkRefResolver?,
): Int {
    val line = lines[i]

    // 1. Blank line
    if (tryBlankLine(line.lexemes) != null) return i + 1

    // 2. Block quote
    val bqStrip = tryStripBlockQuoteMarker(line)
    if (bqStrip != null) return scanBlockQuote(lines, i, sink, resolver)

    // 3. Thematic break (before list markers since `---` is both)
    val tb = tryThematicBreak(line.lexemes)
    if (tb != null) {
        sink.emit(TokenType.ThematicBreak, tb.range.start, tb.range.end)
        return i + 1
    }

    // 4. ATX heading
    val atx = tryAtxHeading(line.lexemes)
    if (atx != null) {
        val marker = atx.first
        val content = atx.second
        sink.emit(TokenType.HeadingMarker, marker.range.start, marker.range.end)
        if (content != null) {
            sink.emit(TokenType.HeadingText, content.range.start, content.range.end)
            // Parse and emit inline spans for heading content
            val rawText = lexemesToText(content.lexemes)
            val sourceMap = SourceMap.simple(content.range.start)
            val inlines = parseInlines(rawText, resolver)
            emitInlineSpans(inlines, rawText, sourceMap, sink)
        }
        return i + 1
    }

    // 5. Fenced code block
    val fence = tryCodeFenceOpen(line.lexemes)
    if (fence != null) return scanFencedCode(lines, i, fence, sink)

    // 6. HTML block
    val htmlType = detectHtmlBlockType(line.lexemes)
    if (htmlType > 0) return scanHtmlBlock(lines, i, htmlType, sink)

    // 7. Bullet list
    val bullet = tryStripBulletMarker(line)
    if (bullet != null) return scanBulletList(lines, i, sink, resolver)

    // 8. Ordered list
    val ordered = tryStripOrderedMarker(line)
    if (ordered != null) return scanOrderedList(lines, i, sink, resolver)

    // 9. Indented code block
    if (tryIndentedCodeLine(line.lexemes) != null) return scanIndentedCode(lines, i, sink)

    // 10. Paragraph (possibly with setext underline)
    return scanParagraph(lines, i, sink, resolver)
}

// ---------------------------------------------------------------------------
// Block quote
// ---------------------------------------------------------------------------

private fun scanBlockQuote(
    lines: List<Line>,
    startIdx: Int,
    sink: SpanSink,
    resolver: LinkRefResolver?,
): Int {
    val innerLines = mutableListOf<Line>()
    var i = startIdx
    var lastInnerWasBlank = false
    var lastBlockIsParagraph = false

    while (i < lines.size) {
        val line = lines[i]
        val strip = tryStripBlockQuoteMarker(line)
        if (strip != null) {
            emitBlockQuoteMarker(line, sink)
            lastInnerWasBlank = tryBlankLine(strip.innerLine.lexemes) != null
            innerLines.add(strip.innerLine)
            if (!lastInnerWasBlank) {
                lastBlockIsParagraph = tryCodeFenceOpen(strip.innerLine.lexemes) == null &&
                    tryIndentedCodeLine(strip.innerLine.lexemes) == null &&
                    tryThematicBreak(strip.innerLine.lexemes) == null &&
                    tryAtxHeading(strip.innerLine.lexemes) == null &&
                    detectHtmlBlockType(strip.innerLine.lexemes) <= 0
            }
            i++
        } else if (tryBlankLine(line.lexemes) != null) {
            break
        } else if (!lastInnerWasBlank && lastBlockIsParagraph && !canInterruptParagraphLine(line) && innerLines.isNotEmpty()) {
            innerLines.add(line)
            i++
        } else {
            break
        }
    }

    var j = 0
    while (j < innerLines.size) {
        j = scanBlockLine(innerLines, j, sink, resolver)
    }

    return i
}

private fun emitBlockQuoteMarker(line: Line, sink: SpanSink) {
    for (lex in line.lexemes) {
        if (lex is Lexeme.AngleClose) {
            sink.emit(TokenType.BlockQuoteMarker, lex.range.start, lex.range.end)
            return
        }
    }
}

// ---------------------------------------------------------------------------
// Fenced code block
// ---------------------------------------------------------------------------

private fun scanFencedCode(
    lines: List<Line>,
    startIdx: Int,
    fence: Pair<parsek.markdown2.token.Token.CodeFenceOpen, parsek.markdown2.token.Token.CodeFenceInfo?>,
    sink: SpanSink,
): Int {
    val open = fence.first
    val info = fence.second

    sink.emit(TokenType.CodeFence, open.range.start, open.range.end)
    if (info != null) {
        sink.emit(TokenType.CodeInfo, info.range.start, info.range.end)
    }

    var i = startIdx + 1
    while (i < lines.size) {
        val close = tryCodeFenceClose(lines[i].lexemes, open.fenceChar, open.fenceLength)
        if (close != null) {
            sink.emit(TokenType.CodeFence, close.range.start, close.range.end)
            return i + 1
        }
        val lr = lineRange(lines[i].lexemes)
        sink.emit(TokenType.CodeContent, lr.start, lr.end)
        i++
    }
    return i
}

// ---------------------------------------------------------------------------
// HTML block
// ---------------------------------------------------------------------------

private fun scanHtmlBlock(
    lines: List<Line>,
    startIdx: Int,
    htmlType: Int,
    sink: SpanSink,
): Int {
    val firstRange = lineRange(lines[startIdx].lexemes)
    sink.emit(TokenType.HtmlBlock, firstRange.start, firstRange.end)

    if (htmlType in 1..5 && htmlBlockEndCondition(lines[startIdx].text, htmlType)) {
        return startIdx + 1
    }

    var i = startIdx + 1
    while (i < lines.size) {
        val line = lines[i]
        when (htmlType) {
            1, 2, 3, 4, 5 -> {
                val range = lineRange(line.lexemes)
                sink.emit(TokenType.HtmlBlock, range.start, range.end)
                i++
                if (htmlBlockEndCondition(line.text, htmlType)) break
            }
            6, 7 -> {
                if (tryBlankLine(line.lexemes) != null) break
                val range = lineRange(line.lexemes)
                sink.emit(TokenType.HtmlBlock, range.start, range.end)
                i++
            }
            else -> break
        }
    }
    return i
}

// ---------------------------------------------------------------------------
// Indented code block
// ---------------------------------------------------------------------------

private fun scanIndentedCode(
    lines: List<Line>,
    startIdx: Int,
    sink: SpanSink,
): Int {
    var i = startIdx
    var lastContentLine = i

    while (i < lines.size) {
        val line = lines[i]
        if (tryIndentedCodeLine(line.lexemes) != null) {
            val range = lineRange(line.lexemes)
            sink.emit(TokenType.CodeContent, range.start, range.end)
            lastContentLine = i
            i++
        } else if (tryBlankLine(line.lexemes) != null) {
            val range = lineRange(line.lexemes)
            sink.emit(TokenType.CodeContent, range.start, range.end)
            i++
        } else {
            break
        }
    }

    return lastContentLine + 1
}

// ---------------------------------------------------------------------------
// Bullet list
// ---------------------------------------------------------------------------

private fun scanBulletList(
    lines: List<Line>,
    startIdx: Int,
    sink: SpanSink,
    resolver: LinkRefResolver?,
): Int {
    val firstStrip = tryStripBulletMarker(lines[startIdx])!!
    val marker = firstStrip.marker
    var i = startIdx

    while (i < lines.size) {
        val blankStart = i
        while (i < lines.size && tryBlankLine(lines[i].lexemes) != null) i++
        if (i >= lines.size) break

        if (tryThematicBreak(lines[i].lexemes) != null && i > startIdx) break

        val strip = tryStripBulletMarker(lines[i])
        if (strip == null || strip.marker != marker) {
            if (i > blankStart && i > startIdx) i = blankStart
            break
        }

        emitListMarkerSpan(lines[i], sink)
        i = scanListItemContent(lines, i, strip.contentIndent, strip.innerLine, sink, resolver)
    }

    return i
}

// ---------------------------------------------------------------------------
// Ordered list
// ---------------------------------------------------------------------------

private fun scanOrderedList(
    lines: List<Line>,
    startIdx: Int,
    sink: SpanSink,
    resolver: LinkRefResolver?,
): Int {
    val firstStrip = tryStripOrderedMarker(lines[startIdx])!!
    val delimiter = firstStrip.delimiter
    var i = startIdx

    while (i < lines.size) {
        val blankStart = i
        while (i < lines.size && tryBlankLine(lines[i].lexemes) != null) i++
        if (i >= lines.size) break

        val strip = tryStripOrderedMarker(lines[i])
        if (strip == null || strip.delimiter != delimiter) {
            if (i > blankStart && i > startIdx) i = blankStart
            break
        }

        emitListMarkerSpan(lines[i], sink)
        i = scanListItemContent(lines, i, strip.contentIndent, strip.innerLine, sink, resolver)
    }

    return i
}

private fun emitListMarkerSpan(line: Line, sink: SpanSink) {
    val lexemes = line.lexemes
    var idx = 0
    while (idx < lexemes.size) {
        when (lexemes[idx]) {
            is Lexeme.Space, is Lexeme.SpaceRun, is Lexeme.Tab -> idx++
            else -> break
        }
    }
    if (idx >= lexemes.size) return

    val markerStart = lexemes[idx].range.start
    when (lexemes[idx]) {
        is Lexeme.Hyphen, is Lexeme.Plus, is Lexeme.Asterisk -> {
            sink.emit(TokenType.ListMarker, markerStart, lexemes[idx].range.end)
            return
        }
        else -> {}
    }

    if (lexemes[idx] is Lexeme.DigitRun) {
        var end = lexemes[idx].range.end
        if (idx + 1 < lexemes.size && (lexemes[idx + 1] is Lexeme.Period || lexemes[idx + 1] is Lexeme.ParenClose)) {
            end = lexemes[idx + 1].range.end
        }
        sink.emit(TokenType.ListMarker, markerStart, end)
    }
}

private fun scanListItemContent(
    lines: List<Line>,
    startIdx: Int,
    contentIndent: Int,
    firstInnerLine: Line,
    sink: SpanSink,
    resolver: LinkRefResolver?,
): Int {
    val innerLines = mutableListOf(firstInnerLine)
    var i = startIdx + 1
    var hadBlank = false

    while (i < lines.size) {
        val line = lines[i]
        if (tryBlankLine(line.lexemes) != null) {
            if (tryBlankLine(firstInnerLine.lexemes) != null) break
            hadBlank = true
            innerLines.add(Line(emptyList(), "\n"))
            i++
            continue
        }

        val ls = leadingSpacesCount(line)
        if (ls >= contentIndent) {
            val stripped = stripIndent(line.lexemes, contentIndent)
            innerLines.add(Line.from(stripped))
            i++
        } else if (tryStripBulletMarker(line) != null || tryStripOrderedMarker(line) != null) {
            break
        } else if (!hadBlank && !canInterruptParagraphLine(line)) {
            innerLines.add(line)
            i++
        } else {
            break
        }
    }

    var j = 0
    while (j < innerLines.size) {
        j = scanBlockLine(innerLines, j, sink, resolver)
    }

    return i
}

// ---------------------------------------------------------------------------
// Paragraph / Setext heading
// ---------------------------------------------------------------------------

private fun scanParagraph(
    lines: List<Line>,
    startIdx: Int,
    sink: SpanSink,
    resolver: LinkRefResolver?,
): Int {
    val paraLines = mutableListOf<Line>()
    var i = startIdx

    paraLines.add(lines[i])
    i++

    while (i < lines.size) {
        val line = lines[i]
        if (tryBlankLine(line.lexemes) != null) break

        // Setext underline → heading
        val su = trySetextUnderline(line.lexemes)
        if (su != null) {
            emitParagraphAsHeadingText(paraLines, sink)
            sink.emit(TokenType.HeadingMarker, su.range.start, su.range.end)
            emitParagraphInlines(paraLines, sink, resolver)
            return i + 1
        }

        // Thematic break with hyphens that is also a setext underline → heading
        val tb = tryThematicBreak(line.lexemes)
        if (tb != null && tb.marker == '-' && trySetextUnderline(line.lexemes) != null) {
            emitParagraphAsHeadingText(paraLines, sink)
            sink.emit(TokenType.HeadingMarker, tb.range.start, tb.range.end)
            emitParagraphInlines(paraLines, sink, resolver)
            return i + 1
        }
        if (tb != null) break

        if (canInterruptParagraphLine(line)) break

        paraLines.add(line)
        i++
    }

    // Regular paragraph — emit inline spans
    emitParagraphInlines(paraLines, sink, resolver)

    return i
}

private fun emitParagraphAsHeadingText(paraLines: List<Line>, sink: SpanSink) {
    for (line in paraLines) {
        val range = contentRange(line)
        if (range != null) {
            sink.emit(TokenType.HeadingText, range.start, range.end)
        }
    }
}

/**
 * Builds a source map and raw text from paragraph lines, strips link ref defs,
 * parses inlines, and emits inline spans.
 */
private fun emitParagraphInlines(
    paraLines: List<Line>,
    sink: SpanSink,
    resolver: LinkRefResolver?,
) {
    val entries = mutableListOf<SourceMap.LineMapping>()
    val textParts = mutableListOf<String>()
    var rawOffset = 0

    for (line in paraLines) {
        val lexemes = line.lexemes
        if (lexemes.isEmpty()) continue

        // Skip leading whitespace
        var idx = 0
        while (idx < lexemes.size) {
            when (lexemes[idx]) {
                is Lexeme.Space, is Lexeme.SpaceRun, is Lexeme.Tab -> idx++
                else -> break
            }
        }
        if (idx >= lexemes.size) continue

        val contentStart = lexemes[idx].range.start
        entries.add(SourceMap.LineMapping(rawOffset, contentStart))

        var endIdx = lexemes.size
        if (endIdx > 0 && lexemes[endIdx - 1] is Lexeme.Newline) endIdx--
        val contentLexemes = if (idx < endIdx) lexemes.subList(idx, endIdx) else emptyList()
        val lineText = lexemesToText(contentLexemes)
        textParts.add(lineText)

        rawOffset += lineText.length + 1
    }

    if (textParts.isEmpty()) return
    val fullRawText = textParts.joinToString("\n")
    val fullSourceMap = SourceMap(entries)

    // Strip leading link ref defs (they consume text from the start)
    val (_, remaining) = parseLinkRefDefs(fullRawText)
    val trimmed = remaining.trim()
    if (trimmed.isEmpty()) return

    // Calculate the offset where the remaining text starts in the full raw text
    val consumedLen = fullRawText.length - remaining.length
    val leadingWs = remaining.length - remaining.trimStart().length
    val offset = consumedLen + leadingWs

    // Build a shifted source map for the trimmed text
    val shiftedEntries = mutableListOf<SourceMap.LineMapping>()
    for (entry in entries) {
        val shiftedRaw = entry.rawStart - offset
        if (shiftedRaw + (if (shiftedEntries.isEmpty()) trimmed.length else 0) >= 0) {
            // Only include entries that map into the trimmed text range
            if (entry.rawStart >= offset) {
                shiftedEntries.add(SourceMap.LineMapping(entry.rawStart - offset, entry.docStart))
            }
        }
    }
    if (shiftedEntries.isEmpty() && entries.isNotEmpty()) {
        // Fallback: use toAbsolute on the full map with the offset
        shiftedEntries.add(SourceMap.LineMapping(0, fullSourceMap.toAbsolute(offset)))
    }
    val sourceMap = SourceMap(shiftedEntries)

    val inlines = parseInlines(trimmed, resolver)
    emitInlineSpans(inlines, trimmed, sourceMap, sink)
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun contentRange(line: Line): SourceRange? {
    val lexemes = line.lexemes
    if (lexemes.isEmpty()) return null

    var start = 0
    while (start < lexemes.size) {
        when (lexemes[start]) {
            is Lexeme.Space, is Lexeme.SpaceRun, is Lexeme.Tab -> start++
            else -> break
        }
    }
    if (start >= lexemes.size) return null

    var end = lexemes.size
    if (end > 0 && lexemes[end - 1] is Lexeme.Newline) end--
    if (start >= end) return null

    return SourceRange(lexemes[start].range.start, lexemes[end - 1].range.end)
}

private fun lineRange(lexemes: List<Lexeme>): SourceRange {
    if (lexemes.isEmpty()) return SourceRange(0, 0)
    return SourceRange(lexemes.first().range.start, lexemes.last().range.end)
}

private fun leadingSpacesCount(line: Line): Int {
    var spaces = 0
    for (lex in line.lexemes) {
        when (lex) {
            is Lexeme.Space -> spaces++
            is Lexeme.SpaceRun -> spaces += lex.count
            is Lexeme.Tab -> spaces += 4 - (spaces % 4)
            else -> break
        }
    }
    return spaces
}

private fun canInterruptParagraphLine(line: Line): Boolean {
    if (tryBlankLine(line.lexemes) != null) return true
    if (tryThematicBreak(line.lexemes) != null) return true
    if (tryAtxHeading(line.lexemes) != null) return true
    if (tryCodeFenceOpen(line.lexemes) != null) return true
    if (tryStripBlockQuoteMarker(line) != null) return true
    val htmlType = detectHtmlBlockType(line.lexemes)
    if (htmlType in 1..6) return true
    val bullet = tryStripBulletMarker(line)
    if (bullet != null && tryBlankLine(bullet.innerLine.lexemes) == null) return true
    val ordered = tryStripOrderedMarker(line)
    if (ordered != null && ordered.number == 1 && tryBlankLine(ordered.innerLine.lexemes) == null) return true
    return false
}

// ---------------------------------------------------------------------------
// Collect link ref defs from parsed document
// ---------------------------------------------------------------------------

private fun collectRefDefs(blocks: List<Block>, refDefs: MutableMap<String, Pair<String, String?>>) {
    for (block in blocks) {
        when (block) {
            is Block.LinkReferenceDefinition -> {
                if (block.label !in refDefs) {
                    refDefs[block.label] = block.destination to block.title
                }
            }
            is Block.BlockQuote -> collectRefDefs(block.blocks, refDefs)
            is Block.BulletList -> block.items.forEach { collectRefDefs(it.blocks, refDefs) }
            is Block.OrderedList -> block.items.forEach { collectRefDefs(it.blocks, refDefs) }
            is Block.ListItem -> collectRefDefs(block.blocks, refDefs)
            else -> {}
        }
    }
}

// ---------------------------------------------------------------------------
// ASCII punctuation helper
// ---------------------------------------------------------------------------

private fun isAsciiPunctuation(c: Char): Boolean =
    c in '!'..'/' || c in ':'..'@' || c in '['..'`' || c in '{'..'~'

// ---------------------------------------------------------------------------
// Inline pass — AST-guided source walking
// ---------------------------------------------------------------------------

private fun emitInlineSpans(
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
            if (pos > textRunStart) {
                sink.emit(TokenType.Text, sourceMap.toAbsolute(textRunStart), sourceMap.toAbsolute(pos))
            }
            sink.emit(TokenType.EscapeSequence, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 2))
            pos += 2
            litIdx += 1
            textRunStart = pos
        } else {
            pos += 1
            litIdx += 1
        }
    }

    if (pos > textRunStart) {
        sink.emit(TokenType.Text, sourceMap.toAbsolute(textRunStart), sourceMap.toAbsolute(pos))
    }

    return pos
}

// ---------------------------------------------------------------------------
// Soft break
// ---------------------------------------------------------------------------

private fun emitSoftBreakSpan(
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos
    val start = pos
    if (pos < raw.length && (raw[pos] == ' ' || raw[pos] == '\t')) pos++
    if (pos < raw.length && raw[pos] == '\n') pos++
    sink.emit(TokenType.SoftBreak, sourceMap.toAbsolute(start), sourceMap.toAbsolute(pos))
    return pos
}

// ---------------------------------------------------------------------------
// Hard break
// ---------------------------------------------------------------------------

private fun emitHardBreakSpan(
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos
    val start = pos
    if (pos < raw.length && raw[pos] == '\\') {
        pos++
    } else {
        while (pos < raw.length && raw[pos] == ' ') pos++
    }
    if (pos < raw.length && raw[pos] == '\n') pos++
    sink.emit(TokenType.HardBreak, sourceMap.toAbsolute(start), sourceMap.toAbsolute(pos))
    return pos
}

// ---------------------------------------------------------------------------
// Code span
// ---------------------------------------------------------------------------

private fun emitCodeSpanSpans(
    codeSpan: Inline.CodeSpan,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos

    var runLen = 0
    while (pos + runLen < raw.length && raw[pos + runLen] == '`') runLen++

    sink.emit(TokenType.CodeSpanDelimiter, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + runLen))
    pos += runLen

    val contentStart = pos
    val contentEnd = findClosingBackticks(raw, pos, runLen)

    sink.emit(TokenType.CodeSpanContent, sourceMap.toAbsolute(contentStart), sourceMap.toAbsolute(contentEnd))
    pos = contentEnd

    sink.emit(TokenType.CodeSpanDelimiter, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + runLen))
    pos += runLen

    return pos
}

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
    return raw.length
}

// ---------------------------------------------------------------------------
// Emphasis
// ---------------------------------------------------------------------------

private fun emitEmphasisSpans(
    emphasis: Inline.Emphasis,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos
    sink.emit(TokenType.EmphasisMarker, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1
    pos = emitInlineSpans(emphasis.children, raw, sourceMap, sink, pos)
    sink.emit(TokenType.EmphasisMarker, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1
    return pos
}

// ---------------------------------------------------------------------------
// Strong emphasis
// ---------------------------------------------------------------------------

private fun emitStrongEmphasisSpans(
    strong: Inline.StrongEmphasis,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos
    sink.emit(TokenType.StrongMarker, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 2))
    pos += 2
    pos = emitInlineSpans(strong.children, raw, sourceMap, sink, pos)
    sink.emit(TokenType.StrongMarker, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 2))
    pos += 2
    return pos
}

// ---------------------------------------------------------------------------
// Strikethrough
// ---------------------------------------------------------------------------

private fun emitStrikethroughSpans(
    strikethrough: Inline.Strikethrough,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos
    sink.emit(TokenType.StrikethroughMarker, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 2))
    pos += 2
    pos = emitInlineSpans(strikethrough.children, raw, sourceMap, sink, pos)
    sink.emit(TokenType.StrikethroughMarker, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 2))
    pos += 2
    return pos
}

// ---------------------------------------------------------------------------
// Link
// ---------------------------------------------------------------------------

private fun emitLinkSpans(
    link: Inline.Link,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos
    sink.emit(TokenType.LinkBracket, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1
    pos = emitInlineSpans(link.children, raw, sourceMap, sink, pos)
    sink.emit(TokenType.LinkBracket, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1
    pos = emitLinkSuffix(raw, sourceMap, sink, pos)
    return pos
}

// ---------------------------------------------------------------------------
// Image
// ---------------------------------------------------------------------------

private fun emitImageSpans(
    image: Inline.Image,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos
    sink.emit(TokenType.ImageMarker, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1
    sink.emit(TokenType.LinkBracket, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1
    pos = emitInlineSpans(image.children, raw, sourceMap, sink, pos)
    sink.emit(TokenType.LinkBracket, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1
    pos = emitLinkSuffix(raw, sourceMap, sink, pos)
    return pos
}

// ---------------------------------------------------------------------------
// Link/Image suffix
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
        else -> startPos
    }
}

private fun emitInlineLinkSuffix(
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos
    sink.emit(TokenType.LinkParen, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1
    pos = skipLinkWhitespace(raw, pos)

    if (pos < raw.length && raw[pos] != ')') {
        val destEnd = scanLinkDestination(raw, pos)
        if (destEnd != null && destEnd > pos) {
            sink.emit(TokenType.LinkDestination, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(destEnd))
            pos = destEnd
        }

        pos = skipLinkWhitespace(raw, pos)

        if (pos < raw.length && (raw[pos] == '"' || raw[pos] == '\'' || raw[pos] == '(')) {
            val titleEnd = scanLinkTitle(raw, pos)
            if (titleEnd != null && titleEnd > pos) {
                sink.emit(TokenType.LinkTitle, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(titleEnd))
                pos = titleEnd
            }
        }

        pos = skipLinkWhitespace(raw, pos)
    }

    if (pos < raw.length && raw[pos] == ')') {
        sink.emit(TokenType.LinkParen, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
        pos += 1
    }

    return pos
}

private fun scanLinkDestination(raw: String, pos: Int): Int? {
    if (pos >= raw.length) return null
    if (raw[pos] == '<') {
        var i = pos + 1
        while (i < raw.length) {
            when (raw[i]) {
                '>' -> return i + 1
                '<' -> return null
                '\\' -> { i += 2; continue }
                '\n' -> return null
                else -> i++
            }
        }
        return null
    }
    var i = pos
    var depth = 0
    while (i < raw.length) {
        val c = raw[i]
        when {
            c == '(' -> { depth++; i++ }
            c == ')' -> { if (depth == 0) return i; depth--; i++ }
            c == '\\' && i + 1 < raw.length -> i += 2
            c <= ' ' -> return i
            else -> i++
        }
    }
    return i
}

private fun scanLinkTitle(raw: String, pos: Int): Int? {
    if (pos >= raw.length) return null
    val open = raw[pos]
    val close = when (open) {
        '"' -> '"'
        '\'' -> '\''
        '(' -> ')'
        else -> return null
    }
    var i = pos + 1
    while (i < raw.length) {
        when (raw[i]) {
            close -> return i + 1
            '\\' -> i += 2
            else -> i++
        }
    }
    return null
}

private fun emitReferenceLinkSuffix(
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos
    sink.emit(TokenType.LinkBracket, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(pos + 1))
    pos += 1
    while (pos < raw.length && raw[pos] != ']') pos++
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
// Autolink
// ---------------------------------------------------------------------------

private fun emitAutolinkSpans(
    autolink: Inline.Autolink,
    raw: String,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    var pos = startPos
    pos += 1
    val urlEnd = pos + autolink.url.length
    sink.emit(TokenType.AutolinkUrl, sourceMap.toAbsolute(pos), sourceMap.toAbsolute(urlEnd))
    pos = urlEnd
    pos += 1
    return pos
}

// ---------------------------------------------------------------------------
// Raw HTML
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
// HTML entity
// ---------------------------------------------------------------------------

private fun emitHtmlEntitySpan(
    entity: Inline.HtmlEntity,
    sourceMap: SourceMap,
    sink: SpanSink,
    startPos: Int,
): Int {
    val len = entity.literal.length
    sink.emit(TokenType.EntityRef, sourceMap.toAbsolute(startPos), sourceMap.toAbsolute(startPos + len))
    return startPos + len
}

// ---------------------------------------------------------------------------
// Extended autolink
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
    url.startsWith("http://www.") -> url.length - 7
    url.startsWith("mailto:") -> url.length - 7
    else -> url.length
}
