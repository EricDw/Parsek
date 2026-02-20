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
 * Returns a [Parser] that succeeds with [Unit] only when all input has been
 * consumed, and fails if any tokens remain.
 *
 * `pEof` is typically used at the end of a top-level parser to assert that
 * the entire input was consumed and no trailing content was left unparsed.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | Input is exhausted | [Success] with [Unit]; index unchanged |
 * | Tokens remain | [Failure] — "Expected end of input but got \<token\> at index \<n\>" |
 *
 * ### Type parameters
 * - [I] — the token type.
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val digit = pSatisfy<Char, Unit> { it.isDigit() }
 * val singleDigit = pAnd(digit, pEof())
 *
 * val input = ParserInput.of("3".toList(), Unit)
 * val result = singleDigit(input)  // Success(Pair('3', Unit), nextIndex=1, ...)
 *
 * val tooLong = ParserInput.of("3x".toList(), Unit)
 * val result2 = singleDigit(tooLong)  // Failure — 'x' remains
 * ```
 *
 * @return a [Parser] that succeeds with [Unit] at end of input.
 *
 * @see pAny
 * @see pSatisfy
 */
fun <I : Any, U : Any> pEof(): Parser<I, Unit, U> =
    Parser { input ->
        if (input.isAtEnd) Success(Unit, input.index, input)
        else Failure(
            "Expected end of input but got ${input.current()} at index ${input.index}",
            input.index,
            input,
        )
    }

/**
 * Returns a [Parser] that consumes and returns any single token, failing only
 * when the input is exhausted.
 *
 * `pAny` is the unconditional token consumer — it never rejects a token on
 * its own, only failing at end of input. Use it as a wildcard in situations
 * where you need to skip or collect tokens regardless of their value.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | Input is exhausted | [Failure] — "Unexpected end of input" |
 * | Token available | [Success] with that token; index advances by 1 |
 *
 * ### Type parameters
 * - [I] — the token type.
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val input = ParserInput.of("ab".toList(), Unit)
 * val result = pAny<Char, Unit>()(input)  // Success('a', nextIndex=1, ...)
 * ```
 *
 * @return a [Parser] that succeeds with the next token or fails at end of input.
 *
 * @see pEof
 * @see pSatisfy
 */
fun <I : Any, U : Any> pAny(): Parser<I, I, U> =
    Parser { input ->
        if (input.isAtEnd) Failure("Unexpected end of input", input.index, input)
        else Success(input.current(), input.index + 1, input)
    }

/**
 * Returns a [Parser] that runs [parser] and, if it succeeds, returns its value
 * without consuming any input.
 *
 * `pLookAhead` peeks ahead in the input: the index is always reset to its
 * original position after a successful match. A failure from [parser] is
 * propagated unchanged, also without consuming input.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | [parser] succeeds | [Success] with [parser]'s value; index **unchanged** |
 * | [parser] fails | [Failure] propagated from [parser]; index unchanged |
 *
 * ### Type parameters
 * - [I] — the token type consumed by [parser].
 * - [O] — the output type of [parser].
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val digit = pSatisfy<Char, Unit> { it.isDigit() }
 * val peek = pLookAhead(digit)
 *
 * val input = ParserInput.of("1a".toList(), Unit)
 * val result = peek(input)  // Success('1', nextIndex=0, ...) — index stays at 0
 * ```
 *
 * @param parser the parser to run as a lookahead.
 * @return a [Parser] that succeeds without advancing the index.
 *
 * @see pNot
 * @see pSatisfy
 */
fun <I : Any, O, U : Any> pLookAhead(parser: Parser<I, O, U>): Parser<I, O, U> =
    Parser { input ->
        when (val result = parser(input)) {
            is Failure -> result
            is Success -> Success(result.value, input.index, input)
        }
    }

/**
 * Returns a [Parser] that succeeds with [Unit] when [parser] would fail at the
 * current position, and fails when [parser] would succeed. No input is consumed
 * in either case.
 *
 * `pNot` is a negative lookahead: it inverts the success/failure of [parser]
 * without ever advancing the index. Use it to assert that a particular pattern
 * does *not* appear at the current position before committing to another parse.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | [parser] fails | [Success] with [Unit]; index unchanged |
 * | [parser] succeeds | [Failure] — "Unexpected match at index \<n\>"; index unchanged |
 *
 * ### Type parameters
 * - [I] — the token type.
 * - [O] — the output type of [parser] (not used in the result).
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val notDigit = pNot(pSatisfy<Char, Unit> { it.isDigit() })
 *
 * val input = ParserInput.of("a1".toList(), Unit)
 * val result = notDigit(input)  // Success(Unit, nextIndex=0, ...) — 'a' is not a digit
 *
 * val digits = ParserInput.of("1a".toList(), Unit)
 * val result2 = notDigit(digits)  // Failure — '1' would have matched
 * ```
 *
 * @param parser the parser whose failure is required.
 * @return a [Parser] that succeeds with [Unit] only when [parser] would fail.
 *
 * @see pLookAhead
 * @see pSatisfy
 */
