package parsek.markdown2.parser

import parsek.markdown.ast.Block
import parsek.markdown.ast.Inline
import parsek.markdown2.lexeme.Lexeme
import parsek.markdown2.lexeme.SourceRange
import parsek.markdown2.lexer.*

/**
 * Line-based block parser that handles both leaf and container blocks.
 *
 * Processes lines of lexemes, detecting container markers (block quotes, lists)
 * and recursively parsing inner content. Leaf blocks (headings, code, etc.)
 * are detected and parsed directly.
 */

/**
 * A raw line of lexemes with its original text for convenience.
 */
data class Line(val lexemes: List<Lexeme>, val text: String, val lazy: Boolean = false) {
    companion object {
        fun from(lexemes: List<Lexeme>, lazy: Boolean = false): Line = Line(lexemes, lexemesToText(lexemes), lazy)
    }
}

/**
 * Parses a list of lines into block AST nodes.
 * This is the main recursive entry point — container blocks call this
 * on their inner content.
 */
fun parseLines(lines: List<Line>): List<Block> {
    val blocks = mutableListOf<Block>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // 1. Blank line
        if (isBlankLine(line)) {
            blocks.add(Block.BlankLine)
            i++
            continue
        }

        // 2. Block quote
        val bqStrip = tryStripBlockQuoteMarker(line)
        if (bqStrip != null) {
            val result = collectBlockQuote(lines, i)
            blocks.add(result.first)
            i = result.second
            continue
        }

        // 3. Thematic break (before list markers since `---` is both)
        val tb = tryThematicBreak(line.lexemes)
        if (tb != null) {
            blocks.add(Block.ThematicBreak)
            i++
            continue
        }

        // 4. ATX heading
        val atx = tryAtxHeading(line.lexemes)
        if (atx != null) {
            val level = atx.first.level
            val content = if (atx.second != null) {
                val text = lexemesToText(atx.second!!.lexemes)
                listOf(Inline.Text(text))
            } else {
                emptyList()
            }
            blocks.add(Block.Heading(level, content))
            i++
            continue
        }

        // 5. Fenced code block
        val fence = tryCodeFenceOpen(line.lexemes)
        if (fence != null) {
            val result = collectFencedCode(lines, i, fence)
            blocks.add(result.first)
            i = result.second
            continue
        }

        // 6. HTML block
        val htmlType = detectHtmlBlockType(line.lexemes)
        if (htmlType > 0) {
            val result = collectHtmlBlock(lines, i, htmlType)
            blocks.add(result.first)
            i = result.second
            continue
        }

        // 7. Bullet list
        val bullet = tryStripBulletMarker(line)
        if (bullet != null) {
            val result = collectBulletList(lines, i)
            blocks.add(result.first)
            i = result.second
            continue
        }

        // 8. Ordered list
        val ordered = tryStripOrderedMarker(line)
        if (ordered != null) {
            val result = collectOrderedList(lines, i)
            blocks.add(result.first)
            i = result.second
            continue
        }

        // 9. Indented code block (cannot interrupt paragraph)
        if (isIndentedCodeLine(line)) {
            val result = collectIndentedCode(lines, i)
            blocks.add(result.first)
            i = result.second
            continue
        }

        // 10. Setext underline (orphaned — no preceding paragraph)
        val setext = trySetextUnderline(line.lexemes)
        if (setext != null) {
            // Treat as paragraph content
            val result = collectParagraph(lines, i)
            blocks.addAll(result.first)
            i = result.second
            continue
        }

        // 11. Paragraph (fallback)
        val result = collectParagraph(lines, i)
        blocks.addAll(result.first)
        i = result.second
    }

    return blocks.filter { it !is Block.BlankLine }
}

/**
 * Same as [parseLines] but retains [Block.BlankLine] entries.
 * Used for tight/loose detection in list items.
 */
private fun parseLinesRaw(lines: List<Line>): List<Block> {
    val blocks = mutableListOf<Block>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        if (isBlankLine(line)) {
            blocks.add(Block.BlankLine)
            i++
            continue
        }

        val bqStrip = tryStripBlockQuoteMarker(line)
        if (bqStrip != null) {
            val result = collectBlockQuote(lines, i)
            blocks.add(result.first)
            i = result.second
            continue
        }

        val tb = tryThematicBreak(line.lexemes)
        if (tb != null) {
            blocks.add(Block.ThematicBreak)
            i++
            continue
        }

        val atx = tryAtxHeading(line.lexemes)
        if (atx != null) {
            val level = atx.first.level
            val content = if (atx.second != null) {
                val text = lexemesToText(atx.second!!.lexemes)
                listOf(Inline.Text(text))
            } else {
                emptyList()
            }
            blocks.add(Block.Heading(level, content))
            i++
            continue
        }

        val fence = tryCodeFenceOpen(line.lexemes)
        if (fence != null) {
            val result = collectFencedCode(lines, i, fence)
            blocks.add(result.first)
            i = result.second
            continue
        }

        val htmlType = detectHtmlBlockType(line.lexemes)
        if (htmlType > 0) {
            val result = collectHtmlBlock(lines, i, htmlType)
            blocks.add(result.first)
            i = result.second
            continue
        }

        val bullet = tryStripBulletMarker(line)
        if (bullet != null) {
            val result = collectBulletList(lines, i)
            blocks.add(result.first)
            i = result.second
            continue
        }

        val ordered = tryStripOrderedMarker(line)
        if (ordered != null) {
            val result = collectOrderedList(lines, i)
            blocks.add(result.first)
            i = result.second
            continue
        }

        if (isIndentedCodeLine(line)) {
            val result = collectIndentedCode(lines, i)
            blocks.add(result.first)
            i = result.second
            continue
        }

        val setext = trySetextUnderline(line.lexemes)
        if (setext != null) {
            val result = collectParagraph(lines, i)
            blocks.addAll(result.first)
            i = result.second
            continue
        }

        val result = collectParagraph(lines, i)
        blocks.addAll(result.first)
        i = result.second
    }

    return blocks
}

