package parsek.markdown.highlight.block

import parsek.Parser
import parsek.markdown.ast.Block
import parsek.markdown.highlight.SpanSink
import parsek.markdown.parser.block.pParagraph

/**
 * Highlight wrapper for a CommonMark paragraph.
 *
 * Paragraphs have no block-level highlight spans — inline highlighting is
 * handled separately. This wrapper is a passthrough to [pParagraph].
 */
fun pParagraphHighlight(): Parser<Char, Block.Paragraph, SpanSink> =
    pParagraph()
