package parsek.commonmark.highlight.block

import parsek.Parser
import parsek.commonmark.ast.Block
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import parsek.commonmark.highlight.pTag
import parsek.commonmark.parser.block.pHtmlBlock

/**
 * Highlight wrapper for a CommonMark HTML block.
 *
 * The entire consumed range is tagged as [TokenType.HtmlBlock].
 */
fun pHtmlBlockHighlight(): Parser<Char, Block.HtmlBlock, SpanSink> =
    pTag(TokenType.HtmlBlock, pHtmlBlock())