/**
 * Checks if blocks have a BlankLine between two non-blank blocks.
 */
private fun hasBlankBetweenContent(blocks: List<Block>): Boolean {
    var seenContent = false
    var seenBlankAfterContent = false
    for (block in blocks) {
        if (block is Block.BlankLine) {
            if (seenContent) seenBlankAfterContent = true
        } else {
            if (seenBlankAfterContent) return true
            seenContent = true
        }
    }
    return false
}

// ── Line classification helpers ─────────────────────────────────────────

private fun isBlankLine(line: Line): Boolean = tryBlankLine(line.lexemes) != null

private fun isIndentedCodeLine(line: Line): Boolean = tryIndentedCodeLine(line.lexemes) != null

private fun leadingSpaces(line: Line): Int {
    val lexemes = line.lexemes
    var spaces = 0
    for (lex in lexemes) {
        when (lex) {
            is Lexeme.Space -> spaces++
            is Lexeme.SpaceRun -> spaces += lex.count
            is Lexeme.Tab -> spaces += 4 - (spaces % 4)
            else -> break
        }
    }
    return spaces
}

/**
 * Checks if a line can interrupt a paragraph.
 */
private fun canInterruptParagraph(line: Line): Boolean {
    if (isBlankLine(line)) return true
    if (tryThematicBreak(line.lexemes) != null) return true
    if (tryAtxHeading(line.lexemes) != null) return true
    if (tryCodeFenceOpen(line.lexemes) != null) return true
    if (tryStripBlockQuoteMarker(line) != null) return true
    val htmlType = detectHtmlBlockType(line.lexemes)
    if (htmlType in 1..6) return true

    // Bullet list item with content can interrupt
    val bullet = tryStripBulletMarker(line)
    if (bullet != null && !isBlankLine(bullet.innerLine)) return true

    // Ordered list starting with 1 can interrupt (with content)
    val ordered = tryStripOrderedMarker(line)
    if (ordered != null && ordered.number == 1 && !isBlankLine(ordered.innerLine)) return true

    return false
}

// ── Block quote ─────────────────────────────────────────────────────────

data class BlockQuoteStrip(val innerLine: Line)

/**
 * Tries to strip a block quote marker (`>` + optional space) from a line.
 */
fun tryStripBlockQuoteMarker(line: Line): BlockQuoteStrip? {
    val lexemes = line.lexemes
    var idx = 0
    var column = 0

    // 0–3 leading spaces
    while (idx < lexemes.size) {
        when (val lex = lexemes[idx]) {
            is Lexeme.Space -> { column++; idx++ }
            is Lexeme.SpaceRun -> { column += lex.count; idx++ }
            is Lexeme.Tab -> { column += 4 - (column % 4); idx++ }
            else -> break
        }
    }
    if (column > 3) return null
    if (idx >= lexemes.size) return null

    // Must be `>`
    if (lexemes[idx] !is Lexeme.AngleClose) return null
    idx++
    column++ // `>` takes one column

    // Optional single space after `>` (may be part of a tab)
    if (idx < lexemes.size) {
        when (val lex = lexemes[idx]) {
            is Lexeme.Space -> { idx++; column++ }
            is Lexeme.SpaceRun -> {
                // Consume one space from the run
                if (lex.count == 2) {
                    val inner = mutableListOf<Lexeme>()
                    inner.add(Lexeme.Space(SourceRange(lex.range.start + 1, lex.range.end)))
                    inner.addAll(lexemes.subList(idx + 1, lexemes.size))
                    return BlockQuoteStrip(Line.from(inner))
                } else if (lex.count > 2) {
                    val inner = mutableListOf<Lexeme>()
                    inner.add(Lexeme.SpaceRun(lex.count - 1, SourceRange(lex.range.start + 1, lex.range.end)))
                    inner.addAll(lexemes.subList(idx + 1, lexemes.size))
                    return BlockQuoteStrip(Line.from(inner))
                }
                idx++; column++
            }
            is Lexeme.Tab -> {
                // Tab partially consumed: 1 column for optional space, rest becomes virtual spaces
                val tabWidth = 4 - (column % 4)
                column += tabWidth // advance column past the full tab
                idx++
                if (tabWidth > 1) {
                    val remaining = tabWidth - 1
                    // Expand remaining tabs to spaces from the current column
                    val inner = expandTabsToSpaces(lexemes, idx, column, remaining, lex.range)
                    return BlockQuoteStrip(Line.from(inner))
                }
                // tabWidth == 1: fully consumed
            }
            else -> {} // No optional space
        }
    }

    val inner = if (idx < lexemes.size) lexemes.subList(idx, lexemes.size) else emptyList()
    return BlockQuoteStrip(Line.from(inner))
}

