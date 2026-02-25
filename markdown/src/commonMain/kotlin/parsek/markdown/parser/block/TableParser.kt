package parsek.markdown.parser.block

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.markdown.ast.Block
import parsek.markdown.ast.Inline
import parsek.pLabel

/**
 * Parses a GFM table (extension, §4.10).
 *
 * A table is recognised when a line of pipe-separated cells (the header row) is
 * immediately followed by a delimiter row consisting of cells of hyphens and
 * optional colons for alignment.
 *
 * Rules:
 * - Cells are separated by `|`. Leading and trailing pipes are optional.
 * - Spaces between pipes and cell content are trimmed.
 * - The delimiter row must have the same number of cells as the header.
 * - Body rows may have fewer (padded with empty cells) or more (excess ignored) cells.
 * - Pipes can be escaped with `\|` inside cells.
 * - A table ends at the first blank line or the start of another block-level structure.
 * - Tables cannot interrupt a paragraph (handled by the caller).
 *
 * Inline content inside cells is produced as a single [Inline.Text] stub that
 * the document parser's inline resolution pass will later replace.
 *
 * @return a [Parser] that succeeds with [Block.Table] or fails.
 */
fun <U : Any> pTable(): Parser<Char, Block.Table, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            var idx = input.index

            // 1. Read the first line as a candidate header row.
            val (headerLine, afterHeader) = readTableLine(chars, idx)
                ?: return@Parser Failure("table", idx, input)
            val headerCells = splitTableCells(headerLine)
            if (headerCells.isEmpty())
                return@Parser Failure("table", idx, input)

            // 2. Read the second line as a candidate delimiter row.
            val (delimLine, afterDelim) = readTableLine(chars, afterHeader)
                ?: return@Parser Failure("table", idx, input)
            val delimCells = splitTableCells(delimLine)

            // 3. Validate the delimiter row: must have same count as header,
            //    and each cell must match the alignment pattern.
            if (delimCells.size != headerCells.size)
                return@Parser Failure("table", idx, input)

            val alignments = mutableListOf<Block.Alignment>()
            for (cell in delimCells) {
                val alignment = parseAlignmentCell(cell)
                    ?: return@Parser Failure("table", idx, input)
                alignments.add(alignment)
            }

            // We have a valid table. Build the header row.
            val colCount = headerCells.size
            val headerRow = Block.TableRow(
                headerCells.map { Block.TableCell(stubInlines(it)) }
            )

            // 4. Consume body rows until blank line, EOF, or block interrupt.
            idx = afterDelim
            val bodyRows = mutableListOf<Block.TableRow>()
            while (idx < chars.size) {
                // Check for blank line.
                if (isTableBlankLine(chars, idx)) break

                // Check for block-level interrupt (e.g. `>`, `#`, fence).
                if (canInterruptParagraph(chars, idx)) break

                val (rowLine, afterRow) = readTableLine(chars, idx) ?: break
                val rowCells = splitTableCells(rowLine)
                // Pad to colCount or truncate.
                val normalised = (0 until colCount).map { i ->
                    Block.TableCell(stubInlines(rowCells.getOrElse(i) { "" }))
                }
                bodyRows.add(Block.TableRow(normalised))
                idx = afterRow
            }

            Success(
                Block.Table(alignments, headerRow, bodyRows),
                idx,
                input,
            )
        },
        "table",
    )

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

/**
 * Reads one line (without the line ending) and returns `(content, nextIdx)`,
 * or `null` if at EOF.
 */
private fun readTableLine(chars: List<Char>, startIdx: Int): Pair<String, Int>? {
    if (startIdx >= chars.size) return null
    var i = startIdx
    while (i < chars.size && chars[i] != '\n' && chars[i] != '\r') i++
    val content = chars.subList(startIdx, i).joinToString("")
    val nextIdx = when {
        i >= chars.size -> i
        chars[i] == '\r' && i + 1 < chars.size && chars[i + 1] == '\n' -> i + 2
        else -> i + 1
    }
    return Pair(content, nextIdx)
}

