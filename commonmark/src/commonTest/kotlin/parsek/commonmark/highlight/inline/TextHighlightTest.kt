package parsek.commonmark.highlight.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.highlight.Span
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TextHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pTextHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun plainText() {
        val spans = highlight("hello world")
        assertEquals(listOf(Span(TokenType.Text, 0, 11)), spans)
    }

    @Test
    fun singleSpecialChar() {
        // Special characters that no other parser handled are consumed one at a time.
        val spans = highlight("*")
        assertEquals(listOf(Span(TokenType.Text, 0, 1)), spans)
    }

    @Test
    fun failureAtEof() {
        val sink = SpanSink()
        val input = ParserInput.of("".toList(), sink)
        val result = pTextHighlight()(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
