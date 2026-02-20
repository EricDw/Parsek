package parsek.commonmark.ast

/**
 * The root node of a CommonMark document.
 *
 * A document is a sequence of [Block] nodes produced by the block-structure
 * pass. Inline nodes appear nested inside blocks such as [Block.Paragraph]
 * and [Block.Heading].
 *
 * @property blocks the top-level block nodes of the document.
 */
data class Document(val blocks: List<Block>)
