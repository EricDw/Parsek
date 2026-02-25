package parsek.markdown.highlight.inline

import parsek.Parser
import parsek.markdown.ast.Inline
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.pTag
import parsek.markdown.parser.inline.pHtmlEntity

/**
 * Highlight wrapper for a CommonMark HTML entity reference.
 *
 * The entire `&…;` range is tagged as [TokenType.EntityRef].
 */
fun pHtmlEntityHighlight(): Parser<Char, Inline, SpanSink> =
    pTag(TokenType.EntityRef, pHtmlEntity())
