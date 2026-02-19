package parsek

/**
 * The result of running a [Parser] against a [ParserInput].
 *
 * Exactly one of two subtypes is returned:
 * - [Success] — the parser matched and produced a value.
 * - [Failure] — the parser did not match and produced a diagnostic message.
 *
 * Exhaustive handling via `when`:
 * ```kotlin
 * when (val result = parser(input)) {
 *     is Success -> println("Parsed: ${result.value}")
 *     is Failure -> println("Error at ${result.index}: ${result.message}")
 * }
 * ```
 *
 * ### Type parameters
 * - [I] — the token type from the originating [ParserInput]. Covariant.
 * - [O] — the output value type on success. Covariant. [Failure] fixes this to
 *   [Nothing] so a `Failure<I, U>` is a subtype of any `ParseResult<I, O, U>`.
 * - [U] — the user context type. Invariant.
 *
 * @see Parser
 * @see ParserInput
 */
sealed class ParseResult<out I : Any, out O : Any, U : Any>

/**
 * Indicates that the parser matched successfully.
 *
 * @property value the value produced by the parser.
 * @property nextIndex the position in the token sequence immediately after the
 *   last consumed token. Pass this to the next [ParserInput] to continue parsing.
 * @property input the [ParserInput] that was active when this result was produced.
 *   Useful for retrieving the [ParserInput.userContext] or for error reporting.
 */
data class Success<out I : Any, out O : Any, U : Any>(
    val value: O,
    val nextIndex: Int,
    val input: ParserInput<I, U>,
) : ParseResult<I, O, U>()

/**
 * Indicates that the parser did not match.
 *
 * A [Failure] does not consume any input; the caller may attempt alternative
 * parsers at the same position.
 *
 * @property message a human-readable description of what was expected or what
 *   went wrong.
 * @property index the position in the token sequence at which the failure
 *   occurred.
 * @property input the [ParserInput] that was active when this result was produced.
 */
data class Failure<out I : Any, U : Any>(
    val message: String,
    val index: Int,
    val input: ParserInput<I, U>,
) : ParseResult<I, Nothing, U>()
