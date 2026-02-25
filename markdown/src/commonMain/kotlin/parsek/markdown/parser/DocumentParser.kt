package parsek.markdown.parser

import parsek.*
import parsek.markdown.ast.Block
import parsek.markdown.ast.Document
import parsek.markdown.ast.Inline
import parsek.markdown.parser.block.*
import parsek.markdown.parser.inline.parseInlineContent
import parsek.markdown.parser.inline.splitExtendedAutolinks
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
 * 9. Indented code block (before setext so `    foo` is not treated as setext content)
 * 10. Setext heading (after indented code block and list to avoid `---` ambiguity)
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
            pTable(),
            pBlockQuote { pBlock() },
            pList { pBlock() },
            pIndentedCodeBlock(),
            pSetextHeading(),
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
internal fun collectLinkRefDefs(
    block: Block,
    refMap: MutableMap<String, Pair<String, String?>>,
) {
    when (block) {
        is Block.LinkReferenceDefinition -> {
            val label = normalizeLinkLabel(block.label)
            if (label.isNotBlank()) {
                refMap.getOrPut(label) { Pair(block.destination, block.title) }
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
internal fun resolveInlines(
    block: Block,
    resolveRef: (String) -> Pair<String, String?>?,
): Block = when (block) {
    is Block.Paragraph -> {
        val raw = extractRawContent(block.inlines)
        if (raw != null) {
            val inlines = splitExtendedAutolinks(parseInlineContent(raw.toList(), Unit, resolveRef))
            Block.Paragraph(inlines)
        } else block
    }
    is Block.Heading -> {
        val raw = extractRawContent(block.inlines)
        if (raw != null) {
            val inlines = splitExtendedAutolinks(parseInlineContent(raw.toList(), Unit, resolveRef))
            Block.Heading(block.level, inlines)
        } else block
    }
    is Block.BlockQuote ->
        Block.BlockQuote(block.blocks.map { resolveInlines(it, resolveRef) })
    is Block.BulletList ->
        Block.BulletList(
            block.tight, block.marker,
            block.items.map { item -> resolveListItem(item, resolveRef) },
        )
    is Block.OrderedList ->
        Block.OrderedList(
            block.tight, block.start, block.delimiter,
            block.items.map { item -> resolveListItem(item, resolveRef) },
        )
    is Block.ListItem ->
        resolveListItem(block, resolveRef)
    is Block.Table ->
        Block.Table(
            block.alignments,
            resolveTableRow(block.header, resolveRef),
            block.body.map { resolveTableRow(it, resolveRef) },
        )
    is Block.TableRow -> block
    is Block.TableCell -> block
    else -> block
}

/**
 * Resolves a list item, detecting a GFM task list marker at the start of its
 * first paragraph's **raw text** (before inline parsing) and setting the
 * `checked` field accordingly.
 *
 * A task list item marker is `[ ]` (unchecked), `[x]`, or `[X]` (checked),
 * followed by at least one whitespace character, at the very start of the
 * first paragraph in the item.
 */
private fun resolveListItem(
    item: Block.ListItem,
    resolveRef: (String) -> Pair<String, String?>?,
): Block.ListItem {
    // Detect task marker BEFORE inline resolution, on raw text content.
    var checked: Boolean? = item.checked
    val blocks = item.blocks.toMutableList()

    val firstParagraphIdx = blocks.indexOfFirst { it is Block.Paragraph }
    if (firstParagraphIdx != -1) {
        val para = blocks[firstParagraphIdx] as Block.Paragraph
        val raw = extractRawContent(para.inlines)
        if (raw != null) {
            val taskResult = parseTaskMarker(raw)
            if (taskResult != null) {
                checked = taskResult.first
                // Strip the marker from the raw text before inline parsing.
                val stripped = raw.substring(taskResult.second)
                val inlines = splitExtendedAutolinks(parseInlineContent(stripped.toList(), Unit, resolveRef))
                blocks[firstParagraphIdx] = Block.Paragraph(inlines)
                // Return early — the paragraph has been resolved.
                val remaining = blocks.mapIndexed { i, b ->
                    if (i == firstParagraphIdx) b else resolveInlines(b, resolveRef)
                }
                return Block.ListItem(remaining, checked)
            }
        }
    }

    return Block.ListItem(blocks.map { resolveInlines(it, resolveRef) }, checked)
}

/**
 * Parses a task list marker at the start of [text].
 *
 * Returns `(checked, endIndex)` where `endIndex` is the position after the
 * marker and its trailing whitespace, or `null` if no valid marker is found.
 */
private fun parseTaskMarker(text: String): Pair<Boolean, Int>? {
    if (text.length < 4) return null  // minimum: "[ ] " = 4 chars
    if (text[0] != '[') return null
    val checked = when (text[1]) {
        ' ' -> false
        'x', 'X' -> true
        else -> return null
    }
    if (text[2] != ']') return null
    // Must be followed by whitespace.
    if (text.length < 4 || (text[3] != ' ' && text[3] != '\t')) return null
    // Consume the marker and exactly one whitespace.
    return Pair(checked, 4)
}

/**
 * Resolves inline content in each cell of a table row.
 */
private fun resolveTableRow(
    row: Block.TableRow,
    resolveRef: (String) -> Pair<String, String?>?,
): Block.TableRow =
    Block.TableRow(
        row.cells.map { cell ->
            val raw = extractRawContent(cell.inlines)
            if (raw != null) {
                Block.TableCell(splitExtendedAutolinks(parseInlineContent(raw.toList(), Unit, resolveRef)))
            } else cell
        },
    )

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
internal fun extractRawContent(inlines: List<Inline>): String? {
    if (inlines.size == 1) {
        val single = inlines[0]
        if (single is Inline.Text) return single.literal
    }
    return null
}
