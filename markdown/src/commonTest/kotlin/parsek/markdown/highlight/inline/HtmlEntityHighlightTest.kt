package parsek.markdown.highlight.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.markdown.highlight.Span
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HtmlEntityHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pHtmlEntityHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun namedEntity() {
        val spans = highlight("&amp;")
        assertEquals(listOf(Span(TokenType.EntityRef, 0, 5)), spans)
    }

    @Test
    fun numericEntity() {
        val spans = highlight("&#42;")
        assertEquals(listOf(Span(TokenType.EntityRef, 0, 5)), spans)
    }

    @Test
    fun hexEntity() {
        val spans = highlight("&#x2A;")
        assertEquals(listOf(Span(TokenType.EntityRef, 0, 6)), spans)
    }

    @Test
    fun failureOnInvalid() {
        val sink = SpanSink()
        val input = ParserInput.of("&;".toList(), sink)
        val result = pHtmlEntityHighlight()(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
