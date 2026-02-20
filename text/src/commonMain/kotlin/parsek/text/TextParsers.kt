package parsek.text

import parsek.Failure
import parsek.Parser
import parsek.Success
import parsek.pAnd
import parsek.pLabel
import parsek.pMany1
import parsek.pMap
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
