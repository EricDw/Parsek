package parsek.markdown.highlight

import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.markdown.ast.Block
import parsek.markdown.ast.Document
import parsek.markdown.ast.Inline
import parsek.markdown.highlight.block.pAtxHeadingHighlight
import parsek.markdown.highlight.block.pBlockQuoteHighlight
import parsek.markdown.highlight.block.pFencedCodeBlockHighlight
import parsek.markdown.highlight.block.pHtmlBlockHighlight
import parsek.markdown.highlight.block.pIndentedCodeBlockHighlight
import parsek.markdown.highlight.block.pLinkReferenceDefinitionHighlight
import parsek.markdown.highlight.block.pListHighlight
import parsek.markdown.highlight.block.pParagraphHighlight
import parsek.markdown.highlight.block.pSetextHeadingHighlight
import parsek.markdown.highlight.block.pTableHighlight
import parsek.markdown.highlight.block.pThematicBreakHighlight
import parsek.markdown.highlight.inline.DelimiterRecord
import parsek.markdown.highlight.inline.emitEmphasisSpans
import parsek.markdown.highlight.inline.emitExtendedAutolinkSpans
import parsek.markdown.highlight.inline.emitStrikethroughSpans
import parsek.markdown.highlight.inline.pAutolinkHighlight
import parsek.markdown.highlight.inline.pBackslashEscapeHighlight
import parsek.markdown.highlight.inline.pCodeSpanHighlight
import parsek.markdown.highlight.inline.pDelimiterRunHighlight
import parsek.markdown.highlight.inline.pHtmlEntityHighlight
import parsek.markdown.highlight.inline.pImageHighlight
import parsek.markdown.highlight.inline.pLineBreakHighlight
import parsek.markdown.highlight.inline.pLinkHighlight
import parsek.markdown.highlight.inline.pRawHtmlHighlight
import parsek.markdown.highlight.inline.pTextHighlight
import parsek.markdown.parser.collectLinkRefDefs
import parsek.markdown.parser.extractRawContent
import parsek.markdown.parser.inline.EmphasisToken
import parsek.markdown.parser.inline.LinkRefResolver
import parsek.markdown.parser.inline.processEmphasis
import parsek.markdown.parser.inline.splitExtendedAutolinks
import parsek.pChoice
import parsek.pLabel
import parsek.pMany
import parsek.pMap
import parsek.text.pBlankLine

// ---------------------------------------------------------------------------
// pBlockHighlight — ordered choice of all block parsers (highlight variants)
// ---------------------------------------------------------------------------

/**
 * Highlight variant of [parsek.markdown.parser.pBlock].
 *
 * Mirrors the same precedence order but substitutes each block parser with
 * its highlight wrapper so that block-level spans are emitted into a
 * [SpanSink] user context.
 */
fun pBlockHighlight(): Parser<Char, Block, SpanSink> =
    pLabel(
        pChoice(
            pMap(pBlankLine<SpanSink>()) { Block.BlankLine },
            pThematicBreakHighlight(),
            pAtxHeadingHighlight(),
            pFencedCodeBlockHighlight(),
            pHtmlBlockHighlight(),
            pLinkReferenceDefinitionHighlight(),
            pTableHighlight(),
            pBlockQuoteHighlight { pBlockHighlight() },
            pListHighlight { pBlockHighlight() },
            pIndentedCodeBlockHighlight(),
            pSetextHeadingHighlight(),
            pParagraphHighlight(),
        ),
        "block",
    )

// ---------------------------------------------------------------------------
// parseInlineContentHighlight — highlight-aware inline re-parsing
// ---------------------------------------------------------------------------

/**
 * Highlight variant of [parsek.markdown.parser.inline.parseInlineContent].
 *
 * Parses [chars] as inline content using highlight inline parsers, emitting
 * inline-level spans into [sink]. Inline positions are 0-based relative to
 * the character list (not absolute document positions).
 */
