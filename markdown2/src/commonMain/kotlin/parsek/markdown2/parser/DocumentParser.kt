package parsek.markdown2.parser

import parsek.markdown.ast.Block
import parsek.markdown.ast.Document
import parsek.markdown.ast.Inline
import parsek.markdown2.lexer.splitLines
import parsek.markdown2.scanner.scanDocument

/**
 * Parses a markdown string into a [Document] AST.
 *
 * Three-pass pipeline:
 *   1. **Block pass**: Scan → Split lines → Recursive line parsing → blocks with stub inlines
 *   2. **Link ref def extraction**: Extract link reference definitions from paragraphs
 *   3. **Inline pass**: Replace stub inlines with fully parsed inline content using resolver
 */
/**
 * Parses a markdown string into a [Document] AST.
 *
 * @param gfm when `true`, enables GFM extensions (task lists, strikethrough,
 *   tables, extended autolinks). When `false`, only standard CommonMark is used.
 */
fun parseDocument(text: String, gfm: Boolean = true): Document {
    // Stage 1: Scan characters into lexemes
    val lexemes = scanDocument(text)

    // Stage 2: Split into lines and parse blocks (including containers)
    val rawLines = splitLines(lexemes)
    val lines = rawLines.map { Line.from(it) }
    val blocks = parseLines(lines)

    // Stage 3: Extract link reference definitions
    val refDefs = mutableMapOf<String, Pair<String, String?>>()
    val cleanedBlocks = extractLinkRefDefs(blocks, refDefs)

    // Stage 4: Build resolver and resolve inlines
    val resolver: LinkRefResolver? = if (refDefs.isNotEmpty()) {
        { label -> refDefs[label] }
    } else {
        null
    }

    val resolved = resolveInlines(cleanedBlocks, resolver, gfm)
    return Document(resolved)
}

/**
 * Recursively walks the block tree and extracts link reference definitions
 * from paragraphs. Paragraphs that consist entirely of link ref defs are removed.
 * First definition for a label wins (subsequent ones are ignored).
 */
private fun extractLinkRefDefs(
    blocks: List<Block>,
    refDefs: MutableMap<String, Pair<String, String?>>,
): List<Block> = blocks.mapNotNull { block ->
    when (block) {
        is Block.LinkReferenceDefinition -> {
            // Already parsed during block pass (e.g., setext heading context)
            if (block.label !in refDefs) {
                refDefs[block.label] = block.destination to block.title
            }
            null // Remove from output
        }
        is Block.Paragraph -> {
            val text = extractStubText(block.inlines)
            val (defs, remaining) = parseLinkRefDefs(text)
            for (def in defs) {
                if (def.label !in refDefs) {
                    refDefs[def.label] = def.destination to def.title
                }
            }
            val trimmed = remaining.trim()
            if (trimmed.isEmpty()) {
                null // Entire paragraph was link ref defs
            } else {
                Block.Paragraph(listOf(Inline.Text(trimmed)))
            }
        }
        is Block.BlockQuote -> Block.BlockQuote(extractLinkRefDefs(block.blocks, refDefs))
        is Block.BulletList -> Block.BulletList(
            block.tight,
            block.marker,
            block.items.map { Block.ListItem(extractLinkRefDefs(it.blocks, refDefs), it.checked) },
        )
        is Block.OrderedList -> Block.OrderedList(
            block.tight,
            block.start,
            block.delimiter,
            block.items.map { Block.ListItem(extractLinkRefDefs(it.blocks, refDefs), it.checked) },
        )
        is Block.ListItem -> Block.ListItem(extractLinkRefDefs(block.blocks, refDefs), block.checked)
        is Block.Table -> Block.Table(
            block.alignments,
            Block.TableRow(block.header.cells.map { Block.TableCell(it.inlines) }),
            block.body.map { row ->
                Block.TableRow(row.cells.map { Block.TableCell(it.inlines) })
            },
        )
        else -> block
    }
}

/**
 * Recursively walks the block tree and replaces stub [Inline.Text] nodes
 * with fully parsed inline content using [parseInlines].
 */
