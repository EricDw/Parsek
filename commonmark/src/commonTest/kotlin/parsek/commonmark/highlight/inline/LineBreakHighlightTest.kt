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

class LineBreakHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pLineBreakHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun hardBreakWithSpaces() {
        // "  \n" — two spaces + newline
        val spans = highlight("  \n")
        assertEquals(listOf(Span(TokenType.HardBreak, 0, 3)), spans)
    }

    @Test
    fun hardBreakWithBackslash() {
        // "\\\n"
        val spans = highlight("\\\n")
        assertEquals(listOf(Span(TokenType.HardBreak, 0, 2)), spans)
    }

    @Test
    fun softBreak() {
        // "\n"
        val spans = highlight("\n")
        assertEquals(listOf(Span(TokenType.SoftBreak, 0, 1)), spans)
    }

    @Test
    fun softBreakWithOneSpace() {
        // " \n" — one space + newline = soft break
        val spans = highlight(" \n")
        assertEquals(listOf(Span(TokenType.SoftBreak, 0, 2)), spans)
    }

    @Test
    fun failureOnNoNewline() {
        val sink = SpanSink()
        val input = ParserInput.of("hello".toList(), sink)
        val result = pLineBreakHighlight()(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
