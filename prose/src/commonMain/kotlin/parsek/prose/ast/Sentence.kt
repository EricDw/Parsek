package parsek.prose.ast

/**
 * A sentence within a [Paragraph].
 *
 * @property tokens the ordered list of [Token]s that make up this sentence,
 *   including [Word], [Punctuation], and [Whitespace] tokens.
 * @property terminator the sentence-ending [Punctuation] token (`.`, `!`, `?`,
 *   or a closing quote/paren after one of those), or `null` if the sentence
 *   has no explicit terminator (e.g. the last sentence before a blank line).
 * @property sourceRange character offsets in the source document.
 */
data class Sentence(
    val tokens: List<Token>,
    val terminator: Punctuation?,
    val sourceRange: IntRange,
)