/**
 * Expands leading tabs in a lexeme list to spaces based on column position (tab stops at 4).
 * Only expands tabs in the leading whitespace; non-whitespace triggers return of remaining as-is.
 */
private fun expandLeadingTabs(lexemes: List<Lexeme>): List<Lexeme> {
    // Quick check: are there any tabs in leading whitespace?
    var hasTabs = false
    for (lex in lexemes) {
        when (lex) {
            is Lexeme.Tab -> { hasTabs = true; break }
            is Lexeme.Space, is Lexeme.SpaceRun -> {}
            else -> break
        }
    }
    if (!hasTabs) return lexemes
    return expandTabsToSpaces(lexemes, 0, 0, 0, SourceRange(0, 0))
}

/**
 * Expands tabs in the remaining lexemes to spaces based on column position.
 * Prepends [leadingSpaces] virtual spaces from partial tab consumption.
 */
private fun expandTabsToSpaces(
    lexemes: List<Lexeme>,
    startIdx: Int,
    startColumn: Int,
    leadingSpaces: Int,
    dummyRange: SourceRange,
): List<Lexeme> {
    val result = mutableListOf<Lexeme>()
    if (leadingSpaces == 1) {
        result.add(Lexeme.Space(dummyRange))
    } else if (leadingSpaces > 1) {
        result.add(Lexeme.SpaceRun(leadingSpaces, dummyRange))
    }
    var col = startColumn
    for (i in startIdx until lexemes.size) {
        val lex = lexemes[i]
        when (lex) {
            is Lexeme.Tab -> {
                val w = 4 - (col % 4)
                if (w == 1) {
                    result.add(Lexeme.Space(lex.range))
                } else {
                    result.add(Lexeme.SpaceRun(w, lex.range))
                }
                col += w
            }
            is Lexeme.Space -> { result.add(lex); col++ }
            is Lexeme.SpaceRun -> { result.add(lex); col += lex.count }
            else -> {
                // Non-whitespace: add remaining lexemes as-is
                result.addAll(lexemes.subList(i, lexemes.size))
                return result
            }
        }
    }
    return result
}

/**
 * Collects a block quote: lines starting with `>`, plus lazy continuation lines.
 */
private fun collectBlockQuote(lines: List<Line>, startIdx: Int): Pair<Block.BlockQuote, Int> {
    val innerLines = mutableListOf<Line>()
    var i = startIdx

    var lastInnerWasBlank = false
    // Track whether the last open block is a paragraph (for lazy continuation)
    var lastBlockIsParagraph = false
    var openFence: Pair<parsek.markdown2.token.Token.CodeFenceOpen, parsek.markdown2.token.Token.CodeFenceInfo?>? = null

    while (i < lines.size) {
        val line = lines[i]
        val strip = tryStripBlockQuoteMarker(line)
        if (strip != null) {
            lastInnerWasBlank = isBlankLine(strip.innerLine)
            innerLines.add(strip.innerLine)
            // Update block type tracking
            if (!lastInnerWasBlank) {
                if (openFence != null) {
                    // Inside a fenced code block — check for close
                    val close = tryCodeFenceClose(strip.innerLine.lexemes, openFence!!.first.fenceChar, openFence!!.first.fenceLength)
                    if (close != null) {
                        openFence = null
                        lastBlockIsParagraph = false
                    }
                    // Otherwise still inside fence
                } else {
                    val fence = tryCodeFenceOpen(strip.innerLine.lexemes)
                    if (fence != null) {
                        openFence = fence
                        lastBlockIsParagraph = false
                    } else if (isIndentedCodeLine(strip.innerLine)) {
                        lastBlockIsParagraph = false
                    } else if (tryThematicBreak(strip.innerLine.lexemes) != null) {
                        lastBlockIsParagraph = false
                    } else if (tryAtxHeading(strip.innerLine.lexemes) != null) {
                        lastBlockIsParagraph = false
                    } else if (detectHtmlBlockType(strip.innerLine.lexemes) > 0) {
                        lastBlockIsParagraph = false
                    } else {
                        lastBlockIsParagraph = true
                    }
                }
            }
            i++
        } else if (isBlankLine(line)) {
            // Blank line without `>` marker always ends the block quote
            break
        } else if (!lastInnerWasBlank && lastBlockIsParagraph && !canInterruptParagraph(line) && innerLines.isNotEmpty()) {
            // Lazy continuation: non-blank line that doesn't interrupt paragraph
            // Only applies when the last inner block is a paragraph (not code/fence/etc.)
            innerLines.add(Line(line.lexemes, line.text, lazy = true))
            i++
        } else {
            break
        }
    }

    val innerBlocks = parseLines(innerLines)
    return Block.BlockQuote(innerBlocks) to i
}

