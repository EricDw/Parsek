package parsek.markdown.highlight.block

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.markdown.highlight.Span
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HtmlBlockHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pHtmlBlockHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun simpleDivBlock() {
        val spans = highlight("<div>\nhello\n</div>\n")
        assertEquals(listOf(Span(TokenType.HtmlBlock, 0, 19)), spans)
    }

    @Test
    fun preBlock() {
        val spans = highlight("<pre>\ncode\n</pre>\n")
        assertEquals(listOf(Span(TokenType.HtmlBlock, 0, 18)), spans)
    }

    @Test
    fun htmlComment() {
        val spans = highlight("<!-- comment -->\n")
        assertEquals(listOf(Span(TokenType.HtmlBlock, 0, 17)), spans)
    }

    @Test
    fun failureProducesNoSpans() {
        val sink = SpanSink()
        val input = ParserInput.of("just text\n".toList(), sink)
        val result = pHtmlBlockHighlight()(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