fun <I : Any, O, U : Any> pNot(parser: Parser<I, O, U>): Parser<I, Unit, U> =
    Parser { input ->
        when (parser(input)) {
            is Success -> Failure("Unexpected match at index ${input.index}", input.index, input)
            is Failure -> Success(Unit, input.index, input)
        }
    }

/**
 * Returns a [Parser] that runs [parser] and, if it fails, replaces the failure
 * message with [message].
 *
 * `pLabel` is the primary way to produce human-readable error messages. Instead
 * of exposing low-level diagnostic text from [pSatisfy] or nested combinators,
 * wrap a parser in `pLabel` to emit a single, intent-describing message when
 * it fails.
 *
 * On success the result is passed through unchanged.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | [parser] succeeds | [Success] propagated unchanged |
 * | [parser] fails | [Failure] with [message]; index and input from the original failure |
 *
 * ### Type parameters
 * - [I] — the token type consumed by [parser].
 * - [O] — the output type of [parser].
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val digit = pLabel(pSatisfy<Char, Unit> { it.isDigit() }, "a digit")
 *
 * val input = ParserInput.of("abc".toList(), Unit)
 * val result = digit(input)
 * // Failure("a digit", index=0, ...)
 * ```
 *
 * @param parser the parser to run.
 * @param message the failure message to use when [parser] fails.
 * @return a [Parser] that propagates success and replaces failure messages.
 *
 * @see pSatisfy
 * @see pMap
 */