// ── List markers ────────────────────────────────────────────────────────

data class BulletStrip(
    val marker: Char,
    val innerLine: Line,
    val contentIndent: Int, // total indent width (leading spaces + marker + space after)
)

data class OrderedStrip(
    val number: Int,
    val delimiter: Char,
    val innerLine: Line,
    val contentIndent: Int,
)

/**
 * Tries to strip a bullet list marker (`-`, `+`, `*`) from a line.
 */
fun tryStripBulletMarker(line: Line): BulletStrip? {
    val lexemes = line.lexemes
    var idx = 0
    var leadSpaces = 0

    // 0–3 leading spaces
    while (idx < lexemes.size) {
        when (val lex = lexemes[idx]) {
            is Lexeme.Space -> { leadSpaces++; idx++ }
            is Lexeme.SpaceRun -> { leadSpaces += lex.count; idx++ }
            is Lexeme.Tab -> { leadSpaces += 4 - (leadSpaces % 4); idx++ }
            else -> break
        }
    }
    if (leadSpaces > 3) return null
    if (idx >= lexemes.size) return null

    // Marker: -, +, or *
    val marker = when (lexemes[idx]) {
        is Lexeme.Hyphen -> '-'
        is Lexeme.Plus -> '+'
        is Lexeme.Asterisk -> '*'
        else -> return null
    }
    idx++

    // Must be followed by space/tab or end of line
    if (idx < lexemes.size) {
        when (lexemes[idx]) {
            is Lexeme.Space, is Lexeme.SpaceRun, is Lexeme.Tab, is Lexeme.Newline -> {}
            else -> return null
        }
    }

    // Count spaces after marker (1–4)
    var spacesAfter = 0
    val spaceStart = idx
    while (idx < lexemes.size && spacesAfter < 4) {
        when (val lex = lexemes[idx]) {
            is Lexeme.Space -> { spacesAfter++; idx++ }
            is Lexeme.SpaceRun -> {
                val take = minOf(lex.count, 4 - spacesAfter)
                spacesAfter += take
                if (take == lex.count) idx++ else break
            }
            is Lexeme.Tab -> {
                val w = 4 - ((leadSpaces + 1 + spacesAfter) % 4)
                spacesAfter += w
                idx++
            }
            else -> break
        }
    }

    // If 5+ spaces after marker, treat as 1 space (rest is content indentation)
    val contentIndent: Int
    val innerStart: Int
    if (spacesAfter > 4 || (idx < lexemes.size && isBlankLine(Line.from(lexemes.subList(idx, lexemes.size))))) {
        contentIndent = leadSpaces + 1 + 1 // leading + marker + 1 space
        innerStart = spaceStart
        // Consume only 1 space
        var si = spaceStart
        if (si < lexemes.size) {
            when (lexemes[si]) {
                is Lexeme.Space -> si++
                is Lexeme.SpaceRun -> {
                    // Split: consume 1 space, leave rest
                    val run = lexemes[si] as Lexeme.SpaceRun
                    val inner = mutableListOf<Lexeme>()
                    if (run.count > 2) {
                        inner.add(Lexeme.SpaceRun(run.count - 1, SourceRange(run.range.start + 1, run.range.end)))
                    } else if (run.count == 2) {
                        inner.add(Lexeme.Space(SourceRange(run.range.start + 1, run.range.end)))
                    }
                    inner.addAll(lexemes.subList(si + 1, lexemes.size))
                    return BulletStrip(marker, Line.from(inner), contentIndent)
                }
                is Lexeme.Tab -> {
                    // Tab partially consumed: 1 column for space, rest as virtual spaces
                    val tabCol = leadSpaces + 1 // column position of the tab (after leading + marker)
                    val tabWidth = 4 - (tabCol % 4)
                    si++
                    if (tabWidth > 1) {
                        val remaining = tabWidth - 1
                        val inner = expandTabsToSpaces(lexemes, si, tabCol + tabWidth, remaining, (lexemes[si - 1] as Lexeme.Tab).range)
                        return BulletStrip(marker, Line.from(inner), contentIndent)
                    }
                    // tabWidth == 1: fully consumed
                }
                else -> {}
            }
        }
        val inner = if (si < lexemes.size) lexemes.subList(si, lexemes.size) else emptyList()
        return BulletStrip(marker, Line.from(inner), contentIndent)
    }

    if (spacesAfter == 0 && idx < lexemes.size && lexemes[idx] !is Lexeme.Newline) {
        return null // No space after marker and not EOL
    }

    contentIndent = leadSpaces + 1 + maxOf(spacesAfter, 1)
    val inner = if (idx < lexemes.size) lexemes.subList(idx, lexemes.size) else emptyList()
    return BulletStrip(marker, Line.from(inner), contentIndent)
}