internal fun parseInlineContentHighlight(
    chars: List<Char>,
    sink: SpanSink,
    resolveRef: LinkRefResolver,
): List<Inline> {
    if (chars.isEmpty()) return emptyList()
    val records = mutableListOf<DelimiterRecord>()
    val innerInput = ParserInput(chars, 0, sink)
    val result = pMany(pInlineTokenHighlight(resolveRef, records))(innerInput)
    return if (result is Success) {
        val tokens = result.value
        val inlines = processEmphasis(tokens)
        emitEmphasisSpans(tokens, inlines, records, sink)
        emitStrikethroughSpans(records, inlines, sink)
        inlines
    } else emptyList()
}

// ---------------------------------------------------------------------------
// pInlineTokenHighlight — single inline token parser (highlight variant)
// ---------------------------------------------------------------------------

/**
 * Highlight variant of the inline token parser.
 *
 * Mirrors the same precedence order but substitutes each inline parser with
 * its highlight wrapper.
 */
private fun pInlineTokenHighlight(
    resolveRef: LinkRefResolver,
    records: MutableList<DelimiterRecord>,
): Parser<Char, EmphasisToken, SpanSink> {
    val contentParser: (List<Char>, SpanSink) -> List<Inline> = { chars, ctx ->
        parseInlineContentHighlight(chars, ctx, resolveRef)
    }

    return pChoice(
        pMap(pBackslashEscapeHighlight()) { EmphasisToken.Content(it) },
        pMap(pHtmlEntityHighlight()) { EmphasisToken.Content(it) },
        pMap(pCodeSpanHighlight()) { EmphasisToken.Content(it) },
        pMap(pAutolinkHighlight()) { EmphasisToken.Content(it) },
        pMap(pRawHtmlHighlight()) { EmphasisToken.Content(it) },
        pMap(pLineBreakHighlight()) { EmphasisToken.Content(it) },
        pMap(pImageHighlight(contentParser, resolveRef)) { EmphasisToken.Content(it) },
        pMap(pLinkHighlight(contentParser, resolveRef)) { EmphasisToken.Content(it) },
        pDelimiterRunHighlight(records),
        pMap(pTextHighlight()) { EmphasisToken.Content(it) },
    )
}

// ---------------------------------------------------------------------------
// resolveInlinesHighlight — inline resolution with highlight spans
// ---------------------------------------------------------------------------

/**
 * Highlight variant of [parsek.markdown.parser.resolveInlines].
 *
 * Recursively walks the block tree, re-parsing inline content in paragraphs
 * and headings using [parseInlineContentHighlight] to emit inline spans.
 */
private fun resolveInlinesHighlight(
    block: Block,
    resolveRef: LinkRefResolver,
    sink: SpanSink,
): Block = when (block) {
    is Block.Paragraph -> {
        val raw = extractRawContent(block.inlines)
        if (raw != null) {
            val parsed = parseInlineContentHighlight(raw.toList(), sink, resolveRef)
            val inlines = splitExtendedAutolinks(parsed)
            emitExtendedAutolinkSpans(inlines, sink)
            Block.Paragraph(inlines)
        } else block
    }
    is Block.Heading -> {
        val raw = extractRawContent(block.inlines)
        if (raw != null) {
            val parsed = parseInlineContentHighlight(raw.toList(), sink, resolveRef)
            val inlines = splitExtendedAutolinks(parsed)
            emitExtendedAutolinkSpans(inlines, sink)
            Block.Heading(block.level, inlines)
        } else block
    }
    is Block.BlockQuote ->
        Block.BlockQuote(block.blocks.map { resolveInlinesHighlight(it, resolveRef, sink) })
    is Block.BulletList ->
        Block.BulletList(
            block.tight, block.marker,
            block.items.map { resolveListItemHighlight(it, resolveRef, sink) },
        )
    is Block.OrderedList ->
        Block.OrderedList(
            block.tight, block.start, block.delimiter,
            block.items.map { resolveListItemHighlight(it, resolveRef, sink) },
        )
    is Block.ListItem ->
        resolveListItemHighlight(block, resolveRef, sink)
    is Block.Table ->
        Block.Table(
            block.alignments,
            block.header,
            block.body,
        )
    is Block.TableRow -> block
    is Block.TableCell -> block
    else -> block
}

// ---------------------------------------------------------------------------
// resolveListItemHighlight — task list marker detection + highlight
// ---------------------------------------------------------------------------

