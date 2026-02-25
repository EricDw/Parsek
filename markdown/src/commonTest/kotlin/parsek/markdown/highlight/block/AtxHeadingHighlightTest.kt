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

class AtxHeadingHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pAtxHeadingHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun simpleHeading() {
        // "# Hello\n"
        val spans = highlight("# Hello\n")
        assertEquals(
            listOf(
                Span(TokenType.HeadingMarker, 0, 1),  // #
                Span(TokenType.HeadingText, 2, 7),     // Hello
            ),
            spans,
        )
    }

    @Test
    fun level2Heading() {
        val spans = highlight("## World\n")
        assertEquals(
            listOf(
                Span(TokenType.HeadingMarker, 0, 2),  // ##
                Span(TokenType.HeadingText, 3, 8),     // World
            ),
            spans,
        )
    }

    @Test
    fun headingWithClosingHashes() {
        // "## foo ##\n"
        val spans = highlight("## foo ##\n")
        assertEquals(
            listOf(
                Span(TokenType.HeadingMarker, 0, 2),   // ##
                Span(TokenType.HeadingText, 3, 6),      // foo
                Span(TokenType.HeadingMarker, 7, 9),    // ##
            ),
            spans,
        )
    }

    @Test
    fun emptyHeading() {
        // "# \n" — empty heading, no text span
        val spans = highlight("# \n")
        assertEquals(
            listOf(Span(TokenType.HeadingMarker, 0, 1)),
            spans,
        )
    }

    @Test
    fun emptyHeadingAtEof() {
        val spans = highlight("#")
        assertEquals(
            listOf(Span(TokenType.HeadingMarker, 0, 1)),
            spans,
        )
    }

    @Test
    fun leadingSpaces() {
        // "  ## hi\n"
        val spans = highlight("  ## hi\n")
        assertEquals(
            listOf(
                Span(TokenType.HeadingMarker, 2, 4),  // ##
                Span(TokenType.HeadingText, 5, 7),     // hi
            ),
            spans,
        )
    }

    @Test
    fun failureProducesNoSpans() {
        val sink = SpanSink()
        val input = ParserInput.of("not a heading\n".toList(), sink)
        val result = pAtxHeadingHighlight()(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