private fun resolveInlines(blocks: List<Block>, resolver: LinkRefResolver?, gfm: Boolean = true): List<Block> = blocks.map { block ->
    when (block) {
        is Block.Paragraph -> {
            val text = extractStubText(block.inlines)
            val inlines = parseInlines(text, resolver)
            Block.Paragraph(if (gfm) splitExtendedAutolinks(inlines) else inlines)
        }
        is Block.Heading -> {
            val text = extractStubText(block.inlines)
            val inlines = parseInlines(text, resolver)
            Block.Heading(block.level, if (gfm) splitExtendedAutolinks(inlines) else inlines)
        }
        is Block.BlockQuote -> Block.BlockQuote(resolveInlines(block.blocks, resolver, gfm))
        is Block.BulletList -> Block.BulletList(
            block.tight,
            block.marker,
            block.items.map { resolveListItem(it, resolver, gfm) },
        )
        is Block.OrderedList -> Block.OrderedList(
            block.tight,
            block.start,
            block.delimiter,
            block.items.map { resolveListItem(it, resolver, gfm) },
        )
        is Block.ListItem -> resolveListItem(block, resolver, gfm)
        is Block.Table -> Block.Table(
            block.alignments,
            resolveTableRow(block.header, resolver, gfm),
            block.body.map { resolveTableRow(it, resolver, gfm) },
        )
        else -> block // ThematicBreak, CodeBlock, HtmlBlock, etc. — no inline content
    }
}

/**
 * Resolves inline content in each cell of a table row.
 */
private fun resolveTableRow(row: Block.TableRow, resolver: LinkRefResolver?, gfm: Boolean): Block.TableRow {
    return Block.TableRow(row.cells.map { cell ->
        val text = extractStubText(cell.inlines)
        val inlines = parseInlines(text, resolver)
        Block.TableCell(if (gfm) splitExtendedAutolinks(inlines) else inlines)
    })
}

/**
 * Resolves a list item, detecting a GFM task list marker at the start of its
 * first paragraph's raw text (before inline parsing) and setting the
 * `checked` field accordingly.
 *
 * A task list item marker is `[ ]` (unchecked), `[x]`, or `[X]` (checked),
 * followed by at least one whitespace character, at the very start of the
 * first paragraph in the item.
 */
private fun resolveListItem(item: Block.ListItem, resolver: LinkRefResolver?, gfm: Boolean): Block.ListItem {
    var checked: Boolean? = item.checked
    val blocks = item.blocks.toMutableList()

    val firstParagraphIdx = blocks.indexOfFirst { it is Block.Paragraph }
    if (gfm && firstParagraphIdx != -1) {
        val para = blocks[firstParagraphIdx] as Block.Paragraph
        val raw = extractStubText(para.inlines)
        val taskResult = parseTaskMarker(raw)
        if (taskResult != null) {
            checked = taskResult.first
            val stripped = raw.substring(taskResult.second)
            val inlines = parseInlines(stripped, resolver)
            blocks[firstParagraphIdx] = Block.Paragraph(splitExtendedAutolinks(inlines))
            val remaining = blocks.mapIndexed { i, b ->
                if (i == firstParagraphIdx) b else resolveInlines(listOf(b), resolver, gfm).first()
            }
            return Block.ListItem(remaining, checked)
        }
    }

    return Block.ListItem(blocks.map { b ->
        resolveInlines(listOf(b), resolver, gfm).first()
    }, checked)
}

/**
 * Parses a task list marker at the start of [text].
 * Returns `(checked, endIndex)` or `null` if no valid marker is found.
 */
private fun parseTaskMarker(text: String): Pair<Boolean, Int>? {
    if (text.length < 4) return null
    if (text[0] != '[') return null
    val checked = when (text[1]) {
        ' ' -> false
        'x', 'X' -> true
        else -> return null
    }
    if (text[2] != ']') return null
    if (text.length < 4 || (text[3] != ' ' && text[3] != '\t')) return null
    return Pair(checked, 4)
}

/**
 * Extracts the raw text from stub inlines (Phase 2 output).
 * Paragraphs and headings initially contain a single Inline.Text with the raw text.
 */
private fun extractStubText(inlines: List<Inline>): String {
    if (inlines.isEmpty()) return ""
    return inlines.joinToString("") { inline ->
        when (inline) {
            is Inline.Text -> inline.literal
            else -> ""
        }
    }
}