/**
 * Tries to strip an ordered list marker (digits + `.` or `)`) from a line.
 */
fun tryStripOrderedMarker(line: Line): OrderedStrip? {
    val lexemes = line.lexemes
    var idx = 0
    var leadSpaces = 0

    // 0–3 leading spaces
    while (idx < lexemes.size) {
        when (val lex = lexemes[idx]) {
            is Lexeme.Space -> { leadSpaces++; idx++ }
            is Lexeme.SpaceRun -> { leadSpaces += lex.count; idx++ }
            is Lexeme.Tab -> { leadSpaces += 4 - (leadSpaces % 4); idx++ }
            else -> break
        }
    }
    if (leadSpaces > 3) return null
    if (idx >= lexemes.size) return null

    // Digits (1–9 digits)
    val digitStart = idx
    if (lexemes[idx] !is Lexeme.DigitRun) return null
    val digitText = (lexemes[idx] as Lexeme.DigitRun).text
    if (digitText.length > 9) return null
    val number = digitText.toIntOrNull() ?: return null
    idx++

    // Delimiter: . or )
    if (idx >= lexemes.size) return null
    val delimiter = when (lexemes[idx]) {
        is Lexeme.Period -> '.'
        is Lexeme.ParenClose -> ')'
        else -> return null
    }
    val markerLength = digitText.length + 1 // digits + delimiter
    idx++

    // Must be followed by space/tab or end of line
    if (idx < lexemes.size) {
        when (lexemes[idx]) {
            is Lexeme.Space, is Lexeme.SpaceRun, is Lexeme.Tab, is Lexeme.Newline -> {}
            else -> return null
        }
    }

    // Count total available spaces after marker
    var totalSpaces = 0
    val spaceStartIdx = idx
    var tempIdx = idx
    while (tempIdx < lexemes.size) {
        when (val lex = lexemes[tempIdx]) {
            is Lexeme.Space -> { totalSpaces++; tempIdx++ }
            is Lexeme.SpaceRun -> { totalSpaces += lex.count; tempIdx++ }
            is Lexeme.Tab -> {
                totalSpaces += 4 - ((leadSpaces + markerLength + totalSpaces) % 4)
                tempIdx++
            }
            else -> break
        }
    }

    // Check if line is blank after marker
    val isBlankAfterMarker = tempIdx >= lexemes.size ||
            lexemes[tempIdx] is Lexeme.Newline

    if (totalSpaces == 0 && !isBlankAfterMarker) {
        return null
    }

    // 5+ spaces or blank after marker → treat as 1 space
    if (totalSpaces >= 5 || isBlankAfterMarker) {
        val contentIndent = leadSpaces + markerLength + 1
        // Consume only 1 space
        var si = spaceStartIdx
        if (si < lexemes.size) {
            when (val lex = lexemes[si]) {
                is Lexeme.Space -> si++
                is Lexeme.SpaceRun -> {
                    val inner = mutableListOf<Lexeme>()
                    if (lex.count > 2) {
                        inner.add(Lexeme.SpaceRun(lex.count - 1, SourceRange(lex.range.start + 1, lex.range.end)))
                    } else if (lex.count == 2) {
                        inner.add(Lexeme.Space(SourceRange(lex.range.start + 1, lex.range.end)))
                    }
                    inner.addAll(lexemes.subList(si + 1, lexemes.size))
                    return OrderedStrip(number, delimiter, Line.from(inner), contentIndent)
                }
                is Lexeme.Tab -> si++
                else -> {}
            }
        }
        val inner = if (si < lexemes.size) lexemes.subList(si, lexemes.size) else emptyList()
        return OrderedStrip(number, delimiter, Line.from(inner), contentIndent)
    }

    // Normal case: consume 1-4 spaces
    var spacesAfter = 0
    while (idx < lexemes.size && spacesAfter < 4) {
        when (val lex = lexemes[idx]) {
            is Lexeme.Space -> { spacesAfter++; idx++ }
            is Lexeme.SpaceRun -> {
                val take = minOf(lex.count, 4 - spacesAfter)
                spacesAfter += take
                if (take == lex.count) idx++ else break
            }
            is Lexeme.Tab -> {
                val w = 4 - ((leadSpaces + markerLength + spacesAfter) % 4)
                spacesAfter += w
                idx++
            }
            else -> break
        }
    }

    val contentIndent = leadSpaces + markerLength + maxOf(spacesAfter, 1)
    val inner = if (idx < lexemes.size) lexemes.subList(idx, lexemes.size) else emptyList()
    return OrderedStrip(number, delimiter, Line.from(inner), contentIndent)
}

// ── List collection ─────────────────────────────────────────────────────

/**
 * Collects a bullet list: consecutive compatible bullet items.
 */
