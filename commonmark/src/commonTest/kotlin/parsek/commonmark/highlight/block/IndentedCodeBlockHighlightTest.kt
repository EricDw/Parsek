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

class IndentedCodeBlockHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pIndentedCodeBlockHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun singleLine() {
        // "    code\n"
        val spans = highlight("    code\n")
        assertEquals(
            listOf(Span(TokenType.CodeContent, 0, 9)),
            spans,
        )
    }

    @Test
    fun multipleLines() {
        // "    line1\n    line2\n"
        val spans = highlight("    line1\n    line2\n")
        assertEquals(
            listOf(
                Span(TokenType.CodeContent, 0, 10),
                Span(TokenType.CodeContent, 10, 20),
            ),
            spans,
        )
    }

    @Test
    fun withBlankInterstitialLine() {
        // "    a\n\n    b\n"
        val spans = highlight("    a\n\n    b\n")
        assertEquals(
            listOf(
                Span(TokenType.CodeContent, 0, 6),   // "    a\n"
                Span(TokenType.CodeContent, 6, 7),    // "\n"
                Span(TokenType.CodeContent, 7, 13),   // "    b\n"
            ),
            spans,
        )
    }

    @Test
    fun failureProducesNoSpans() {
        val sink = SpanSink()
        val input = ParserInput.of("no indent\n".toList(), sink)
        val result = pIndentedCodeBlockHighlight()(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
