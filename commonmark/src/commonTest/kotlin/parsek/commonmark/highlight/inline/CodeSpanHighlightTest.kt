package parsek.commonmark.highlight.inline

import parsek.ParserInput
import parsek.Success
import parsek.commonmark.highlight.Span
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CodeSpanHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pCodeSpanHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun simpleCodeSpan() {
        // "`code`"
        val spans = highlight("`code`")
        assertEquals(
            listOf(
                Span(TokenType.CodeSpanDelimiter, 0, 1),  // `
                Span(TokenType.CodeSpanContent, 1, 5),    // code
                Span(TokenType.CodeSpanDelimiter, 5, 6),  // `
            ),
            spans,
        )
    }

    @Test
    fun doubleBacktickCodeSpan() {
        // "``code``"
        val spans = highlight("``code``")
        assertEquals(
            listOf(
                Span(TokenType.CodeSpanDelimiter, 0, 2),  // ``
                Span(TokenType.CodeSpanContent, 2, 6),    // code
                Span(TokenType.CodeSpanDelimiter, 6, 8),  // ``
            ),
            spans,
        )
    }

    @Test
    fun emptyCodeSpan() {
        // "``"  — no closing run, returns Text, no spans
        val sink = SpanSink()
        val input = ParserInput.of("``".toList(), sink)
        val result = pCodeSpanHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }

    @Test
    fun codeSpanWithBackticksInside() {
        // "`` `foo` ``" — double backtick span with single backticks inside
        val spans = highlight("`` `foo` ``")
        assertEquals(
            listOf(
                Span(TokenType.CodeSpanDelimiter, 0, 2),   // ``
                Span(TokenType.CodeSpanContent, 2, 9),     // " `foo` "
                Span(TokenType.CodeSpanDelimiter, 9, 11),  // ``
            ),
            spans,
        )
    }
}
