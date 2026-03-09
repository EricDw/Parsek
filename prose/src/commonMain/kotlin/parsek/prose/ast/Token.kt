package parsek.prose.ast

/**
 * A granular token within a [Sentence].
 *
 * Every token carries the original [text] and a [sourceRange] mapping back to
 * character offsets in the source document, enabling precise cursor positioning
 * and selection in an editor.
 */
sealed interface Token {
    val text: String
    val sourceRange: IntRange
}

/**
 * A word — a contiguous run of letters and/or digits.
 */
data class Word(
    override val text: String,
    override val sourceRange: IntRange,
) : Token

/**
 * One or more punctuation characters grouped together.
 *
 * Runs like `...`, `--`, and `""` are kept as single tokens.
 */
data class Punctuation(
    override val text: String,
    override val sourceRange: IntRange,
) : Token

/**
 * One or more whitespace characters (spaces, tabs).
 *
 * Line endings within a paragraph are normalised to a single space during
 * tokenization.
 */
data class Whitespace(
    override val text: String,
    override val sourceRange: IntRange,
) : Token
