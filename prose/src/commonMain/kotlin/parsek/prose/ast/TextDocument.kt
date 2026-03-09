package parsek.prose.ast

/**
 * Root AST node for a plain-text document.
 *
 * A document is a flat list of [Paragraph]s. There are no headings or other
 * block-level constructs — everything is a paragraph.
 *
 * @property paragraphs the ordered list of paragraphs in the document.
 */
data class TextDocument(val paragraphs: List<Paragraph>)
