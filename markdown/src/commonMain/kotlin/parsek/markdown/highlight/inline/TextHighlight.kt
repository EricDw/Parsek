package parsek.markdown.highlight.inline

import parsek.Parser
import parsek.markdown.ast.Inline
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.pTag
import parsek.markdown.parser.inline.pText

/**
 * Highlight wrapper for plain text runs.
 *
 * The consumed character range is tagged as [TokenType.Text].
 */
fun pTextHighlight(): Parser<Char, Inline, SpanSink> =
    pTag(TokenType.Text, pText())
