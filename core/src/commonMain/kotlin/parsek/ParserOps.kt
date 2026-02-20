package parsek

// -------------------------------------------------------------------------------------------------
// Binary operators
// -------------------------------------------------------------------------------------------------

/**
 * Sequences this parser with [other], collecting both outputs into a [Pair].
 *
 * Sugar for [pAnd]: `a + b` is equivalent to `pAnd(a, b)`.
 *
 * Because `+` has higher precedence than any `infix` function, sequence binds
 * tighter than choice without needing parentheses:
 * ```kotlin
 * a + b or c + d   // (a + b) or (c + d)
 * ```
 */
operator fun <I : Any, O1, O2, U : Any> Parser<I, O1, U>.plus(
    other: Parser<I, O2, U>,
): Parser<I, Pair<O1, O2>, U> = pAnd(this, other)

/**
 * Repeats this parser exactly [count] times, collecting outputs into a [List].
 *
 * Sugar for [pRepeat]: `a * 3` is equivalent to `pRepeat(3, a)`.
 */
operator fun <I : Any, O, U : Any> Parser<I, O, U>.times(
    count: Int,
): Parser<I, List<O>, U> = pRepeat(count, this)

// -------------------------------------------------------------------------------------------------
// Infix functions
// -------------------------------------------------------------------------------------------------

/**
 * Tries this parser and, if it fails, tries [other] at the same position.
 *
 * Sugar for [pOr]: `a or b` is equivalent to `pOr(a, b)`.
 *
 * As an `infix` function, `or` has lower precedence than the `+` operator,
 * so sequence naturally dominates choice:
 * ```kotlin
 * a + b or c + d   // (a + b) or (c + d)
 * ```
 */
infix fun <I : Any, O, U : Any> Parser<I, O, U>.or(
    other: Parser<I, O, U>,
): Parser<I, O, U> = pOr(this, other)

/**
 * Replaces this parser's failure message with [message] on failure.
 *
 * Sugar for [pLabel]: `a label "expected digit"` is equivalent to
 * `pLabel(a, "expected digit")`.
 */
infix fun <I : Any, O, U : Any> Parser<I, O, U>.label(
    message: String,
): Parser<I, O, U> = pLabel(this, message)

// -------------------------------------------------------------------------------------------------
// Unary prefix operator
// -------------------------------------------------------------------------------------------------

/**
 * Negative lookahead — succeeds with [Unit] when this parser would fail,
 * fails when this parser would succeed. Never consumes input.
 *
 * Sugar for [pNot]: `!a` is equivalent to `pNot(a)`.
 */
operator fun <I : Any, O, U : Any> Parser<I, O, U>.not(): Parser<I, Unit, U> = pNot(this)

// -------------------------------------------------------------------------------------------------
// Extension properties — postfix style
// -------------------------------------------------------------------------------------------------

/**
 * Zero-or-one: succeeds with the parsed value or `null` if this parser fails.
 *
 * Sugar for [pOptional]: `a.optional` is equivalent to `pOptional(a)`.
 */
val <I : Any, O : Any, U : Any> Parser<I, O, U>.optional: Parser<I, O?, U>
    get() = pOptional(this)

/**
 * Zero-or-more: runs this parser repeatedly until it fails, collecting
 * results into a [List]. Always succeeds.
 *
 * Sugar for [pMany]: `a.many` is equivalent to `pMany(a)`.
 */
val <I : Any, O, U : Any> Parser<I, O, U>.many: Parser<I, List<O>, U>
    get() = pMany(this)

/**
 * One-or-more: runs this parser repeatedly until it fails. Fails if this
 * parser does not match at least once.
 *
 * Sugar for [pMany1]: `a.many1` is equivalent to `pMany1(a)`.
 */
val <I : Any, O, U : Any> Parser<I, O, U>.many1: Parser<I, List<O>, U>
    get() = pMany1(this)

/**
 * Positive lookahead — runs this parser and returns its value without
 * consuming any input.
 *
 * Sugar for [pLookAhead]: `a.lookAhead` is equivalent to `pLookAhead(a)`.
 */
val <I : Any, O, U : Any> Parser<I, O, U>.lookAhead: Parser<I, O, U>
    get() = pLookAhead(this)

// -------------------------------------------------------------------------------------------------
// Extension functions — lambda operations
// -------------------------------------------------------------------------------------------------

/**
 * Transforms the success value of this parser using [transform].
 *
 * Sugar for [pMap]: `a.map { … }` is equivalent to `pMap(a) { … }`.
 *
 * Prefer trailing-lambda call style over `infix` to avoid visual ambiguity.
 */
fun <I : Any, O, R, U : Any> Parser<I, O, U>.map(
    transform: (O) -> R,
): Parser<I, R, U> = pMap(this, transform)

/**
 * Flat-maps the success value of this parser to a second parser, then runs it.
 *
 * Sugar for [pBind]: `a.bind { … }` is equivalent to `pBind(a) { … }`.
 *
 * Prefer trailing-lambda call style over `infix` to avoid visual ambiguity.
 */
fun <I : Any, O, R, U : Any> Parser<I, O, U>.bind(
    transform: (O) -> Parser<I, R, U>,
): Parser<I, R, U> = pBind(this, transform)
