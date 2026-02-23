package parsek.commonmark.highlight.inline

import parsek.Parser
import parsek.commonmark.ast.Inline
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import parsek.commonmark.highlight.pTag
import parsek.commonmark.parser.inline.pHtmlEntity

/**
 * Highlight wrapper for a CommonMark HTML entity reference.
 *
 * The entire `&…;` range is tagged as [TokenType.EntityRef].
 */
fun pHtmlEntityHighlight(): Parser<Char, Inline, SpanSink> =
    pTag(TokenType.EntityRef, pHtmlEntity())
