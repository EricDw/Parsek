package parsek.commonmark.parser

import parsek.Parser
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.commonmark.ast.Document
import parsek.commonmark.ast.Inline
import parsek.commonmark.parser.block.normalizeLinkLabel
import parsek.commonmark.parser.block.pAtxHeading
import parsek.commonmark.parser.block.pBlockQuote
import parsek.commonmark.parser.block.pFencedCodeBlock
import parsek.commonmark.parser.block.pHtmlBlock
import parsek.commonmark.parser.block.pIndentedCodeBlock
import parsek.commonmark.parser.block.pLinkReferenceDefinition
import parsek.commonmark.parser.block.pList
import parsek.commonmark.parser.block.pParagraph
import parsek.commonmark.parser.block.pSetextHeading
import parsek.commonmark.parser.block.pThematicBreak
import parsek.commonmark.parser.inline.parseInlineContent
import parsek.pChoice
import parsek.pLabel
import parsek.pMany
import parsek.pMap
import parsek.text.pBlankLine

// ---------------------------------------------------------------------------
// pBlock — ordered choice of all block parsers
// ---------------------------------------------------------------------------

/**
 * Parses a single CommonMark block.
 *
 * The alternatives are tried in CommonMark precedence order:
 * 1. Blank line (consumed as [Block.BlankLine])
 * 2. Thematic break
 * 3. ATX heading
 * 4. Fenced code block
 * 5. HTML block
 * 6. Link reference definition
 * 7. Block quote (recursive)
 * 8. List (recursive)
 * 9. Setext heading (after list to avoid `---` ambiguity)
 * 10. Indented code block
 * 11. Paragraph (fallback)
 *
 * Container blocks ([Block.BlockQuote], list types) recursively call `pBlock`
 * via the `blockFactory` parameter to parse their nested content.
 *
 * @return a [Parser] that succeeds with a [Block] node, or fails.
 */
fun <U : Any> pBlock(): Parser<Char, Block, U> =
    pLabel(
        pChoice(
            pMap(pBlankLine<U>()) { Block.BlankLine },
            pThematicBreak(),
            pAtxHeading(),
            pFencedCodeBlock(),
            pHtmlBlock(),
            pLinkReferenceDefinition(),
            pBlockQuote { pBlock() },
            pList { pBlock() },
            pSetextHeading(),
            pIndentedCodeBlock(),
            pParagraph(),
        ),
        "block",
    )

// ---------------------------------------------------------------------------
// pDocument — top-level document parser
// ---------------------------------------------------------------------------

/**
 * Parses a complete CommonMark document.
 *
 * This is the top-level entry point that implements the two-pass design
 * described in the CommonMark specification:
 *
 * 1. **Block pass**: parse the input into a flat list of [Block] nodes using
 *    [pBlock]. During this pass, [Block.LinkReferenceDefinition] nodes are
 *    collected to build a reference map.
 *
 * 2. **Inline pass**: walk the block tree and re-parse the raw inline content
 *    in [Block.Paragraph] and [Block.Heading] nodes using the full inline
 *    parser pipeline ([parseInlineContent]), which resolves link references
 *    against the map built in step 1.
 *
 * After processing, [Block.BlankLine] and [Block.LinkReferenceDefinition]
 * nodes are removed from the output — they serve only as structural markers
 * during parsing.
 *
 * @return a [Parser] that succeeds with a [Document], or produces a
 *   [Document] with an empty block list on empty input.
 */
fun <U : Any> pDocument(): Parser<Char, Document, U> =
    pLabel(
        pMap(pMany(pBlock())) { blocks ->
            // 1. Collect link reference definitions (first definition wins).
            val refMap = mutableMapOf<String, Pair<String, String?>>()
            for (block in blocks) {
                collectLinkRefDefs(block, refMap)
            }

            val resolveRef: (String) -> Pair<String, String?>? = { label ->
                refMap[label]
            }

            // 2. Re-parse inline content and filter structural-only blocks.
            val processed = blocks
                .map { block -> resolveInlines(block, resolveRef) }
                .filter { it !is Block.BlankLine && it !is Block.LinkReferenceDefinition }

            Document(processed)
        },
        "document",
    )

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

/**
 * Recursively collects [Block.LinkReferenceDefinition] nodes into [refMap].
 * First definition for a given normalised label wins (subsequent duplicates
 * are ignored).
 */
private fun collectLinkRefDefs(
    block: Block,
    refMap: MutableMap<String, Pair<String, String?>>,
) {
    when (block) {
        is Block.LinkReferenceDefinition -> {
            val label = normalizeLinkLabel(block.label)
            if (label.isNotBlank()) {
                refMap.putIfAbsent(label, Pair(block.destination, block.title))
            }
        }
        is Block.BlockQuote -> block.blocks.forEach { collectLinkRefDefs(it, refMap) }
        is Block.BulletList -> block.items.forEach { item ->
            item.blocks.forEach { collectLinkRefDefs(it, refMap) }
        }
        is Block.OrderedList -> block.items.forEach { item ->
            item.blocks.forEach { collectLinkRefDefs(it, refMap) }
        }
        is Block.ListItem -> block.blocks.forEach { collectLinkRefDefs(it, refMap) }
        else -> {}
    }
}

/**
 * Recursively walks the block tree and replaces stub inline content in
 * [Block.Paragraph] and [Block.Heading] nodes with fully parsed inline
 * content using the inline parser pipeline.
 *
 * Container blocks are walked recursively; leaf blocks other than paragraphs
 * and headings are returned unchanged.
 */
private fun resolveInlines(
    block: Block,
    resolveRef: (String) -> Pair<String, String?>?,
): Block = when (block) {
    is Block.Paragraph -> {
        val raw = extractRawContent(block.inlines)
        if (raw != null) {
            val inlines = parseInlineContent(raw.toList(), Unit, resolveRef)
            Block.Paragraph(inlines)
        } else block
    }
    is Block.Heading -> {
        val raw = extractRawContent(block.inlines)
        if (raw != null) {
            val inlines = parseInlineContent(raw.toList(), Unit, resolveRef)
            Block.Heading(block.level, inlines)
        } else block
    }
    is Block.BlockQuote ->
        Block.BlockQuote(block.blocks.map { resolveInlines(it, resolveRef) })
    is Block.BulletList ->
        Block.BulletList(
            block.tight, block.marker,
            block.items.map { item ->
                Block.ListItem(item.blocks.map { resolveInlines(it, resolveRef) })
            },
        )
    is Block.OrderedList ->
        Block.OrderedList(
            block.tight, block.start, block.delimiter,
            block.items.map { item ->
                Block.ListItem(item.blocks.map { resolveInlines(it, resolveRef) })
            },
        )
    is Block.ListItem ->
        Block.ListItem(block.blocks.map { resolveInlines(it, resolveRef) })
    else -> block
}

/**
 * Extracts the raw text content from a list of stub inline nodes.
 *
 * During the block pass, paragraphs and headings store their content as a
 * single [Inline.Text] node. This function extracts that raw string so it
 * can be re-parsed by the inline parser.
 *
 * Returns `null` if the inline list does not contain a simple text stub
 * (i.e. it has already been parsed or is empty).
 */
private fun extractRawContent(inlines: List<Inline>): String? {
    if (inlines.size == 1) {
        val single = inlines[0]
        if (single is Inline.Text) return single.literal
    }
    return null
}
