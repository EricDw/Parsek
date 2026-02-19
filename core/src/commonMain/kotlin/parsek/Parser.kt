package parsek

/**
 * A parser that consumes tokens of type [I] and produces a value of type [O],
 * with access to an arbitrary user-defined context of type [U].
 *
 * `Parser` is a functional interface — any lambda or function reference with the
 * matching signature `(ParserInput<I, U>) -> ParseResult<I, O, U>` can be used
 * directly as a `Parser`.
 *
 * ### Type parameters
 * - [I] — the token (input element) type, e.g. `Char` for character-level parsing.
 *   Contravariant: a `Parser<Any, O, U>` can be used wherever a `Parser<Char, O, U>`
 *   is expected.
 * - [O] — the output value type produced on success. Covariant; may be nullable.
 *   A `Parser<I, String, U>` satisfies `Parser<I, CharSequence?, U>`.
 * - [U] — the user context type threaded through parsing without being consumed.
 *   Invariant: use [Unit] when no context is needed.
 *
 * ### Example
 * ```kotlin
 * // A parser that always succeeds and returns the current index
 * val position: Parser<Char, Int, Unit> = Parser { input -> Success(input.index, input.index, input) }
 * ```
 *
 * @see ParserInput
 * @see ParseResult
 * @see pSatisfy
 */
fun interface Parser<in I : Any, out O, U : Any> {
    /**
     * Runs this parser against [input].
     *
     * @param input the current parse position, including the token sequence and user context.
     * @return [Success] containing the parsed value and the next position, or
     *         [Failure] with a diagnostic message and the position at which parsing failed.
     */
    operator fun invoke(input: ParserInput<@UnsafeVariance I, U>): ParseResult<@UnsafeVariance I, O, U>
}