/**
 * Highlight variant of [parsek.markdown.parser.resolveListItem].
 *
 * Detects a GFM task list marker (`[ ]` or `[x]`) at the start of the first
 * paragraph's raw text. If found, emits a [TokenType.TaskMarker] span and
 * strips the marker before inline parsing.
 */
private fun resolveListItemHighlight(
    item: Block.ListItem,
    resolveRef: LinkRefResolver,
    sink: SpanSink,
): Block.ListItem {
    var checked: Boolean? = item.checked
    val blocks = item.blocks.toMutableList()

    val firstParagraphIdx = blocks.indexOfFirst { it is Block.Paragraph }
    if (firstParagraphIdx != -1) {
        val para = blocks[firstParagraphIdx] as Block.Paragraph
        val raw = extractRawContent(para.inlines)
        if (raw != null) {
            val taskResult = parseTaskMarkerHighlight(raw)
            if (taskResult != null) {
                checked = taskResult.first
                val markerLen = taskResult.second
                // Emit a TaskMarker span for the `[ ]` or `[x]` part (3 chars).
                sink.emit(TokenType.TaskMarker, 0, 3)
                // Strip marker and parse remaining inline content.
                val stripped = raw.substring(markerLen)
                val parsed = parseInlineContentHighlight(stripped.toList(), sink, resolveRef)
                val inlines = splitExtendedAutolinks(parsed)
                emitExtendedAutolinkSpans(inlines, sink)
                blocks[firstParagraphIdx] = Block.Paragraph(inlines)
                val remaining = blocks.mapIndexed { i, b ->
                    if (i == firstParagraphIdx) b else resolveInlinesHighlight(b, resolveRef, sink)
                }
                return Block.ListItem(remaining, checked)
            }
        }
    }

    return Block.ListItem(
        blocks.map { resolveInlinesHighlight(it, resolveRef, sink) },
        checked,
    )
}

/**
 * Parses a task list marker at the start of [text].
 * Returns `(checked, endIndex)` or `null` if no valid marker found.
 */
private fun parseTaskMarkerHighlight(text: String): Pair<Boolean, Int>? {
    if (text.length < 4) return null
    if (text[0] != '[') return null
    val isChecked = when (text[1]) {
        ' ' -> false
        'x', 'X' -> true
        else -> return null
    }
    if (text[2] != ']') return null
    if (text[3] != ' ' && text[3] != '\t') return null
    return Pair(isChecked, 4)
}

// ---------------------------------------------------------------------------
// pDocumentHighlight — top-level entry point
// ---------------------------------------------------------------------------

/**
 * Parses a complete CommonMark document while emitting all highlight spans
 * (block + inline) into a shared [SpanSink] user context.
 *
 * This is the highlight-aware counterpart of
 * [parsek.markdown.parser.pDocument]. It uses the same two-pass design:
 *
 * 1. **Block pass**: parse blocks using [pBlockHighlight], emitting
 *    block-level spans at absolute document positions.
 *
 * 2. **Inline pass**: re-parse inline content in paragraphs and headings
 *    using [parseInlineContentHighlight], emitting inline spans at 0-based
 *    positions relative to each paragraph/heading's raw content.
 *
 * @return a [Parser] that succeeds with a [Document] and has populated the
 *   [SpanSink] with all highlight spans.
 */
fun pDocumentHighlight(): Parser<Char, Document, SpanSink> =
    pLabel(
        Parser { input ->
            val blockResult = pMany(pBlockHighlight())(input) as Success
            val blocks = blockResult.value
            val sink = input.userContext

            val refMap = mutableMapOf<String, Pair<String, String?>>()
            for (block in blocks) {
                collectLinkRefDefs(block, refMap)
            }

            val resolveRef: (String) -> Pair<String, String?>? = { label ->
                refMap[label]
            }

            val processed = blocks
                .map { block -> resolveInlinesHighlight(block, resolveRef, sink) }
                .filter { it !is Block.BlankLine && it !is Block.LinkReferenceDefinition }

            Success(Document(processed), blockResult.nextIndex, blockResult.input)
        },
        "document",
    )
