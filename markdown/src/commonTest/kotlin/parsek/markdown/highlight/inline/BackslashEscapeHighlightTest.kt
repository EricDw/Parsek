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

class BackslashEscapeHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pBackslashEscapeHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun escapedPunctuation() {
        val spans = highlight("\\*")
        assertEquals(listOf(Span(TokenType.EscapeSequence, 0, 2)), spans)
    }

    @Test
    fun escapedBracket() {
        val spans = highlight("\\[")
        assertEquals(listOf(Span(TokenType.EscapeSequence, 0, 2)), spans)
    }

    @Test
    fun failureOnNonPunctuation() {
        val sink = SpanSink()
        val input = ParserInput.of("\\a".toList(), sink)
        val result = pBackslashEscapeHighlight()(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
