package parsek.commonmark.highlight.inline

import parsek.Parser
import parsek.Success
import parsek.commonmark.ast.Inline
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import parsek.commonmark.highlight.emit
import parsek.commonmark.parser.inline.pLineBreak

/**
 * Highlight wrapper for a CommonMark line break.
 *
 * On success, emits either:
 * - [TokenType.HardBreak] for a hard break (2+ spaces or `\` before newline)
 * - [TokenType.SoftBreak] for a soft break (plain newline)
 *
 * The span covers the entire consumed range (triggering whitespace + line ending).
 */
fun pLineBreakHighlight(): Parser<Char, Inline, SpanSink> =
    Parser { input ->
        val start = input.index
        val result = pLineBreak<SpanSink>()(input)
        if (result !is Success) return@Parser result

        val sink = input.userContext
        val type = when (result.value) {
            is Inline.HardBreak -> TokenType.HardBreak
            else -> TokenType.SoftBreak
        }
        sink.emit(type, start, result.nextIndex)

        result
    }
