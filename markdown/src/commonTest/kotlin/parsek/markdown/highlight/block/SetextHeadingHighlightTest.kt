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

class SetextHeadingHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pSetextHeadingHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun level1Heading() {
        // "Foo\n===\n"
        val spans = highlight("Foo\n===\n")
        assertEquals(
            listOf(
                Span(TokenType.HeadingText, 0, 3),     // Foo
                Span(TokenType.HeadingMarker, 4, 7),   // ===
            ),
            spans,
        )
    }

    @Test
    fun level2Heading() {
        // "Bar\n---\n"
        val spans = highlight("Bar\n---\n")
        assertEquals(
            listOf(
                Span(TokenType.HeadingText, 0, 3),     // Bar
                Span(TokenType.HeadingMarker, 4, 7),   // ---
            ),
            spans,
        )
    }

    @Test
    fun multiLineContent() {
        // "Foo\nBar\n===\n"
        val spans = highlight("Foo\nBar\n===\n")
        assertEquals(
            listOf(
                Span(TokenType.HeadingText, 0, 7),      // Foo\nBar
                Span(TokenType.HeadingMarker, 8, 11),   // ===
            ),
            spans,
        )
    }

    @Test
    fun underlineWithLeadingSpaces() {
        // "Hi\n  --\n"
        val spans = highlight("Hi\n  --\n")
        assertEquals(
            listOf(
                Span(TokenType.HeadingText, 0, 2),     // Hi
                Span(TokenType.HeadingMarker, 3, 7),   // "  --"
            ),
            spans,
        )
    }

    @Test
    fun failureProducesNoSpans() {
        val sink = SpanSink()
        val input = ParserInput.of("not a heading\n".toList(), sink)
        val result = pSetextHeadingHighlight()(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
