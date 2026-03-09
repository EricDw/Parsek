package parsek.prose.ast

/**
 * A paragraph within a [TextDocument], separated from other paragraphs by blank lines.
 *
 * @property sentences the ordered list of [Sentence]s in this paragraph.
 * @property sourceRange character offsets in the source document.
 */
data class Paragraph(
    val sentences: List<Sentence>,
    val sourceRange: IntRange,
)
