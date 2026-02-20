package parsek.text

import parsek.Failure
import parsek.Parser
import parsek.Success
import parsek.pAnd
import parsek.pLabel
import parsek.pMany
import parsek.pMany1
import parsek.pMap
import parsek.pNot
import parsek.pOptional
import parsek.pOr
import parsek.pSequence
import parsek.pSatisfy

/**
 * Returns a [Parser] that consumes exactly one [Char] equal to [character], and fails
 * if the next character is different or the input is exhausted.
 *
 * This is a thin wrapper around [pSatisfy] with a strict equality predicate.
 *
 * ### Type parameters
 * - [U] — the user context type threaded through unchanged. Use [Unit] when no
 *   context is needed.
 *
 * ### Example
 * ```kotlin
 * val openBrace: Parser<Char, Char, Unit> = pChar('{')
 *
 * val input = ParserInput.of("{...}".toList(), Unit)
 * when (val result = openBrace(input)) {
 *     is Success -> println("Matched: ${result.value}")  // '{'
 *     is Failure -> println(result.message)
 * }
 * ```
 *
 * @param character the exact character to match.
 * @return a [Parser] that succeeds with [character] on a match, or fails with a
 *   diagnostic message from [pSatisfy].
 *
 * @see pSatisfy
 */
fun <U : Any> pChar(
    character: Char,
    ignoreCase: Boolean = false
): Parser<Char, Char, U> = pSatisfy { it.equals(character, ignoreCase) }

/**
 * Returns a [Parser] that consumes exactly the sequence of characters in [string],
 * and fails if the input does not match or is exhausted before the full string is consumed.
 *
 * ### Type parameters
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val hello: Parser<Char, String, Unit> = pString("Hello")
 *
 * val input = ParserInput.of("Hello, World!".toList(), Unit)
 * when (val result = hello(input)) {
 *     is Success -> println("Matched: ${result.value}")  // "Hello"
 *     is Failure -> println(result.message)
 * }
 * ```
 *
 * An empty [string] always succeeds with `""` without consuming any input.
 *
 * When [ignoreCase] is `true` the comparison is case-insensitive and the returned
 * value reflects the actual characters from the input, not the pattern.
 *
 * @param string the exact sequence of characters to match.
 * @param ignoreCase whether the comparison is case-insensitive. Defaults to `false`.
 * @return a [Parser] that succeeds with the matched substring, or fails with a
 *   diagnostic message.
 *
 * @see pChar
 */
fun <U : Any> pString(
    string: String,
    ignoreCase: Boolean = false,
): Parser<Char, String, U> =
    pMap(pSequence(string.map { char -> pChar(char, ignoreCase) })) { chars ->
        chars.joinToString("")
    }

/**
 * Returns a [Parser] that consumes an optional sign (`+` or `-`) followed by
 * one or more decimal digits, and succeeds with the resulting [Int] value.
 *
 * ### Behaviour
 * | Input | Result |
 * |---|---|
 * | No digits present | [Failure] — "integer" |
 * | Sign with no following digits | [Failure] — "integer" |
 * | Valid decimal integer | [Success] with the [Int] value |
 * | Value outside [Int] range | [Failure] — "Integer out of range: \<string\>" |
 *
 * ### Type parameters
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val parser: Parser<Char, Int, Unit> = pInt()
 *
 * pInt<Unit>()(ParserInput.of("-42".toList(), Unit))   // Success(-42, nextIndex=3, ...)
 * pInt<Unit>()(ParserInput.of("+7".toList(), Unit))    // Success(7,   nextIndex=2, ...)
 * pInt<Unit>()(ParserInput.of("abc".toList(), Unit))   // Failure("integer", ...)
 * ```
 *
 * @return a [Parser] that succeeds with the parsed [Int], or fails with a
 *   diagnostic message.
 *
 * @see pChar
 * @see pString
 */
/**
 * Returns a [Parser] that consumes one decimal digit character (`0`–`9`).
 *
 * @return a [Parser] that succeeds with the matched digit character.
 *
 * @see pHexDigit
 * @see pLetter
 */
fun <U : Any> pDigit(): Parser<Char, Char, U> =
    pLabel(pSatisfy { it.isDigit() }, "digit")

/**
 * Returns a [Parser] that consumes one hexadecimal digit character
 * (`0`–`9`, `a`–`f`, `A`–`F`).
 *
 * @return a [Parser] that succeeds with the matched hex digit character.
 *
 * @see pDigit
 */
fun <U : Any> pHexDigit(): Parser<Char, Char, U> =
    pLabel(pSatisfy { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }, "hex digit")

/**
 * Returns a [Parser] that consumes one Unicode letter character (as determined
 * by [Char.isLetter]).
 *
 * @return a [Parser] that succeeds with the matched letter character.
 *
 * @see pDigit
 */
fun <U : Any> pLetter(): Parser<Char, Char, U> =
    pLabel(pSatisfy { it.isLetter() }, "letter")

/**
 * Returns a [Parser] that consumes exactly one space character (`' '`).
 *
 * @return a [Parser] that succeeds with `' '`.
 *
 * @see pTab
 * @see pSpaceOrTab
 */
fun <U : Any> pSpace(): Parser<Char, Char, U> =
    pLabel(pChar(' '), "space")

/**
 * Returns a [Parser] that consumes exactly one tab character (`'\t'`).
 *
 * @return a [Parser] that succeeds with `'\t'`.
 *
 * @see pSpace
 * @see pSpaceOrTab
 */
fun <U : Any> pTab(): Parser<Char, Char, U> =
    pLabel(pChar('\t'), "tab")

/**
 * Returns a [Parser] that consumes exactly one space (`' '`) or tab (`'\t'`)
 * character, whichever appears next in the input.
 *
 * @return a [Parser] that succeeds with the matched character.
 *
 * @see pSpace
 * @see pTab
 */