private fun collectBulletList(lines: List<Line>, startIdx: Int): Pair<Block.BulletList, Int> {
    val firstStrip = tryStripBulletMarker(lines[startIdx])!!
    val marker = firstStrip.marker
    val items = mutableListOf<Block.ListItem>()
    var loose = false
    var lastBlankAfter = false
    var i = startIdx

    while (i < lines.size) {
        // Skip blank lines between items
        val blankStart = i
        while (i < lines.size && isBlankLine(lines[i])) i++
        if (i > blankStart && items.isNotEmpty()) lastBlankAfter = true
        if (i >= lines.size) break

        // Thematic break takes priority over bullet list item
        if (tryThematicBreak(lines[i].lexemes) != null) {
            if (i > blankStart && items.isNotEmpty()) i = blankStart
            break
        }

        val strip = tryStripBulletMarker(lines[i])
        if (strip == null || strip.marker != marker) {
            // Rewind blanks — they don't belong to this list
            if (i > blankStart && items.isNotEmpty()) i = blankStart
            break
        }

        // Blank line between previous and this item → loose
        if (lastBlankAfter) loose = true

        val result = collectListItem(lines, i, strip.contentIndent, strip.innerLine)
        items.add(result.item)
        if (result.containsBlank) loose = true
        lastBlankAfter = result.blankAfter
        i = result.nextIndex
    }

    return Block.BulletList(!loose, marker, items) to i
}

/**
 * Collects an ordered list: consecutive compatible ordered items.
 */
private fun collectOrderedList(lines: List<Line>, startIdx: Int): Pair<Block.OrderedList, Int> {
    val firstStrip = tryStripOrderedMarker(lines[startIdx])!!
    val delimiter = firstStrip.delimiter
    val startNumber = firstStrip.number
    val items = mutableListOf<Block.ListItem>()
    var loose = false
    var lastBlankAfter = false
    var i = startIdx

    while (i < lines.size) {
        // Skip blank lines between items
        val blankStart = i
        while (i < lines.size && isBlankLine(lines[i])) i++
        if (i > blankStart && items.isNotEmpty()) lastBlankAfter = true
        if (i >= lines.size) break

        val strip = tryStripOrderedMarker(lines[i])
        if (strip == null || strip.delimiter != delimiter) {
            if (i > blankStart && items.isNotEmpty()) i = blankStart
            break
        }

        // Check thematic break doesn't interrupt
        if (tryThematicBreak(lines[i].lexemes) != null) {
            if (i > blankStart && items.isNotEmpty()) i = blankStart
            break
        }

        // Blank line between previous and this item → loose
        if (lastBlankAfter) loose = true

        val result = collectListItem(lines, i, strip.contentIndent, strip.innerLine)
        items.add(result.item)
        if (result.containsBlank) loose = true
        lastBlankAfter = result.blankAfter
        i = result.nextIndex
    }

    return Block.OrderedList(!loose, startNumber, delimiter, items) to i
}

/**
 * Result of collecting a list item.
 * @param item The parsed list item
 * @param nextIndex Index of the next line after this item
 * @param blankAfter True if there was a blank line between this and next item
 * @param containsBlank True if a blank line separates content within this item
 */
data class ListItemResult(
    val item: Block.ListItem,
    val nextIndex: Int,
    val blankAfter: Boolean,
    val containsBlank: Boolean,
)

/**
 * Collects a single list item: the first line (after marker stripping)
 * plus continuation lines indented by at least [contentIndent] spaces.
 */
private fun collectListItem(
    lines: List<Line>,
    startIdx: Int,
    contentIndent: Int,
    firstInnerLine: Line,
): ListItemResult {
    val innerLines = mutableListOf(firstInnerLine)
    var i = startIdx + 1
    var hadBlank = false       // any blank seen (for lazy continuation check)
    var trailingBlank = false  // consecutive blanks at end (for between-item looseness)
    var isEmptyItem = isBlankLine(firstInnerLine)

    while (i < lines.size) {
        val line = lines[i]

        if (isBlankLine(line)) {
            if (isEmptyItem) break // Empty item + blank = end
            // Look ahead: if next non-blank line isn't indented enough, don't consume the blank
            var lookAhead = i + 1
            while (lookAhead < lines.size && isBlankLine(lines[lookAhead])) lookAhead++
            if (lookAhead >= lines.size || leadingSpaces(lines[lookAhead]) < contentIndent) {
                // Blank followed by non-continuation or end — leave blank for parent
                trailingBlank = true
                break
            }
            hadBlank = true
            trailingBlank = true
            innerLines.add(Line(emptyList(), "\n"))
            i++
            continue
        }

        // Check if line is indented enough for continuation
        val ls = leadingSpaces(line)
        if (ls >= contentIndent) {
            // Continuation — expand tabs to spaces first, then strip the indent
            val expanded = expandLeadingTabs(line.lexemes)
            val stripped = stripIndent(expanded, contentIndent)
            innerLines.add(Line.from(stripped))
            isEmptyItem = false
            trailingBlank = false
            i++
        } else if (tryStripBulletMarker(line) != null || tryStripOrderedMarker(line) != null) {
            // New list item at same or outer level — end this item
            break
        } else if (!hadBlank && !canInterruptParagraph(line)) {
            // Lazy continuation (no blank line between, doesn't interrupt)
            innerLines.add(line)
            isEmptyItem = false
            trailingBlank = false
            i++
        } else {
            break
        }
    }

    // Remove trailing blank lines from inner content
    while (innerLines.isNotEmpty() && isBlankLine(innerLines.last())) {
        innerLines.removeAt(innerLines.size - 1)
    }

    val innerBlocksRaw = parseLinesRaw(innerLines)
    // Check for blank lines between direct block children
    val hasDirectBlanks = hasBlankBetweenContent(innerBlocksRaw)
    val innerBlocks = innerBlocksRaw.filter { it !is Block.BlankLine }
    return ListItemResult(Block.ListItem(innerBlocks), i, trailingBlank, hasDirectBlanks)
}

