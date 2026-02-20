package parsek.commonmark.ast

/**
 * A node in the block structure of a CommonMark document.
 *
 * Block nodes form the top-level structure of a document. The sealed hierarchy
 * mirrors the block node types defined in the CommonMark 0.31.2 specification.
 */
sealed interface Block {

    /**
     * A thematic break (`---`, `***`, or `___`).
     *
     * Rendered as `<hr>` in HTML.
     */
    data object ThematicBreak : Block

    /**
     * An ATX or Setext heading.
     *
     * @property level the heading level (1–6).
     * @property inlines the inline content of the heading.
     */
    data class Heading(
        val level: Int,
        val inlines: List<Inline>,
    ) : Block

    /**
     * A block of code indented by 4 spaces (or 1 tab).
     *
     * @property literal the raw code content with the 4-space prefix stripped.
     */
    data class IndentedCodeBlock(val literal: String) : Block

    /**
     * A fenced code block delimited by backtick or tilde runs.
     *
     * @property info the info string following the opening fence, or `null` if absent.
     * @property literal the raw code content between the fences.
     */
    data class FencedCodeBlock(
        val info: String?,
        val literal: String,
    ) : Block

    /**
     * A raw HTML block. One of the seven HTML block types defined by the spec.
     *
     * @property literal the raw HTML content as it appeared in the source.
     */
    data class HtmlBlock(val literal: String) : Block

    /**
     * A link reference definition (`[label]: destination "title"`).
     *
     * Link reference definitions are collected during the block pass and used
     * to resolve shortcut/full/collapsed reference links in the inline pass.
     *
     * @property label the normalised link label (case-folded, whitespace collapsed).
     * @property destination the link destination URL.
     * @property title the optional link title.
     */
    data class LinkReferenceDefinition(
        val label: String,
        val destination: String,
        val title: String?,
    ) : Block

    /**
     * A paragraph of inline content.
     *
     * @property inlines the inline nodes that make up the paragraph.
     */
    data class Paragraph(val inlines: List<Inline>) : Block

    /**
     * A blank line. Consumed during the block pass and not usually emitted
     * into the final document tree.
     */
    data object BlankLine : Block

    /**
     * A block quotation (`> ...`).
     *
     * @property blocks the block nodes contained within the block quote.
     */
    data class BlockQuote(val blocks: List<Block>) : Block

    /**
     * A single list item, containing one or more block nodes.
     *
     * @property blocks the block nodes inside this list item.
     */
    data class ListItem(val blocks: List<Block>) : Block

    /**
     * An unordered (bullet) list.
     *
     * @property tight `true` if the list is tight (no blank lines between items).
     * @property marker the bullet character used (`-`, `+`, or `*`).
     * @property items the list items.
     */
    data class BulletList(
        val tight: Boolean,
        val marker: Char,
        val items: List<ListItem>,
    ) : Block

    /**
     * An ordered list.
     *
     * @property tight `true` if the list is tight (no blank lines between items).
     * @property start the start number of the list.
     * @property delimiter the delimiter character used (`.` or `)`).
     * @property items the list items.
     */
    data class OrderedList(
        val tight: Boolean,
        val start: Int,
        val delimiter: Char,
        val items: List<ListItem>,
    ) : Block
}
