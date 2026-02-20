package parsek.commonmark.parser.block

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.pEof
import parsek.pLabel
import parsek.pMap
import parsek.pOr
import parsek.text.pBlankLine
import parsek.text.pLineEnding
import parsek.text.pRestOfLine

/**
 * Parses a CommonMark indented code block.
 *
 * An indented code block is a maximal sequence of lines where:
 * - Each non-blank line begins with 4 spaces or 1 tab (the code-block indent).
 * - Blank lines (lines with only spaces/tabs) may appear between indented lines.
 * - Trailing blank lines are **not** consumed — they are left in the input for
 *   the document parser.
 *
 * The 4-space (or 1-tab) prefix is stripped from each indented line; any
 * additional leading spaces beyond the indent are preserved verbatim. Blank
 * lines within the block contribute an empty line (`""`) to the literal. A
 * trailing `'\n'` is appended to the literal regardless of how the last line
 * ends in the source.
 *
 * **Note:** An indented code block cannot interrupt a paragraph. The document
 * parser enforces this ordering constraint; this parser itself is unaware of it.
 *
 * @return a [Parser] that succeeds with [Block.IndentedCodeBlock] or fails.
 */
fun <U : Any> pIndentedCodeBlock(): Parser<Char, Block.IndentedCodeBlock, U> =
    pLabel(
        Parser { input ->
            val blank = pBlankLine<U>()
            val lineEnding = pOr(
                pMap(pLineEnding<U>()) { Unit },
                pMap(pEof<Char, U>()) { Unit },
            )

            val lines = mutableListOf<String>()
            var idx = input.index
            var committed = idx   // last position confirmed to be inside the block
            var pendingBlanks = 0 // blank lines seen since last committed indented line

            while (true) {
                val pos = ParserInput(input.input, idx, input.userContext)

                // Determine whether the current line begins with a code-block indent.
                val indentIdx: Int? = when {
                    // Four consecutive spaces.
                    idx + 4 <= input.input.size &&
                        input.input[idx] == ' ' && input.input[idx + 1] == ' ' &&
                        input.input[idx + 2] == ' ' && input.input[idx + 3] == ' ' -> idx + 4
                    // One tab.
                    idx < input.input.size && input.input[idx] == '\t' -> idx + 1
                    else -> null
                }

                if (indentIdx != null) {
                    // Indented line: commit any pending blank lines and this line.
                    val restResult = pRestOfLine<U>()(
                        ParserInput(input.input, indentIdx, input.userContext)
                    ) as Success
                    val line = restResult.value
                    val afterRest = restResult.nextIndex

                    when (val end = lineEnding(ParserInput(input.input, afterRest, input.userContext))) {
                        is Failure -> break // unreachable: pEof covers the EOF case
                        is Success -> {
                            repeat(pendingBlanks) { lines.add("") }
                            pendingBlanks = 0
                            lines.add(line)
                            idx = end.nextIndex
                            committed = idx
                        }
                    }
                } else {
                    // Try a blank line. It is tentatively accepted but only committed
                    // to the block if a subsequent indented line follows.
                    when (val r = blank(pos)) {
                        is Success -> {
                            pendingBlanks++
                            idx = r.nextIndex
                        }
                        is Failure -> break
                    }
                }
            }

            if (lines.isEmpty()) return@Parser Failure("indented code block", input.index, input)

            val literal = lines.joinToString("\n") + "\n"
            Success(Block.IndentedCodeBlock(literal), committed, input)
        },
        "indented code block",
    )

/**
 * Parses a CommonMark fenced code block.
 *
 * A fenced code block consists of:
 * - An **opening fence**: 0–3 spaces of indentation (N), then ≥3 backtick (`` ` ``)
 *   or tilde (`~`) characters, then an optional info string, then a line ending.
 * - Zero or more **content lines**, each with up to N leading spaces stripped.
 * - A **closing fence**: 0–3 spaces, then ≥ the opening fence's length of the same
 *   character, then only optional spaces/tabs, then a line ending or EOF. If no
 *   closing fence appears before EOF the block extends to the end of the document.
 *
 * Info string rules:
 * - Everything after the fence characters (trimmed of leading/trailing whitespace).
 * - For backtick fences: the info string may not contain a backtick character.
 * - For tilde fences: no such restriction.
 * - `null` is returned when the trimmed info string is empty.
 *
 * @return a [Parser] that succeeds with [Block.FencedCodeBlock] or fails.
 */
