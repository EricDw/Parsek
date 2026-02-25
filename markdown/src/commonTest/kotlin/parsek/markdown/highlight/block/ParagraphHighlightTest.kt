package parsek.markdown.highlight.block

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.markdown.highlight.Span
import parsek.markdown.highlight.SpanSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ParagraphHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pParagraphHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun paragraphProducesNoBlockSpans() {
        val spans = highlight("Hello world\n")
        assertEquals(emptyList(), spans)
    }

    @Test
    fun multiLineParagraphProducesNoBlockSpans() {
        val spans = highlight("Hello\nworld\n")
        assertEquals(emptyList(), spans)
    }

    @Test
    fun failureOnBlankLine() {
        val sink = SpanSink()
        val input = ParserInput.of("\n".toList(), sink)
        val result = pParagraphHighlight()(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
