package parsek.commonmark.highlight

import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.commonmark.ast.Document
import parsek.commonmark.ast.Inline
import parsek.commonmark.highlight.block.pAtxHeadingHighlight
import parsek.commonmark.highlight.block.pBlockQuoteHighlight
import parsek.commonmark.highlight.block.pFencedCodeBlockHighlight
import parsek.commonmark.highlight.block.pHtmlBlockHighlight
import parsek.commonmark.highlight.block.pIndentedCodeBlockHighlight
import parsek.commonmark.highlight.block.pLinkReferenceDefinitionHighlight
import parsek.commonmark.highlight.block.pListHighlight
import parsek.commonmark.highlight.block.pParagraphHighlight
import parsek.commonmark.highlight.block.pSetextHeadingHighlight
import parsek.commonmark.highlight.block.pThematicBreakHighlight
import parsek.commonmark.highlight.inline.DelimiterRecord
import parsek.commonmark.highlight.inline.emitEmphasisSpans
import parsek.commonmark.highlight.inline.pAutolinkHighlight
import parsek.commonmark.highlight.inline.pBackslashEscapeHighlight
import parsek.commonmark.highlight.inline.pCodeSpanHighlight
import parsek.commonmark.highlight.inline.pDelimiterRunHighlight
import parsek.commonmark.highlight.inline.pHtmlEntityHighlight
import parsek.commonmark.highlight.inline.pImageHighlight
import parsek.commonmark.highlight.inline.pLineBreakHighlight
import parsek.commonmark.highlight.inline.pLinkHighlight
import parsek.commonmark.highlight.inline.pRawHtmlHighlight
import parsek.commonmark.highlight.inline.pTextHighlight
import parsek.commonmark.parser.collectLinkRefDefs
import parsek.commonmark.parser.extractRawContent
import parsek.commonmark.parser.inline.EmphasisToken
import parsek.commonmark.parser.inline.LinkRefResolver
import parsek.commonmark.parser.inline.processEmphasis
import parsek.pChoice
import parsek.pLabel
import parsek.pMany
import parsek.pMap
import parsek.text.pBlankLine

// ---------------------------------------------------------------------------
// pBlockHighlight — ordered choice of all block parsers (highlight variants)
// ---------------------------------------------------------------------------

/**
 * Highlight variant of [parsek.commonmark.parser.pBlock].
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
 * Highlight variant of [parsek.commonmark.parser.inline.parseInlineContent].
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
 * Highlight variant of [parsek.commonmark.parser.resolveInlines].
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
            val inlines = parseInlineContentHighlight(raw.toList(), sink, resolveRef)
            Block.Paragraph(inlines)
        } else block
    }
    is Block.Heading -> {
        val raw = extractRawContent(block.inlines)
        if (raw != null) {
            val inlines = parseInlineContentHighlight(raw.toList(), sink, resolveRef)
            Block.Heading(block.level, inlines)
        } else block
    }
    is Block.BlockQuote ->
        Block.BlockQuote(block.blocks.map { resolveInlinesHighlight(it, resolveRef, sink) })
    is Block.BulletList ->
        Block.BulletList(
            block.tight, block.marker,
            block.items.map { item ->
                Block.ListItem(item.blocks.map { resolveInlinesHighlight(it, resolveRef, sink) })
            },
        )
    is Block.OrderedList ->
        Block.OrderedList(
            block.tight, block.start, block.delimiter,
            block.items.map { item ->
                Block.ListItem(item.blocks.map { resolveInlinesHighlight(it, resolveRef, sink) })
            },
        )
    is Block.ListItem ->
        Block.ListItem(block.blocks.map { resolveInlinesHighlight(it, resolveRef, sink) })
    else -> block
}

// ---------------------------------------------------------------------------
// pDocumentHighlight — top-level entry point
// ---------------------------------------------------------------------------

/**
 * Parses a complete CommonMark document while emitting all highlight spans
 * (block + inline) into a shared [SpanSink] user context.
 *
 * This is the highlight-aware counterpart of
 * [parsek.commonmark.parser.pDocument]. It uses the same two-pass design:
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
