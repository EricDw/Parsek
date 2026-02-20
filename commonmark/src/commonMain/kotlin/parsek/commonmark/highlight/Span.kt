package parsek.commonmark.highlight

/**
 * A half-open source range `[start, end)` annotated with a [TokenType].
 *
 * [start] and [end] are zero-based indices into the flat character sequence
 * that was parsed. The range covers exactly the characters that the parser
 * consumed to produce this token.
 *
 * @property type the semantic type of this span.
 * @property start the index of the first character (inclusive).
 * @property end the index one past the last character (exclusive).
 */
data class Span(
    val type: TokenType,
    val start: Int,
    val end: Int,
)