/**
 * Splits a line into table cells by unescaped `|` characters.
 *
 * Leading and trailing pipes are stripped (they are optional delimiters, not
 * empty cells). Each cell's content is trimmed of surrounding whitespace.
 *
 * Escaped pipes (`\|`) are left as literal text and do not act as separators.
 */
internal fun splitTableCells(line: String): List<String> {
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var i = 0
    while (i < line.length) {
        when {
            line[i] == '\\' && i + 1 < line.length && line[i + 1] == '|' -> {
                // Table-level escape: \| becomes | in cell content.
                current.append("|")
                i += 2
            }
            // Preserve content inside backtick spans: `|` inside code spans
            // must not act as a cell separator. Also apply `\|` → `|` escape.
            line[i] == '`' -> {
                val tickStart = i
                var tickLen = 0
                while (i < line.length && line[i] == '`') { tickLen++; i++ }
                current.append(line, tickStart, i)
                // Find matching closing backtick run.
                val closeStart = findClosingBackticks(line, i, tickLen)
                if (closeStart != -1) {
                    // Apply \| → | within the backtick span content.
                    val spanContent = line.substring(i, closeStart + tickLen)
                    current.append(spanContent.replace("\\|", "|"))
                    i = closeStart + tickLen
                }
                // If no closing run, the backticks are literal — already appended.
            }
            line[i] == '|' -> {
                cells.add(current.toString().trim())
                current.clear()
                i++
            }
            else -> {
                current.append(line[i])
                i++
            }
        }
    }
    // Add the trailing segment after the last pipe.
    cells.add(current.toString().trim())

    // Strip leading empty cell (from leading pipe) and trailing empty cell (from
    // trailing pipe), but only if they are truly empty after trimming.
    if (cells.isNotEmpty() && cells.first().isEmpty()) cells.removeAt(0)
    if (cells.isNotEmpty() && cells.last().isEmpty()) cells.removeAt(cells.lastIndex)

    return cells
}

/**
 * Finds the start index of a closing backtick run of length [tickLen] in
 * [line] starting the search at [from]. Returns -1 if not found.
 */
private fun findClosingBackticks(line: String, from: Int, tickLen: Int): Int {
    var i = from
    while (i < line.length) {
        if (line[i] == '`') {
            val start = i
            var count = 0
            while (i < line.length && line[i] == '`') { count++; i++ }
            if (count == tickLen) return start
        } else {
            i++
        }
    }
    return -1
}

/**
 * Parses a delimiter-row cell and returns the alignment, or `null` if
 * the cell does not match the pattern `[:]-+[:]`.
 */
internal fun parseAlignmentCell(cell: String): Block.Alignment? {
    val trimmed = cell.trim()
    if (trimmed.isEmpty()) return null

    val left = trimmed.startsWith(':')
    val right = trimmed.endsWith(':')

    // The inner content (between optional colons) must be all hyphens.
    val start = if (left) 1 else 0
    val end = if (right) trimmed.length - 1 else trimmed.length
    if (start >= end) return null // must have at least one hyphen
    for (i in start until end) {
        if (trimmed[i] != '-') return null
    }

    return when {
        left && right -> Block.Alignment.CENTER
        left -> Block.Alignment.LEFT
        right -> Block.Alignment.RIGHT
        else -> Block.Alignment.NONE
    }
}

/**
 * Returns `true` if the position [idx] starts a blank line
 * (only spaces/tabs before the next line ending or EOF).
 */
private fun isTableBlankLine(chars: List<Char>, idx: Int): Boolean {
    var i = idx
    while (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) i++
    return i >= chars.size || chars[i] == '\n' || chars[i] == '\r'
}

/**
 * Wraps cell content into a stub [Inline.Text] list for later inline resolution.
 */
private fun stubInlines(content: String): List<Inline> =
    if (content.isEmpty()) emptyList() else listOf(Inline.Text(content))
