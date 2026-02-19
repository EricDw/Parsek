package parsek

/**
 * An immutable, position-aware view into a token sequence, optionally carrying a
 * user-defined context value.
 *
 * Every successful parse step produces a new `ParserInput` advanced past the
 * consumed tokens; the underlying [input] list and [userContext] are shared by
 * reference, so advancing is allocation-light.
 *
 * ### Type parameters
 * - [I] — the token type (e.g. `Char`). Covariant: a `ParserInput<Char, U>` can
 *   be read wherever a `ParserInput<Any, U>` is expected.
 * - [U] — the user context type. Invariant. Use [Unit] when no context is needed.
 *
 * ### Example
 * ```kotlin
 * data class ParseState(val source: String)
 *
 * val input = ParserInput.of("hello".toList(), ParseState("hello.kt"))
 * println(input.current())   // 'h'
 * println(input.advance().current())  // 'e'
 * println(input.userContext.source)   // "hello.kt"
 * ```
 *
 * @param input the full token sequence being parsed.
 * @param index the current position within [input].
 * @param userContext an arbitrary value threaded through parsing unchanged.
 *
 * @see Parser
 * @see ParseResult
 */
class ParserInput<out I : Any, U : Any>(val input: List<I>, val index: Int, val userContext: U) {

    /** `true` when [index] is at or past the end of [input]; no more tokens to consume. */
    val isAtEnd: Boolean get() = index >= input.size

    /**
     * Returns the token at the current [index].
     *
     * @throws IndexOutOfBoundsException if [isAtEnd] is `true`.
     */
    fun current(): I = input[index]

    /**
     * Returns a new [ParserInput] advanced by one position, sharing the same
     * [input] list and [userContext].
     *
     * Does not check bounds — call only after confirming [isAtEnd] is `false`.
     */
    fun advance(): ParserInput<I, U> = ParserInput(input, index + 1, userContext)

    companion object {
        /**
         * Creates a [ParserInput] from a [Collection], converting it to a [List]
         * internally.
         *
         * @param input the token sequence to parse.
         * @param userContext an arbitrary value made available to parsers throughout
         *   the parse without being consumed. Use [Unit] when no context is required.
         * @param index the starting position within [input]; defaults to `0`.
         */
        fun <I : Any, U : Any> of(input: Collection<I>, userContext: U, index: Int = 0) =
            ParserInput(input.toList(), index, userContext)
    }
}
