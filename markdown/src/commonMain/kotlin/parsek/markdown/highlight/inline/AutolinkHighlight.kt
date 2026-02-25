package parsek.markdown.highlight.inline

import parsek.Parser
import parsek.Success
import parsek.markdown.ast.Inline
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.emit
import parsek.markdown.parser.inline.pAutolink

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
