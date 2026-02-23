package parsek.commonmark.highlight.block

import parsek.Parser
import parsek.commonmark.ast.Block
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.parser.block.pParagraph

/**
 * Highlight wrapper for a CommonMark paragraph.
 *
 * Paragraphs have no block-level highlight spans — inline highlighting is
 * handled separately. This wrapper is a passthrough to [pParagraph].
 */
fun pParagraphHighlight(): Parser<Char, Block.Paragraph, SpanSink> =
    pParagraph()
