package parsek.commonmark.highlight.inline

import parsek.Parser
import parsek.commonmark.ast.Inline
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import parsek.commonmark.highlight.pTag
import parsek.commonmark.parser.inline.pText

/**
 * Highlight wrapper for plain text runs.
 *
 * The consumed character range is tagged as [TokenType.Text].
 */
fun pTextHighlight(): Parser<Char, Inline, SpanSink> =
    pTag(TokenType.Text, pText())
