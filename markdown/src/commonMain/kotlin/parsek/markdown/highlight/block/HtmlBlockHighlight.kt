package parsek.markdown.highlight.block

import parsek.Parser
import parsek.markdown.ast.Block
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.pTag
import parsek.markdown.parser.block.pHtmlBlock

/**
 * Highlight wrapper for a CommonMark HTML block.
 *
 * The entire consumed range is tagged as [TokenType.HtmlBlock].
 */
fun pHtmlBlockHighlight(): Parser<Char, Block.HtmlBlock, SpanSink> =
    pTag(TokenType.HtmlBlock, pHtmlBlock())
