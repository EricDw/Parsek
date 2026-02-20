package parsek.commonmark.parser.block

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.commonmark.ast.Inline
import parsek.pEof
import parsek.pLabel
import parsek.pMap
import parsek.pOr
import parsek.text.pLineEnding
import parsek.text.pRestOfLine
import parsek.text.pUpTo3Spaces

/**
 * Parses a CommonMark ATX heading.
 *
 * An ATX heading consists of:
 * - 0–3 optional leading spaces
 * - 1–6 `#` characters whose count determines the heading level
 * - A space or tab (or end of line / EOF for an empty heading)
 * - Optional inline content with leading/trailing whitespace stripped
 * - An optional closing `#` run that must be preceded by a space or tab
 * - A line ending or end of input
 *
 * The inline content is currently produced as a single [Inline.Text] stub; it
 * will be replaced by a real inline pass once the inline parsers are implemented.
 *
 * Examples of valid ATX headings:
 * ```
 * # Heading 1
 * ## Heading 2
 * ### foo ###
 * ###### level six
 * #   (empty heading with spaces)
 * ```
 *
 * @return a [Parser] that succeeds with [Block.Heading] or fails otherwise.
 */
fun <U : Any> pAtxHeading(): Parser<Char, Block.Heading, U> =
    pLabel(
        Parser { input ->
            // 1. Consume 0–3 leading spaces.
            val indentResult = pUpTo3Spaces<U>()(input) as Success
            val hashStart = indentResult.nextIndex
            var idx = hashStart

            // 2. Count 1–6 consecutive '#' characters; fail if 0 or more than 6.
            var level = 0
            while (idx < input.input.size && input.input[idx] == '#') {
                level++
                if (level > 6) return@Parser Failure("ATX heading", hashStart, input)
                idx++
            }
            if (level == 0) return@Parser Failure("ATX heading", idx, input)

            // 3. The '#' run must be followed by space/tab, a line ending, or EOF.
            val nextChar = input.input.getOrNull(idx)
            if (nextChar != null && nextChar != ' ' && nextChar != '\t' &&
                nextChar != '\n' && nextChar != '\r'
            ) {
                return@Parser Failure("ATX heading", idx, input)
            }

            // 4. Collect the rest of the line (up to but not including the line ending).
            val restResult = pRestOfLine<U>()(ParserInput(input.input, idx, input.userContext)) as Success
            val raw = restResult.value
            idx = restResult.nextIndex

            // 5. Consume the line ending or confirm EOF.
            val lineEnd = pOr(
                pMap(pLineEnding<U>()) { Unit },
                pMap(pEof<Char, U>()) { Unit },
            )(ParserInput(input.input, idx, input.userContext))
            when (lineEnd) {
                is Failure -> return@Parser Failure("ATX heading", idx, input)
                is Success -> idx = lineEnd.nextIndex
            }

            // 6. Normalise: strip leading/trailing whitespace and the optional closing '#' run.
            val content = normalizeAtxContent(raw)

            // 7. Stub inline pass — produces a single Text node until inline parsers land.
            val inlines: List<Inline> =
                if (content.isEmpty()) emptyList() else listOf(Inline.Text(content))

            Success(Block.Heading(level, inlines), idx, input)
        },
        "ATX heading",
    )

/**
 * Normalises raw ATX heading content:
 * 1. Strips leading and trailing spaces/tabs.
 * 2. Strips the optional closing `#` run if it is preceded by a space or tab.
 * 3. Strips trailing spaces/tabs that were left before the removed `#` run.
 *
 * A `#` run that starts at the beginning of the (leading-stripped) content is
 * **not** treated as a closing sequence because there is no preceding space.
 */
private fun normalizeAtxContent(raw: String): String {
    var s = raw.trimStart(' ', '\t')
    s = s.trimEnd(' ', '\t')
    if (s.endsWith('#')) {
        // Find the start index of the trailing '#' run.
        val hashRunStart = s.indexOfLast { it != '#' } + 1
        // Only strip if preceded by a space or tab (hashRunStart > 0 ensures it is not
        // at the beginning of the content, which would mean no preceding space exists).
        if (hashRunStart > 0 && (s[hashRunStart - 1] == ' ' || s[hashRunStart - 1] == '\t')) {
            s = s.substring(0, hashRunStart).trimEnd(' ', '\t')
        }
    }
    return s
}
