package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.Parser
import parsek.Success
import parsek.commonmark.ast.Inline
import parsek.pLabel

/**
 * Parses a CommonMark code span (§6.1).
 *
 * A code span opens with a run of N backtick characters and closes with the
 * next run of exactly N backticks. Content may include any characters, including
 * line endings. Backtick runs of a different length than N are treated as
 * literal content.
 *
 * The raw content is normalised before being stored:
 * 1. Each line ending (`\r\n`, `\r`, or `\n`) is converted to a single space.
 * 2. If the resulting string begins **and** ends with a space character but is
 *    not entirely composed of spaces, one leading and one trailing space are
 *    removed.
 *
 * If no matching closing backtick run is found the parser fails.
 *
 * @return a [Parser] that succeeds with [Inline.CodeSpan], or fails.
 */
fun <U : Any> pCodeSpan(): Parser<Char, Inline, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            var i = input.index

            // Count opening backtick run.
            val openStart = i
            while (i < chars.size && chars[i] == '`') i++
            val n = i - openStart
            if (n == 0) return@Parser Failure("code span", input.index, input)

            // Scan for a closing run of exactly n backticks.
            val sb = StringBuilder()
            while (i < chars.size) {
                if (chars[i] == '`') {
                    val tickStart = i
                    while (i < chars.size && chars[i] == '`') i++
                    val tickCount = i - tickStart
                    if (tickCount == n) {
                        // Found the closing run — normalise and return.
                        val content = normaliseCodeSpan(sb.toString())
                        return@Parser Success(Inline.CodeSpan(content), i, input)
                    } else {
                        // Wrong-length backtick run — treat as literal content.
                        repeat(tickCount) { sb.append('`') }
                    }
                } else {
                    sb.append(chars[i])
                    i++
                }
            }

            Failure("code span", input.index, input)
        },
        "code span",
    )

/**
 * Applies the two-step code span normalisation described in §6.1:
 * 1. Line endings (`\r\n`, `\r`, `\n`) → single space.
 * 2. Strip one leading/trailing space when both ends are a space and the
 *    content is not entirely spaces.
 */
private fun normaliseCodeSpan(raw: String): String {
    val spaced = buildString {
        var j = 0
        while (j < raw.length) {
            when {
                raw[j] == '\r' && j + 1 < raw.length && raw[j + 1] == '\n' -> { append(' '); j += 2 }
                raw[j] == '\r' || raw[j] == '\n' -> { append(' '); j++ }
                else -> { append(raw[j]); j++ }
            }
        }
    }
    return if (spaced.length >= 2 &&
        spaced.first() == ' ' &&
        spaced.last() == ' ' &&
        spaced.any { it != ' ' }
    ) spaced.substring(1, spaced.length - 1) else spaced
}
