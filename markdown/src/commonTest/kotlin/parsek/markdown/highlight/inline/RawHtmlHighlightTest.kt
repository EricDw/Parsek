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

class RawHtmlHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pRawHtmlHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun openTag() {
        val spans = highlight("<em>")
        assertEquals(listOf(Span(TokenType.HtmlInline, 0, 4)), spans)
    }

    @Test
    fun closeTag() {
        val spans = highlight("</em>")
        assertEquals(listOf(Span(TokenType.HtmlInline, 0, 5)), spans)
    }

    @Test
    fun selfClosingTag() {
        val spans = highlight("<br />")
        assertEquals(listOf(Span(TokenType.HtmlInline, 0, 6)), spans)
    }

    @Test
    fun htmlComment() {
        val spans = highlight("<!-- comment -->")
        assertEquals(listOf(Span(TokenType.HtmlInline, 0, 16)), spans)
    }

    @Test
    fun failureOnInvalid() {
        val sink = SpanSink()
        val input = ParserInput.of("< not html>".toList(), sink)
        val result = pRawHtmlHighlight()(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
