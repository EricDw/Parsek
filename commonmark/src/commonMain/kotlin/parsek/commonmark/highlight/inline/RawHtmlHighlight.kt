package parsek.commonmark.highlight.inline

import parsek.Parser
import parsek.commonmark.ast.Inline
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import parsek.commonmark.highlight.pTag
import parsek.commonmark.parser.inline.pRawHtml

/**
 * Highlight wrapper for an inline raw HTML construct.
 *
 * The entire tag (including `<` and `>`) is tagged as [TokenType.HtmlInline].
 */
fun pRawHtmlHighlight(): Parser<Char, Inline, SpanSink> =
    pTag(TokenType.HtmlInline, pRawHtml())
