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
 * @see pAnd
 */
fun <I : Any, U : Any> pSatisfy(predicate: (I) -> Boolean): Parser<I, I, U> =
    Parser { input ->
        when {
            input.isAtEnd -> Failure("Unexpected end of input", input.index, input)
            predicate(input.current()) -> Success(input.current(), input.index + 1, input)
            else -> Failure("Unexpected ${input.current()} at index ${input.index}", input.index, input)
        }
    }

/**
 * Returns a [Parser] that runs [first] and then [second] in sequence, combining
 * their outputs into a [Pair].
 *
 * If either parser fails the combined parser fails immediately, without consuming
 * any input beyond what the failing parser had already consumed.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | [first] fails | [Failure] propagated from [first] |
 * | [first] succeeds, [second] fails | [Failure] propagated from [second] |
 * | Both succeed | [Success] with `Pair(first.value, second.value)`; index advanced past both |
 *
 * ### Type parameters
 * - [I] — the shared token type consumed by both parsers.
 * - [O1] — the output type of [first].
 * - [O2] — the output type of [second].
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val digit = pSatisfy<Char, Unit> { it.isDigit() }
 * val letter = pSatisfy<Char, Unit> { it.isLetter() }
 * val digitThenLetter = pAnd(digit, letter)
 *
 * val input = ParserInput.of("1a".toList(), Unit)
 * val result = digitThenLetter(input)  // Success(Pair('1', 'a'), nextIndex=2, ...)
 * ```
 *
 * @param first the parser to run first.
 * @param second the parser to run after [first] succeeds.
 * @return a [Parser] that produces a [Pair] of both outputs on success.
 *
 * @see pSatisfy
 */
fun <I : Any, O1 : Any, O2 : Any, U : Any> pAnd(
    first: Parser<I, O1, U>,
    second: Parser<I, O2, U>,
): Parser<I, Pair<O1, O2>, U> =
    Parser { input ->
        when (val r1 = first(input)) {
            is Failure -> r1
            is Success -> {
                val next = ParserInput(input.input, r1.nextIndex, input.userContext)
                when (val r2 = second(next)) {
                    is Failure -> r2
                    is Success -> Success(r1.value to r2.value, r2.nextIndex, input)
                }
            }
        }
    }
