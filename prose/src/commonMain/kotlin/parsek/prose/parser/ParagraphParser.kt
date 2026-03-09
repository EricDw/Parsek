package parsek.prose.parser

import parsek.prose.ast.Paragraph

/**
 * Parses a single paragraph's raw text into a [Paragraph] AST node.
 *
 * @param text the raw paragraph text (no blank lines; may contain single line
 *   breaks which are treated as whitespace).
 * @param offset the character offset of this paragraph in the source document.
 * @return a [Paragraph] with tokenized and sentence-split content.
 */
internal fun parseParagraph(text: String, offset: Int): Paragraph {
    val tokens = tokenize(text, offset)
    val sentences = splitSentences(tokens)
    val rangeStart = offset
    val rangeEnd = offset + text.length - 1
    return Paragraph(sentences, rangeStart..maxOf(rangeStart, rangeEnd))
}
