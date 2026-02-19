package parsek.text

import parsek.Parser
import parsek.pMap
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