fun <U : Any> pSpaceOrTab(): Parser<Char, Char, U> =
    pLabel(pOr(pSpace(), pTab()), "space or tab")

/**
 * Returns a [Parser] that consumes a line ending (`\n`, `\r\n`, or `\r` not
 * followed by `\n`) and always succeeds with the normalised newline character `'\n'`.
 *
 * The three forms are tried in this order:
 * 1. `\r\n` — Windows CRLF
 * 2. `\r` not followed by `\n` — classic Mac CR
 * 3. `\n` — Unix LF
 *
 * @return a [Parser] that succeeds with `'\n'` on any line ending form.
 *
 * @see pBlankLine
 * @see pRestOfLine
 */
fun <U : Any> pLineEnding(): Parser<Char, Char, U> {
    val cr = pChar<U>('\r')
    val lf = pChar<U>('\n')
    val crlf    = pMap(pAnd(cr, lf)) { '\n' }
    val crAlone = pMap(pAnd(cr, pNot(lf))) { '\n' }
    return pLabel(pOr(crlf, pOr(crAlone, lf)), "line ending")
}

/**
 * Returns a [Parser] that consumes zero or more space/tab characters followed
 * by a line ending, succeeding with [Unit].
 *
 * A blank line may contain any number of spaces and tabs before the line ending;
 * the whitespace and the ending are both consumed.
 *
 * @return a [Parser] that always succeeds with [Unit] on a blank line, or fails
 *   if no line ending follows the optional whitespace.
 *
 * @see pLineEnding
 * @see pSpaceOrTab
 */
fun <U : Any> pBlankLine(): Parser<Char, Unit, U> =
    pLabel(
        pMap(pAnd(pMany(pSpaceOrTab<U>()), pLineEnding())) { Unit },
        "blank line",
    )

/**
 * Returns a [Parser] that consumes all characters up to (but not including)
 * the next line ending or end of input, succeeding with the matched [String].
 *
 * The line ending itself is **not** consumed. An empty string is returned when
 * the current position is already at a line ending or end of input.
 *
 * @return a [Parser] that always succeeds with the rest of the current line.
 *
 * @see pLineEnding
 */
fun <U : Any> pRestOfLine(): Parser<Char, String, U> =
    pMap(pMany(pSatisfy { it != '\n' && it != '\r' })) { chars ->
        chars.joinToString("")
    }

/**
 * Returns a [Parser] that consumes one ASCII punctuation character.
 *
 * ASCII punctuation is defined by the CommonMark spec as any character in:
 * ``!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~``
 *
 * @return a [Parser] that succeeds with the matched punctuation character.
 *
 * @see pUnicodePunctuation
 */
fun <U : Any> pAsciiPunctuation(): Parser<Char, Char, U> =
    pLabel(
        pSatisfy { it in "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~" },
        "ASCII punctuation",
    )

/**
 * Returns a [Parser] that consumes one Unicode punctuation character.
 *
 * A character is considered Unicode punctuation if it belongs to the Unicode
 * general categories P (punctuation) or S (symbol), or is an ASCII punctuation
 * character as defined by the CommonMark spec.
 *
 * @return a [Parser] that succeeds with the matched punctuation character.
 *
 * @see pAsciiPunctuation
 */
fun <U : Any> pUnicodePunctuation(): Parser<Char, Char, U> =
    pLabel(
        pSatisfy { ch ->
            ch in "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~" ||
                ch.category.let { cat ->
                    cat == CharCategory.CONNECTOR_PUNCTUATION ||
                        cat == CharCategory.DASH_PUNCTUATION ||
                        cat == CharCategory.START_PUNCTUATION ||
                        cat == CharCategory.END_PUNCTUATION ||
                        cat == CharCategory.INITIAL_QUOTE_PUNCTUATION ||
                        cat == CharCategory.FINAL_QUOTE_PUNCTUATION ||
                        cat == CharCategory.OTHER_PUNCTUATION ||
                        cat == CharCategory.MATH_SYMBOL ||
                        cat == CharCategory.CURRENCY_SYMBOL ||
                        cat == CharCategory.MODIFIER_SYMBOL ||
                        cat == CharCategory.OTHER_SYMBOL
                }
        },
        "Unicode punctuation",
    )

/**
 * Returns a [Parser] that consumes one Unicode whitespace character.
 *
 * Unicode whitespace is defined by the CommonMark spec as any character in
 * Unicode category Zs (space separator), plus tab (`\t`), line feed (`\n`),
 * form feed (`\u000C`), and carriage return (`\r`).
 *
 * @return a [Parser] that succeeds with the matched whitespace character.
 *
 * @see pSpaceOrTab
 * @see pLineEnding
 */
fun <U : Any> pUnicodeWhitespace(): Parser<Char, Char, U> =
    pLabel(
        pSatisfy { ch ->
            ch == '\t' || ch == '\n' || ch == '\u000C' || ch == '\r' ||
                ch.category == CharCategory.SPACE_SEPARATOR
        },
        "Unicode whitespace",
    )

fun <U : Any> pInt(): Parser<Char, Int, U> {
    val sign = pOptional(pOr(pChar<U>('+'), pChar('-')))
    val digits = pMany1(pSatisfy<Char, U> { it.isDigit() })
    val raw = pLabel(pAnd(sign, digits), "integer")
    return Parser { input ->
        when (val r = raw(input)) {
            is Failure -> r
            is Success -> {
                val str = "${r.value.first ?: ""}${r.value.second.joinToString("")}"
                val n = str.toIntOrNull()
                    ?: return@Parser Failure("Integer out of range: $str", input.index, input)
                Success(n, r.nextIndex, input)
            }
        }
    }
}
