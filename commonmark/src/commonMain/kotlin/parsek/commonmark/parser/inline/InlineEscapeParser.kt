package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.Parser
import parsek.Success
import parsek.commonmark.ast.Inline
import parsek.pAnd
import parsek.pLabel
import parsek.pMap
import parsek.text.pAsciiPunctuation
import parsek.text.pChar

/**
 * Parses a CommonMark backslash escape (§6.1).
 *
 * A backslash followed by any ASCII punctuation character is a backslash escape.
 * Returns [Inline.Text] containing just the escaped character — the backslash
 * is consumed but not included in the output.
 *
 * Only ASCII punctuation may be escaped; a backslash before any other character
 * (letter, digit, space, …) is not a backslash escape and causes this parser to fail.
 *
 * @return a [Parser] that succeeds with [Inline.Text], or fails.
 */
fun <U : Any> pBackslashEscape(): Parser<Char, Inline, U> =
    pLabel(
        pMap(pAnd(pChar<U>('\\'), pAsciiPunctuation())) { (_, ch) ->
            Inline.Text(ch.toString())
        },
        "backslash escape",
    )

/**
 * Parses a CommonMark HTML entity reference (§2.5).
 *
 * Three syntactic forms are recognised (tried in order):
 * - **Hex numeric**: `&#x` or `&#X` followed by 1–6 hexadecimal digits and `;`.
 * - **Decimal numeric**: `&#` followed by 1–7 decimal digits and `;`.
 * - **Named**: `&` followed by 1–100 ASCII alphanumeric characters and `;`.
 *
 * The [Inline.HtmlEntity.literal] contains the full original string
 * (e.g. `&amp;`, `&#42;`, `&#x2A;`). Resolution to a Unicode code point is
 * left to the renderer.
 *
 * @return a [Parser] that succeeds with [Inline.HtmlEntity], or fails.
 */
fun <U : Any> pHtmlEntity(): Parser<Char, Inline, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            val start = input.index

            if (start >= chars.size || chars[start] != '&')
                return@Parser Failure("HTML entity", start, input)

            // --- Hex form: &#x[hex]{1,6}; or &#X[hex]{1,6}; ---
            if (start + 2 < chars.size && chars[start + 1] == '#' &&
                (chars[start + 2] == 'x' || chars[start + 2] == 'X')
            ) {
                var i = start + 3
                val hexStart = i
                while (i < chars.size &&
                    (chars[i].isDigit() || chars[i] in 'a'..'f' || chars[i] in 'A'..'F')
                ) i++
                val hexLen = i - hexStart
                if (hexLen in 1..6 && i < chars.size && chars[i] == ';') {
                    val literal = chars.subList(start, i + 1).joinToString("")
                    return@Parser Success(Inline.HtmlEntity(literal), i + 1, input)
                }
            }

            // --- Decimal form: &#[digits]{1,7}; ---
            if (start + 1 < chars.size && chars[start + 1] == '#') {
                var i = start + 2
                if (i < chars.size && chars[i] != 'x' && chars[i] != 'X') {
                    val digStart = i
                    while (i < chars.size && chars[i].isDigit()) i++
                    val digLen = i - digStart
                    if (digLen in 1..7 && i < chars.size && chars[i] == ';') {
                        val literal = chars.subList(start, i + 1).joinToString("")
                        return@Parser Success(Inline.HtmlEntity(literal), i + 1, input)
                    }
                }
            }

            // --- Named form: &[alphanumeric]{1,100}; ---
            var i = start + 1
            while (i < chars.size && chars[i].isLetterOrDigit()) i++
            val nameLen = i - (start + 1)
            if (nameLen in 1..100 && i < chars.size && chars[i] == ';') {
                val literal = chars.subList(start, i + 1).joinToString("")
                return@Parser Success(Inline.HtmlEntity(literal), i + 1, input)
            }

            Failure("HTML entity", start, input)
        },
        "HTML entity",
    )
