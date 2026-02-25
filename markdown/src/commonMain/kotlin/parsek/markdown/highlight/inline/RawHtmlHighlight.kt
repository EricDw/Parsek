package parsek.markdown.highlight.inline

import parsek.Parser
import parsek.markdown.ast.Inline
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.pTag
import parsek.markdown.parser.inline.pRawHtml

/**
 * Highlight wrapper for an inline raw HTML construct.
 *
 * The entire tag (including `<` and `>`) is tagged as [TokenType.HtmlInline].
 */
fun pRawHtmlHighlight(): Parser<Char, Inline, SpanSink> =
    pTag(TokenType.HtmlInline, pRawHtml())
