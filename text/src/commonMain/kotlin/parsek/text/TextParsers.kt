package parsek.text

import parsek.Parser
import parsek.pSatisfy

/**
 * Returns a [Parser] that consumes exactly one [Char] equal to [c], and fails
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
 * @param c the exact character to match.
 * @return a [Parser] that succeeds with [c] on a match, or fails with a
 *   diagnostic message from [pSatisfy].
 *
 * @see pSatisfy
 */
fun <U : Any> pChar(c: Char): Parser<Char, Char, U> = pSatisfy { it == c }
