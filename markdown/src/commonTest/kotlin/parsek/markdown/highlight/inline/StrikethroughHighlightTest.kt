package parsek.markdown.highlight.inline

import parsek.ParserInput
import parsek.Success
import parsek.markdown.highlight.Span
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.parseInlineContentHighlight
import parsek.markdown.parser.inline.parseInlineContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrikethroughHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        parseInlineContentHighlight(s.toList(), sink) { null }
        return sink.spans
    }

    @Test
    fun basicStrikethrough() {
        val spans = highlight("~~Hi~~")
        val markers = spans.filter { it.type == TokenType.StrikethroughMarker }
        assertTrue(markers.isNotEmpty(), "Expected StrikethroughMarker spans")
    }

    @Test
    fun noStrikethroughNoSpans() {
        val spans = highlight("hello world")
        val markers = spans.filter { it.type == TokenType.StrikethroughMarker }
        assertEquals(0, markers.size)
    }

    @Test
    fun singleTildeNoSpans() {
        val spans = highlight("~hello~")
        val markers = spans.filter { it.type == TokenType.StrikethroughMarker }
        assertEquals(0, markers.size)
    }
}