// ── Fenced code ─────────────────────────────────────────────────────────

private fun collectFencedCode(
    lines: List<Line>,
    startIdx: Int,
    fence: Pair<parsek.markdown2.token.Token.CodeFenceOpen, parsek.markdown2.token.Token.CodeFenceInfo?>,
): Pair<Block.FencedCodeBlock, Int> {
    val open = fence.first
    val info = fence.second?.info?.let { resolveEntities(processBackslashEscapes(it)) }
    val contentBuilder = StringBuilder()
    var i = startIdx + 1

    while (i < lines.size) {
        val close = tryCodeFenceClose(lines[i].lexemes, open.fenceChar, open.fenceLength)
        if (close != null) {
            i++
            break
        }
        // Strip indent up to opening fence indent
        val stripped = if (open.indent > 0) stripIndent(lines[i].lexemes, open.indent) else lines[i].lexemes
        val lineText = lexemesToText(stripped)
        // Blank lines with empty lexemes still contribute a newline
        contentBuilder.append(if (lineText.isEmpty() && isBlankLine(lines[i])) "\n" else lineText)
        i++
    }

    return Block.FencedCodeBlock(info, contentBuilder.toString()) to i
}

// ── HTML block ──────────────────────────────────────────────────────────

private fun collectHtmlBlock(
    lines: List<Line>,
    startIdx: Int,
    htmlType: Int,
): Pair<Block.HtmlBlock, Int> {
    val contentBuilder = StringBuilder()
    contentBuilder.append(lines[startIdx].text)
    var i = startIdx + 1

    // Check if start line also ends the block
    if (htmlType in 1..5 && htmlBlockEndCondition(lines[startIdx].text, htmlType)) {
        return Block.HtmlBlock(contentBuilder.toString()) to i
    }

    while (i < lines.size) {
        val line = lines[i]
        when (htmlType) {
            1, 2, 3, 4, 5 -> {
                contentBuilder.append(line.text)
                i++
                if (htmlBlockEndCondition(line.text, htmlType)) break
            }
            6, 7 -> {
                if (isBlankLine(line)) break // Blank ends type 6/7
                contentBuilder.append(line.text)
                i++
            }
            else -> break
        }
    }

    return Block.HtmlBlock(contentBuilder.toString()) to i
}

// ── Indented code ───────────────────────────────────────────────────────

private fun collectIndentedCode(
    lines: List<Line>,
    startIdx: Int,
): Pair<Block.IndentedCodeBlock, Int> {
    data class CodeLine(val isBlank: Boolean, val content: String)

    val allLines = mutableListOf<CodeLine>()
    var i = startIdx

    while (i < lines.size) {
        val line = lines[i]
        if (isIndentedCodeLine(line)) {
            val stripped = stripIndent(line.lexemes, 4)
            allLines.add(CodeLine(false, lexemesToText(stripped)))
            i++
        } else if (isBlankLine(line)) {
            // Check if it's an indented blank (4+ spaces)
            val ls = leadingSpaces(line)
            if (ls >= 4) {
                val stripped = stripIndent(line.lexemes, 4)
                allLines.add(CodeLine(true, lexemesToText(stripped)))
            } else {
                allLines.add(CodeLine(true, "\n"))
            }
            i++
        } else {
            break
        }
    }

    // Strip leading and trailing blank lines
    val firstNonBlank = allLines.indexOfFirst { !it.isBlank }
    val lastNonBlank = allLines.indexOfLast { !it.isBlank }

    if (firstNonBlank == -1) {
        // All blank — rewind, emit blank
        return Block.IndentedCodeBlock("\n") to startIdx + 1
    }

    // Rewind trailing blanks
    val trailingCount = allLines.size - lastNonBlank - 1
    if (trailingCount > 0) i -= trailingCount

    val content = buildString {
        for (idx in firstNonBlank..lastNonBlank) {
            append(allLines[idx].content)
        }
    }

    return Block.IndentedCodeBlock(content) to i
}

// ── Paragraph / Setext ──────────────────────────────────────────────────

