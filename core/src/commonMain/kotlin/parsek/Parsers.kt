package parsek

/**
 * Returns a [Parser] that consumes exactly one token if [predicate] returns `true`
 * for it, and fails otherwise.
 *
 * `pSatisfy` is the fundamental building block for token-level parsers. All
 * character and token matchers are typically implemented in terms of it.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | Input is exhausted | [Failure] — "Unexpected end of input" |
 * | `predicate(current)` is `true` | [Success] with the matched token; index advances by 1 |
 * | `predicate(current)` is `false` | [Failure] — "Unexpected \<token\> at index \<n\>" |
 *
 * ### Type parameters
 * - [I] — the token type.
 * - [U] — the user context type threaded through unchanged. Use [Unit] when no
 *   context is needed.
 *
 * ### Example
 * ```kotlin
 * val isDigit: Parser<Char, Char, Unit> = pSatisfy { it.isDigit() }
 *
 * val input = ParserInput.of("42".toList(), Unit)
 * val result = isDigit(input)   // Success('4', nextIndex=1, ...)
 * ```
 *
 * @param predicate a function that tests each token; should be side-effect-free.
 * @return a [Parser] that succeeds with the matching token or fails with a
 *   diagnostic message.
 *
 * @see pChar
 */
fun <I : Any, U : Any> pSatisfy(predicate: (I) -> Boolean): Parser<I, I, U> =
    Parser { input ->
        when {
            input.isAtEnd -> Failure("Unexpected end of input", input.index, input)
            predicate(input.current()) -> Success(input.current(), input.index + 1, input)
            else -> Failure("Unexpected ${input.current()} at index ${input.index}", input.index, input)
        }
    }