fun <I : Any, O, U : Any> pLabel(
    parser: Parser<I, O, U>,
    message: String,
): Parser<I, O, U> =
    Parser { input ->
        when (val result = parser(input)) {
            is Success -> result
            is Failure -> Failure(message, result.index, result.input)
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
fun <I : Any, O1, O2, U : Any> pAnd(
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
fun <I : Any, O, U : Any> pOr(
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
fun <I : Any, O, R, U : Any> pMap(
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
fun <I : Any, O, R, U : Any> pBind(
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
 * @see pSequence
 * @see pMany
 */
fun <I : Any, O, U : Any> pRepeat(
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

/**
 * Returns a [Parser] that runs each parser in [parsers] in order, collecting
 * every output into a [List].
 *
 * Unlike [pRepeat], which applies a single parser [count] times, `pSequence`
 * applies a *different* parser at each position. The runs proceed left-to-right;
 * each run starts where the previous one left off. If any parser fails the whole
 * combined parser fails immediately at that position.
 *
 * An empty [parsers] list always succeeds with an empty list.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | [parsers] is empty | [Success] with an empty list; index unchanged |
 * | All parsers succeed | [Success] with a list of values; index advanced past all |
 * | Any parser fails | [Failure] from that parser |
 *
 * ### Type parameters
 * - [I] — the shared token type consumed by all parsers.
 * - [O] — the shared output type; all parsers must produce the same type.
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val digit = pSatisfy<Char, Unit> { it.isDigit() }
 * val letter = pSatisfy<Char, Unit> { it.isLetter() }
 * val parser = pSequence(listOf(digit, letter, digit))
 *
 * val input = ParserInput.of("1a2".toList(), Unit)
 * val result = parser(input)  // Success(['1','a','2'], nextIndex=3, ...)
 * ```
 *
 * @param parsers the ordered list of parsers to run.
 * @return a [Parser] that collects the output of each parser on success.
 *
 * @see pRepeat
 * @see pAnd
 * @see pMany
 */
fun <I : Any, O, U : Any> pSequence(
    parsers: List<Parser<I, O, U>>,
): Parser<I, List<O>, U> =
    Parser { input ->
        val values = ArrayList<O>(parsers.size)
        var current = input
        for (parser in parsers) {
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

/**
 * Returns a [Parser] that tries [parser] and, if it fails, succeeds with `null`
 * at the same position.
 *
 * `pOptional` is the zero-or-one combinator — it always succeeds. Use it to
 * represent optional grammar elements.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | [parser] succeeds | [Success] with the parsed value; index advanced |
 * | [parser] fails | [Success] with `null`; index unchanged |
 *
 * ### Type parameters
 * - [I] — the token type consumed by [parser].
 * - [O] — the output type of [parser]; `null` is returned when it is absent.
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val sign = pSatisfy<Char, Unit> { it == '+' || it == '-' }
 * val optionalSign = pOptional(sign)
 *
 * val input = ParserInput.of("-1".toList(), Unit)
 * val result = optionalSign(input)  // Success('-', nextIndex=1, ...)
 *
 * val noSign = ParserInput.of("1".toList(), Unit)
 * val result2 = optionalSign(noSign)  // Success(null, nextIndex=0, ...)
 * ```
 *
 * @param parser the parser to attempt.
 * @return a [Parser] that always succeeds with the parsed value or `null`.
 *
 * @see pMany
 * @see pOr
 */
fun <I : Any, O : Any, U : Any> pOptional(
    parser: Parser<I, O, U>,
): Parser<I, O?, U> =
    Parser { input ->
        when (val result = parser(input)) {
            is Failure -> Success(null, input.index, input)
            is Success -> result
        }
    }

/**
 * Returns a [Parser] that runs [parser] one or more times, collecting each
 * output into a [List]. Fails if [parser] does not match at least once.
 *
 * Equivalent to requiring one mandatory match followed by [pMany]: the first
 * run of [parser] must succeed, after which further runs are collected until
 * failure.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | [parser] fails on first attempt | [Failure] from [parser] |
 * | [parser] succeeds N times then fails (N ≥ 1) | [Success] with N values; index advanced past all |
 *
 * ### Type parameters
 * - [I] — the token type consumed by [parser].
 * - [O] — the output type of [parser]; each successful value is collected.
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val digit = pSatisfy<Char, Unit> { it.isDigit() }
 * val digits = pMany1(digit)
 *
 * val input = ParserInput.of("42!".toList(), Unit)
 * val result = digits(input)  // Success(['4','2'], nextIndex=2, ...)
 * ```
 *
 * @param parser the parser to run one or more times.
 * @return a [Parser] that succeeds with a non-empty list, or fails if [parser]
 *   does not match at least once.
 *
 * @see pMany
 * @see pRepeat
 */
fun <I : Any, O, U : Any> pMany1(
    parser: Parser<I, O, U>,
): Parser<I, List<O>, U> =
    pMap(pAnd(parser, pMany(parser))) { (first, rest) -> listOf(first) + rest }

/**
 * Returns a [Parser] that runs [parser] repeatedly until it fails, collecting
 * each output into a [List]. Always succeeds, returning an empty list if
 * [parser] fails on the first attempt.
 *
 * `pMany` is the zero-or-more repetition combinator. For one-or-more, pair it
 * with an initial required match using [pAnd] or [pBind].
 *
 * > **Note:** [parser] must consume at least one token on each success. If it
 * > succeeds without advancing the index, `pMany` stops immediately to prevent
 * > an infinite loop.
 *
 * ### Behaviour
 * | Condition | Result |
 * |---|---|
 * | [parser] fails on first attempt | [Success] with an empty list; index unchanged |
 * | [parser] succeeds N times then fails | [Success] with N values; index advanced past all |
 * | [parser] succeeds without advancing | [Success] with values collected so far; loop stops |
 *
 * ### Type parameters
 * - [I] — the token type consumed by [parser].
 * - [O] — the output type of [parser]; each successful value is collected.
 * - [U] — the user context type threaded through unchanged.
 *
 * ### Example
 * ```kotlin
 * val letter = pSatisfy<Char, Unit> { it.isLetter() }
 * val word = pMany(letter)
 *
 * val input = ParserInput.of("abc!".toList(), Unit)
 * val result = word(input)  // Success(['a','b','c'], nextIndex=3, ...)
 * ```
 *
 * @param parser the parser to run repeatedly.
 * @return a [Parser] that always succeeds with a (possibly empty) list of values.
 *
 * @see pRepeat
 * @see pMany1
 * @see pOptional
 * @see pAnd
 */
fun <I : Any, O, U : Any> pMany(
    parser: Parser<I, O, U>,
): Parser<I, List<O>, U> =
    Parser { input ->
        val values = mutableListOf<O>()
        var current = input
        while (true) {
            when (val result = parser(current)) {
                is Failure -> break
                is Success -> {
                    if (result.nextIndex == current.index) break
                    values.add(result.value)
                    current = ParserInput(input.input, result.nextIndex, input.userContext)
                }
            }
        }
        Success(values, current.index, input)
    }
