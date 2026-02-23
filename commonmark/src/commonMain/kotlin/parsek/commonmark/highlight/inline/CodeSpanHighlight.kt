package parsek.commonmark.highlight.inline

import parsek.Parser
import parsek.Success
import parsek.commonmark.ast.Inline
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import parsek.commonmark.highlight.emit
import parsek.commonmark.parser.inline.pCodeSpan

/**
 * Highlight wrapper for a CommonMark code span.
 *
 * On success with an actual [Inline.CodeSpan], emits:
 * - [TokenType.CodeSpanDelimiter] for the opening backtick run
 * - [TokenType.CodeSpanContent] for the body between delimiters
 * - [TokenType.CodeSpanDelimiter] for the closing backtick run
 *
 * When the parser returns [Inline.Text] (unmatched backtick run), no
 * highlight spans are emitted.
 */
fun pCodeSpanHighlight(): Parser<Char, Inline, SpanSink> =
    Parser { input ->
        val start = input.index
        val result = pCodeSpan<SpanSink>()(input)
        if (result !is Success) return@Parser result

        // Only emit spans for actual code spans, not fallback Text nodes.
        if (result.value !is Inline.CodeSpan) return@Parser result

        val chars = input.input
        val sink = input.userContext

        // Count opening backtick run.
        var idx = start
        while (idx < chars.size && chars[idx] == '`') idx++
        val n = idx - start

        sink.emit(TokenType.CodeSpanDelimiter, start, idx)

        // Content: everything between opening and closing backtick runs.
        val contentStart = idx
        val contentEnd = result.nextIndex - n
        sink.emit(TokenType.CodeSpanContent, contentStart, contentEnd)

        // Closing backtick run.
        sink.emit(TokenType.CodeSpanDelimiter, contentEnd, result.nextIndex)

        result
    }
