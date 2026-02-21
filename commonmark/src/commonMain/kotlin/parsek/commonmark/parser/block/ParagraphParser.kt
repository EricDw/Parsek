package parsek.commonmark.parser.block

import parsek.Failure
import parsek.Parser
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.commonmark.ast.Inline
import parsek.pLabel

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

private fun isBlankLine(content: String): Boolean =
    content.all { it == ' ' || it == '\t' }

/** Strips up to 3 leading space characters from [s]. */
private fun stripUpTo3Spaces(s: String): String {
    var i = 0
    while (i < s.length && i < 3 && s[i] == ' ') i++
    return s.substring(i)
}

private fun advancePastLineEnding(chars: List<Char>, idx: Int): Int = when {
    idx >= chars.size -> idx
    chars[idx] == '\r' && idx + 1 < chars.size && chars[idx + 1] == '\n' -> idx + 2
    chars[idx] == '\r' || chars[idx] == '\n' -> idx + 1
    else -> idx
}

/**
 * Reads one line of content (without the line ending) starting at [startIdx].
 *
 * Returns `(content, nextIdx)` where `nextIdx` is the position immediately
 * after the consumed line ending, or at EOF if there is none.
 */
private fun readLineContent(chars: List<Char>, startIdx: Int): Pair<String, Int> {
    var i = startIdx
    while (i < chars.size && chars[i] != '\n' && chars[i] != '\r') i++
    val content = chars.subList(startIdx, i).joinToString("")
    return Pair(content, advancePastLineEnding(chars, i))
}

/**
 * Returns the setext heading level for the line beginning at [startIdx]
 * (1 for an `=` underline, 2 for a `-` underline), or `null` if the line is
 * not a valid setext heading underline.
 *
 * A setext heading underline is:
 * - 0–3 leading space characters
 * - One or more `=` or `-` characters (all the same)
 * - Optional trailing spaces/tabs
 * - Ends at a line ending or EOF
 */
private fun setextUnderlineLevel(chars: List<Char>, startIdx: Int): Int? {
    var i = startIdx
    var spaces = 0
    while (spaces < 3 && i < chars.size && chars[i] == ' ') { spaces++; i++ }
    val c = chars.getOrNull(i) ?: return null
    val level = when (c) { '=' -> 1; '-' -> 2; else -> return null }
    val markStart = i
    while (i < chars.size && chars[i] == c) i++
    if (i == markStart) return null
    while (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) i++
    return if (i >= chars.size || chars[i] == '\n' || chars[i] == '\r') level else null
}

// ---------------------------------------------------------------------------
// pSetextHeading
// ---------------------------------------------------------------------------

/**
 * Parses a CommonMark setext heading (§4.3).
 *
 * A setext heading consists of one or more non-blank content lines followed
 * immediately by a setext heading underline on a line by itself:
 * - A run of `=` characters → level-1 heading
 * - A run of `-` characters → level-2 heading
 *
 * The underline allows 0–3 leading spaces and optional trailing spaces/tabs.
 * Content lines may also have 0–3 leading spaces, which are stripped.
 *
 * The heading's raw content is formed by joining the (leading-space-stripped)
 * content lines with `\n` and trimming trailing whitespace. Inline content is
 * produced as a single [Inline.Text] stub until the inline pass is implemented.
 *
 * The rule that a setext heading cannot interrupt a paragraph is enforced by
 * the document parser, not by this parser.
 *
 * @return a [Parser] that succeeds with [Block.Heading] (level 1 or 2) or fails.
 */
fun <U : Any> pSetextHeading(): Parser<Char, Block.Heading, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            var idx = input.index
            val contentLines = mutableListOf<String>()

            while (true) {
                if (idx >= chars.size) break  // EOF with no underline → fail

                // If we have accumulated content, check whether the current line
                // is a setext heading underline.
                if (contentLines.isNotEmpty()) {
                    val level = setextUnderlineLevel(chars, idx)
                    if (level != null) {
                        // Consume the underline line.
                        while (idx < chars.size && chars[idx] != '\n' && chars[idx] != '\r') idx++
                        idx = advancePastLineEnding(chars, idx)
                        val content = contentLines.joinToString("\n").trimEnd()
                        val inlines: List<Inline> =
                            if (content.isEmpty()) emptyList() else listOf(Inline.Text(content))
                        return@Parser Success(Block.Heading(level, inlines), idx, input)
                    }
                }

                // Read the next potential content line.
                val (lineContent, nextIdx) = readLineContent(chars, idx)
                if (isBlankLine(lineContent)) break  // blank line terminates search → fail
                contentLines.add(stripUpTo3Spaces(lineContent))
                idx = nextIdx
            }

            Failure("setext heading", input.index, input)
        },
        "setext heading",
    )

// ---------------------------------------------------------------------------
// pParagraph
// ---------------------------------------------------------------------------

/**
 * Parses a CommonMark paragraph (§4.9).
 *
 * A paragraph is one or more non-blank lines. The parser accumulates lines
 * until a blank line or end of input. The terminating blank line is **not**
 * consumed.
 *
 * Content lines may have 0–3 leading spaces, which are stripped. The raw
 * content is formed by joining the stripped lines with `\n` and trimming
 * trailing whitespace. Inline content is produced as a single [Inline.Text]
 * stub until the inline pass is implemented in Phase 5.
 *
 * The rule that certain block types can interrupt a paragraph is enforced by
 * the document parser, not by this parser.
 *
 * @return a [Parser] that succeeds with [Block.Paragraph] or fails.
 */
fun <U : Any> pParagraph(): Parser<Char, Block.Paragraph, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            var idx = input.index
            val contentLines = mutableListOf<String>()

            while (idx < chars.size) {
                val (lineContent, nextIdx) = readLineContent(chars, idx)
                if (isBlankLine(lineContent)) break
                contentLines.add(stripUpTo3Spaces(lineContent))
                idx = nextIdx
            }

            if (contentLines.isEmpty())
                return@Parser Failure("paragraph", input.index, input)

            val content = contentLines.joinToString("\n").trimEnd()
            val inlines: List<Inline> =
                if (content.isEmpty()) emptyList() else listOf(Inline.Text(content))
            Success(Block.Paragraph(inlines), idx, input)
        },
        "paragraph",
    )
