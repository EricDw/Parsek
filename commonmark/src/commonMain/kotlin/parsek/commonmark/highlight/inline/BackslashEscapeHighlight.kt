package parsek.commonmark.highlight.inline

import parsek.Parser
import parsek.commonmark.ast.Inline
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import parsek.commonmark.highlight.pTag
import parsek.commonmark.parser.inline.pBackslashEscape

/**
 * Highlight wrapper for a CommonMark backslash escape.
 *
 * The entire `\` + punctuation character range is tagged as [TokenType.EscapeSequence].
 */
fun pBackslashEscapeHighlight(): Parser<Char, Inline, SpanSink> =
    pTag(TokenType.EscapeSequence, pBackslashEscape())