private fun collectParagraph(
    lines: List<Line>,
    startIdx: Int,
): Pair<List<Block>, Int> {
    val textParts = mutableListOf<String>()
    var i = startIdx
    val firstLine = lines[i]

    // First line may be a setext underline (orphaned) — use its text
    val setext = trySetextUnderline(firstLine.lexemes)
    if (setext != null) {
        textParts.add(setext.text)
    } else {
        textParts.add(extractParagraphText(firstLine))
    }
    i++

    while (i < lines.size) {
        val line = lines[i]

        if (isBlankLine(line)) break

        // Setext underline → heading (but first strip leading link ref defs)
        // Lazy continuation lines cannot be setext underlines (CommonMark §4.3)
        val su = if (!line.lazy) trySetextUnderline(line.lexemes) else null
        if (su != null) {
            val text = textParts.joinToString("\n")
            val (defs, remaining) = parseLinkRefDefs(text + "\n")
            val trimmed = remaining.trimEnd('\n').trim()
            val blocks = mutableListOf<Block>()
            for (def in defs) {
                blocks.add(Block.LinkReferenceDefinition(def.label, def.destination, def.title))
            }
            if (trimmed.isNotEmpty()) {
                blocks.add(Block.Heading(su.level, listOf(Inline.Text(trimmed))))
            } else {
                // All content was ref defs; setext underline is orphaned.
                // Continue collecting paragraph lines starting from the setext line.
                textParts.clear()
                textParts.add(su.text)
                i++
                // Fall through to continue the paragraph collection loop
                // to merge with subsequent lines
                while (i < lines.size) {
                    val nextLine = lines[i]
                    if (isBlankLine(nextLine)) break
                    val nextSu = trySetextUnderline(nextLine.lexemes)
                    if (nextSu != null) {
                        val paraText = textParts.joinToString("\n")
                        blocks.add(Block.Heading(nextSu.level, listOf(Inline.Text(paraText))))
                        return blocks to (i + 1)
                    }
                    val nextTb = tryThematicBreak(nextLine.lexemes)
                    if (nextTb != null && nextTb.marker == '-' && trySetextUnderline(nextLine.lexemes) != null) {
                        val paraText = textParts.joinToString("\n")
                        blocks.add(Block.Heading(2, listOf(Inline.Text(paraText))))
                        return blocks to (i + 1)
                    }
                    if (nextTb != null) break
                    if (canInterruptParagraph(nextLine)) break
                    textParts.add(extractParagraphText(nextLine))
                    i++
                }
                val paraText = textParts.joinToString("\n")
                blocks.add(Block.Paragraph(listOf(Inline.Text(paraText))))
                return blocks to i
            }
            return blocks to (i + 1)
        }

        // Thematic break with hyphens that is ALSO a setext underline → heading
        // Lazy continuation lines cannot be setext underlines (CommonMark §4.3)
        val tb = tryThematicBreak(line.lexemes)
        if (tb != null && tb.marker == '-' && !line.lazy && trySetextUnderline(line.lexemes) != null) {
            val text = textParts.joinToString("\n")
            val (defs, remaining) = parseLinkRefDefs(text + "\n")
            val trimmed = remaining.trimEnd('\n').trim()
            val blocks = mutableListOf<Block>()
            for (def in defs) {
                blocks.add(Block.LinkReferenceDefinition(def.label, def.destination, def.title))
            }
            if (trimmed.isNotEmpty()) {
                blocks.add(Block.Heading(2, listOf(Inline.Text(trimmed))))
            } else {
                // All content was ref defs; `---` becomes thematic break
                blocks.add(Block.ThematicBreak)
            }
            return blocks to (i + 1)
        }
        // Non-setext thematic break (like `--- -`) interrupts the paragraph
        if (tb != null) break

        // Other paragraph-interrupting constructs
        if (canInterruptParagraph(line)) break

        // Continuation line
        textParts.add(extractParagraphText(line))
        i++
    }

    val text = textParts.joinToString("\n")
    return listOf(Block.Paragraph(listOf(Inline.Text(text)))) to i
}

/**
 * Extracts the text content of a paragraph line.
 *
 * Strips all leading whitespace (spaces, tabs) from the line per CommonMark §4.8:
 * "The contents are the result of removing the optional leading spaces or tabs."
 * Also removes trailing newline.
 */
private fun extractParagraphText(line: Line): String {
    val lexemes = line.lexemes
    var idx = 0
    while (idx < lexemes.size) {
        when (lexemes[idx]) {
            is Lexeme.Space, is Lexeme.SpaceRun, is Lexeme.Tab -> idx++
            else -> break
        }
    }
    val content = if (idx > 0) lexemes.subList(idx, lexemes.size) else lexemes
    // Remove trailing newline
    val trimmed = if (content.isNotEmpty() && content.last() is Lexeme.Newline) {
        content.subList(0, content.size - 1)
    } else {
        content
    }
    return lexemesToText(trimmed)
}

private val BACKSLASH_ESCAPABLE = setOf(
    '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.',
    '/', ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '^', '_', '`',
    '{', '|', '}', '~',
)

/**
 * Processes backslash escapes in a string (e.g., code fence info strings).
 */
private fun processBackslashEscapes(text: String): String = buildString {
    var i = 0
    while (i < text.length) {
        if (text[i] == '\\' && i + 1 < text.length && text[i + 1] in BACKSLASH_ESCAPABLE) {
            append(text[i + 1])
            i += 2
        } else {
            append(text[i])
            i++
        }
    }
}