fun <U : Any> pFencedCodeBlock(): Parser<Char, Block.FencedCodeBlock, U> =
    pLabel(
        Parser { input ->
            val lineEnding = pOr(
                pMap(pLineEnding<U>()) { Unit },
                pMap(pEof<Char, U>()) { Unit },
            )

            // 1. Opening fence indentation: 0–3 spaces.
            var idx = input.index
            var openIndent = 0
            while (openIndent < 3 && idx < input.input.size && input.input[idx] == ' ') {
                openIndent++
                idx++
            }

            // 2. Fence character and minimum length (≥3 of the same char: ` or ~).
            val fenceChar = input.input.getOrNull(idx)
            if (fenceChar != '`' && fenceChar != '~')
                return@Parser Failure("fenced code block", idx, input)

            var fenceLen = 0
            while (idx < input.input.size && input.input[idx] == fenceChar) {
                fenceLen++
                idx++
            }
            if (fenceLen < 3) return@Parser Failure("fenced code block", idx, input)

            // 3. Info string: rest of the opening fence line (trimmed).
            //    Backtick fences may not contain a backtick in the info string.
            val infoResult = pRestOfLine<U>()(ParserInput(input.input, idx, input.userContext)) as Success
            val infoRaw = infoResult.value
            idx = infoResult.nextIndex
            if (fenceChar == '`' && '`' in infoRaw)
                return@Parser Failure("fenced code block", idx, input)
            val info: String? = infoRaw.trim().takeIf { it.isNotEmpty() }

            // 4. Consume the opening fence's line ending (or confirm EOF).
            when (val end = lineEnding(ParserInput(input.input, idx, input.userContext))) {
                is Failure -> return@Parser Failure("fenced code block", idx, input)
                is Success -> idx = end.nextIndex
            }

            // 5. Collect content lines until a matching closing fence or EOF.
            val contentLines = mutableListOf<String>()

            while (idx < input.input.size) {
                // Check whether the current line is a valid closing fence.
                val closeEnd = tryFencedClose(input.input, idx, fenceChar, fenceLen)
                if (closeEnd != null) {
                    idx = closeEnd
                    break
                }

                // Content line: strip up to openIndent leading spaces then collect.
                var lineIdx = idx
                var stripped = 0
                while (stripped < openIndent && lineIdx < input.input.size && input.input[lineIdx] == ' ') {
                    stripped++
                    lineIdx++
                }
                val restResult = pRestOfLine<U>()(ParserInput(input.input, lineIdx, input.userContext)) as Success
                val line = restResult.value
                lineIdx = restResult.nextIndex

                when (val end = lineEnding(ParserInput(input.input, lineIdx, input.userContext))) {
                    is Failure -> { contentLines.add(line); idx = lineIdx; break }
                    is Success -> { contentLines.add(line); idx = end.nextIndex }
                }
            }

            val literal =
                if (contentLines.isEmpty()) "" else contentLines.joinToString("\n") + "\n"
            Success(Block.FencedCodeBlock(info, literal), idx, input)
        },
        "fenced code block",
    )

/**
 * Attempts to parse a fenced code block closing fence starting at [startIdx].
 *
 * A valid closing fence is:
 * - 0–3 space characters (tabs are not permitted)
 * - At least [minLen] consecutive [fenceChar] characters
 * - Only spaces or tabs thereafter
 * - A line ending (`\n`, `\r\n`, or `\r`) or end of input
 *
 * @return the index immediately after the closing fence (including its line ending),
 *   or `null` if the line at [startIdx] is not a closing fence.
 */
private fun tryFencedClose(
    chars: List<Char>,
    startIdx: Int,
    fenceChar: Char,
    minLen: Int,
): Int? {
    var idx = startIdx

    // 0–3 leading spaces (tabs not allowed for fence indentation).
    var spaces = 0
    while (spaces < 3 && idx < chars.size && chars[idx] == ' ') {
        spaces++
        idx++
    }

    // Fence character run (must be at least minLen of the same char).
    var fenceCount = 0
    while (idx < chars.size && chars[idx] == fenceChar) {
        fenceCount++
        idx++
    }
    if (fenceCount < minLen) return null

    // Only spaces or tabs may follow the fence characters.
    while (idx < chars.size && (chars[idx] == ' ' || chars[idx] == '\t')) {
        idx++
    }

    // Must end with a line ending or EOF.
    return when {
        idx >= chars.size -> idx                                             // EOF
        chars[idx] == '\n' -> idx + 1                                       // LF
        chars[idx] == '\r' && idx + 1 < chars.size && chars[idx + 1] == '\n' -> idx + 2 // CRLF
        chars[idx] == '\r' -> idx + 1                                       // CR alone
        else -> null                                                          // other content after fence
    }
}
