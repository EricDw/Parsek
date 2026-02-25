package parsek.markdown.highlight.inline

import parsek.Parser
import parsek.markdown.ast.Inline
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.pTag
import parsek.markdown.parser.inline.pBackslashEscape

/**
 * Highlight wrapper for a CommonMark backslash escape.
 *
 * The entire `\` + punctuation character range is tagged as [TokenType.EscapeSequence].
 */
fun pBackslashEscapeHighlight(): Parser<Char, Inline, SpanSink> =
    pTag(TokenType.EscapeSequence, pBackslashEscape())
