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
fun parseDocument(text: String): Document {
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

    val resolved = resolveInlines(cleanedBlocks, resolver)
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
        else -> block
    }
}

/**
 * Recursively walks the block tree and replaces stub [Inline.Text] nodes
 * with fully parsed inline content using [parseInlines].
 */
private fun resolveInlines(blocks: List<Block>, resolver: LinkRefResolver?): List<Block> = blocks.map { block ->
    when (block) {
        is Block.Paragraph -> {
            val text = extractStubText(block.inlines)
            Block.Paragraph(parseInlines(text, resolver))
        }
        is Block.Heading -> {
            val text = extractStubText(block.inlines)
            Block.Heading(block.level, parseInlines(text, resolver))
        }
        is Block.BlockQuote -> Block.BlockQuote(resolveInlines(block.blocks, resolver))
        is Block.BulletList -> Block.BulletList(
            block.tight,
            block.marker,
            block.items.map { Block.ListItem(resolveInlines(it.blocks, resolver), it.checked) },
        )
        is Block.OrderedList -> Block.OrderedList(
            block.tight,
            block.start,
            block.delimiter,
            block.items.map { Block.ListItem(resolveInlines(it.blocks, resolver), it.checked) },
        )
        is Block.ListItem -> Block.ListItem(resolveInlines(block.blocks, resolver), block.checked)
        else -> block // ThematicBreak, CodeBlock, HtmlBlock, etc. — no inline content
    }
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
