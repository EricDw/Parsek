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
 * @see pOr
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

/**
 * Returns a [Parser] that tries [first] and, if it fails, tries [second] at the
 * same position.
 *
 * `pOr` implements ordered choice: [first] is always preferred. [second] is only
 * attempted when [first] fails, and always against the original input position,
 * never against input that [first] may have partially consumed.
 *
 * When both parsers fail, the failure with the greater [Failure.index] is returned,
 * as it represents the furthest point reached in the input. If both fail at the same
 * index, the failure from [second] is returned.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | [first] succeeds | [Success] from [first] |
 * | [first] fails, [second] succeeds | [Success] from [second] |
 * | Both fail | [Failure] from whichever reached the furthest index |
 *
 * ### Type parameters
 * - [I] — the shared token type consumed by both parsers.
 * - [O] — the shared output type; both parsers must produce the same type.
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val digit = pSatisfy<Char, Unit> { it.isDigit() }
 * val letter = pSatisfy<Char, Unit> { it.isLetter() }
 * val digitOrLetter = pOr(digit, letter)
 *
 * val input = ParserInput.of("a1".toList(), Unit)
 * val result = digitOrLetter(input)  // Success('a', nextIndex=1, ...) via letter
 * ```
 *
 * @param first the parser to try first.
 * @param second the parser to try if [first] fails.
 * @return a [Parser] that succeeds if either alternative matches.
 *
 * @see pAnd
 * @see pMap
 */
fun <I : Any, O : Any, U : Any> pOr(
    first: Parser<I, O, U>,
    second: Parser<I, O, U>,
): Parser<I, O, U> =
    Parser { input ->
        when (val r1 = first(input)) {
            is Success -> r1
            is Failure -> when (val r2 = second(input)) {
                is Success -> r2
                is Failure -> if (r1.index >= r2.index) r1 else r2
            }
        }
    }

/**
 * Returns a [Parser] that runs [parser] and applies [transform] to the output
 * value if it succeeds.
 *
 * `pMap` is the primary way to convert raw parsed tokens into meaningful domain
 * types without altering parse position or error behaviour.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | [parser] fails | [Failure] propagated unchanged |
 * | [parser] succeeds | [Success] with `transform(value)`; index and input unchanged |
 *
 * ### Type parameters
 * - [I] — the token type consumed by [parser].
 * - [O] — the output type of [parser] and the input type of [transform].
 * - [R] — the output type of [transform] and of the returned parser.
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val digit = pSatisfy<Char, Unit> { it.isDigit() }
 * val digitInt = pMap(digit) { it.digitToInt() }
 *
 * val input = ParserInput.of("3".toList(), Unit)
 * val result = digitInt(input)  // Success(3, nextIndex=1, ...)
 * ```
 *
 * @param parser the parser whose success value is transformed.
 * @param transform a function applied to the parsed value on success; should be
 *   side-effect-free.
 * @return a [Parser] that produces `transform(value)` on success.
 *
 * @see pAnd
 * @see pOr
 * @see pBind
 */
fun <I : Any, O : Any, R : Any, U : Any> pMap(
    parser: Parser<I, O, U>,
    transform: (O) -> R,
): Parser<I, R, U> =
    Parser { input ->
        when (val result = parser(input)) {
            is Failure -> result
            is Success -> Success(transform(result.value), result.nextIndex, result.input)
        }
    }

/**
 * Returns a [Parser] that runs [parser] and, on success, passes the output value
 * to [transform] to obtain and immediately run a second parser.
 *
 * `pBind` is the monadic bind (also known as `flatMap`) for parsers. Unlike [pMap],
 * the second parse step is not fixed in advance — it is chosen dynamically based on
 * what the first parser produced. This makes it possible to write context-sensitive
 * parsers whose structure depends on previously parsed values.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | [parser] fails | [Failure] propagated from [parser] |
 * | [parser] succeeds, derived parser fails | [Failure] propagated from the derived parser |
 * | Both succeed | [Success] from the derived parser |
 *
 * ### Type parameters
 * - [I] — the token type consumed by both parsers.
 * - [O] — the output type of [parser]; passed to [transform].
 * - [R] — the output type of the parser returned by [transform].
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * // Parse a digit, then use its value to decide which character to match next.
 * val digit = pSatisfy<Char, Unit> { it.isDigit() }
 *
 * val parser = pBind(digit) { d ->
 *     // If the digit is '1', expect 'a'; otherwise expect 'b'.
 *     val expected = if (d == '1') 'a' else 'b'
 *     pSatisfy<Char, Unit> { it == expected }
 * }
 *
 * val input = ParserInput.of("1a".toList(), Unit)
 * val result = parser(input)  // Success('a', nextIndex=2, ...)
 * ```
 *
 * @param parser the first parser to run.
 * @param transform a function that receives the first parser's output and returns
 *   the next parser to run; should be side-effect-free.
 * @return a [Parser] that sequences [parser] and the parser produced by [transform].
 *
 * @see pMap
 * @see pAnd
 * @see pRepeat
 */
fun <I : Any, O : Any, R : Any, U : Any> pBind(
    parser: Parser<I, O, U>,
    transform: (O) -> Parser<I, R, U>,
): Parser<I, R, U> =
    Parser { input ->
        when (val result = parser(input)) {
            is Failure -> result
            is Success -> transform(result.value)(ParserInput(input.input, result.nextIndex, input.userContext))
        }
    }

/**
 * Returns a [Parser] that runs [parser] exactly [count] times in sequence,
 * collecting each output into a [List].
 *
 * The runs are applied left-to-right; each run starts where the previous one
 * left off. If any run fails the whole parser fails at that position, without
 * consuming the tokens matched by the successful preceding runs.
 *
 * A [count] of zero always succeeds immediately with an empty list.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | `count == 0` | [Success] with an empty list; index unchanged |
 * | All [count] runs succeed | [Success] with a list of [count] values; index advanced past all |
 * | Any run fails | [Failure] from that run |
 *
 * ### Type parameters
 * - [I] — the token type consumed by [parser].
 * - [O] — the output type of [parser]; each successful value is collected.
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val digit = pSatisfy<Char, Unit> { it.isDigit() }
 * val threeDigits = pRepeat(3, digit)
 *
 * val input = ParserInput.of("123x".toList(), Unit)
 * val result = threeDigits(input)  // Success(['1','2','3'], nextIndex=3, ...)
 * ```
 *
 * @param count the exact number of times to run [parser]; must be >= 0.
 * @param parser the parser to repeat.
 * @return a [Parser] that collects exactly [count] values on success.
 *
 * @see pBind
 * @see pAnd
 */
fun <I : Any, O : Any, U : Any> pRepeat(
    count: Int,
    parser: Parser<I, O, U>,
): Parser<I, List<O>, U> =
    Parser { input ->
        val values = ArrayList<O>(count)
        var current = input
        repeat(count) {
            when (val result = parser(current)) {
                is Failure -> return@Parser result
                is Success -> {
                    values.add(result.value)
                    current = ParserInput(input.input, result.nextIndex, input.userContext)
                }
            }
        }
        Success(values, current.index, input)
    }
