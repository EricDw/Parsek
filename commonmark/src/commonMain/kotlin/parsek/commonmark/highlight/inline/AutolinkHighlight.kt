package parsek.commonmark.highlight.inline

import parsek.Parser
import parsek.Success
import parsek.commonmark.ast.Inline
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import parsek.commonmark.highlight.emit
import parsek.commonmark.parser.inline.pAutolink

/**
 * Highlight wrapper for a CommonMark autolink.
 *
 * On success, emits [TokenType.AutolinkUrl] over the URI/email address,
 * excluding the surrounding `<` and `>` angle brackets.
 */
fun pAutolinkHighlight(): Parser<Char, Inline, SpanSink> =
    Parser { input ->
        val start = input.index
        val result = pAutolink<SpanSink>()(input)
        if (result !is Success) return@Parser result

        val sink = input.userContext
        // The autolink consumes `<url>`, so the URL is at [start+1, end-1).
        sink.emit(TokenType.AutolinkUrl, start + 1, result.nextIndex - 1)

        result
    }
