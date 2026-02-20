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
