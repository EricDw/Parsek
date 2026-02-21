package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.Parser
import parsek.Success
import parsek.commonmark.ast.Inline
import parsek.pLabel

/**
 * Parses a CommonMark line break (§6.7).
 *
 * Two forms are recognised:
 *
 * - **Hard break**: two or more spaces (or a backslash) immediately before a
 *   line ending. The spaces/backslash and the line ending are all consumed.
 *   Returns [Inline.HardBreak].
 *
 * - **Soft break**: a plain line ending, with an optional single leading
 *   space or tab stripped. Returns [Inline.SoftBreak].
 *
 * The hard-break form is tried first; if the current position does not start
 * with 2+ spaces or `\` followed by a line ending, the soft-break form is
 * attempted.
 *
 * @return a [Parser] that succeeds with [Inline.HardBreak] or [Inline.SoftBreak],
 *   or fails if there is no line ending at the current position.
 */
fun <U : Any> pLineBreak(): Parser<Char, Inline, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            val start = input.index

            if (start >= chars.size)
                return@Parser Failure("line break", start, input)

            // --- Hard break: 2+ spaces then line ending ---
            if (chars[start] == ' ') {
                var i = start
                while (i < chars.size && chars[i] == ' ') i++
                val spaceCount = i - start
                if (spaceCount >= 2 && i < chars.size && isLineEnding(chars, i)) {
                    val end = advancePastLineEnding(chars, i)
                    return@Parser Success(Inline.HardBreak, end, input)
                }
            }

            // --- Hard break: backslash then line ending ---
            if (chars[start] == '\\') {
                val afterBackslash = start + 1
                if (afterBackslash < chars.size && isLineEnding(chars, afterBackslash)) {
                    val end = advancePastLineEnding(chars, afterBackslash)
                    return@Parser Success(Inline.HardBreak, end, input)
                }
            }

            // --- Soft break: optional single space/tab then line ending ---
            var i = start
            if (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) i++
            if (i < chars.size && isLineEnding(chars, i)) {
                val end = advancePastLineEnding(chars, i)
                return@Parser Success(Inline.SoftBreak, end, input)
            }

            Failure("line break", start, input)
        },
        "line break",
    )

private fun isLineEnding(chars: List<Char>, i: Int): Boolean =
    chars[i] == '\n' || chars[i] == '\r'

private fun advancePastLineEnding(chars: List<Char>, i: Int): Int = when {
    i >= chars.size -> i
    chars[i] == '\r' && i + 1 < chars.size && chars[i + 1] == '\n' -> i + 2
    else -> i + 1
}
