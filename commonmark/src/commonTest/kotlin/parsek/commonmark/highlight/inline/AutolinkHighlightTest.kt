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

class AutolinkHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pAutolinkHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun uriAutolink() {
        // "<http://example.com>"
        val spans = highlight("<http://example.com>")
        assertEquals(
            listOf(Span(TokenType.AutolinkUrl, 1, 19)),  // http://example.com
            spans,
        )
    }

    @Test
    fun emailAutolink() {
        // "<foo@bar.com>"
        val spans = highlight("<foo@bar.com>")
        assertEquals(
            listOf(Span(TokenType.AutolinkUrl, 1, 12)),  // foo@bar.com
            spans,
        )
    }

    @Test
    fun failureOnInvalid() {
        val sink = SpanSink()
        val input = ParserInput.of("<not an autolink".toList(), sink)
        val result = pAutolinkHighlight()(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
