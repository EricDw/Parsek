package parsek.commonmark.highlight.block

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.highlight.Span
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ThematicBreakHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pThematicBreakHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun simpleDashes() {
        val spans = highlight("---\n")
        assertEquals(listOf(Span(TokenType.ThematicBreak, 0, 4)), spans)
    }

    @Test
    fun asterisksWithSpaces() {
        val spans = highlight("* * *\n")
        assertEquals(listOf(Span(TokenType.ThematicBreak, 0, 6)), spans)
    }

    @Test
    fun underscoresNoTrailingNewline() {
        val spans = highlight("___")
        assertEquals(listOf(Span(TokenType.ThematicBreak, 0, 3)), spans)
    }

    @Test
    fun leadingSpaces() {
        val spans = highlight("   ---\n")
        assertEquals(listOf(Span(TokenType.ThematicBreak, 0, 7)), spans)
    }

    @Test
    fun failureProducesNoSpans() {
        val sink = SpanSink()
        val input = ParserInput.of("hello\n".toList(), sink)
        val result = pThematicBreakHighlight()(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
