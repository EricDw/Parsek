package parsek.markdown.highlight.block

import parsek.ParserInput
import parsek.Success
import parsek.markdown.highlight.Span
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TableHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pTableHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun basicTable() {
        val spans = highlight("| a | b |\n| - | - |\n| c | d |\n")
        assertEquals(1, spans.size)
        assertEquals(TokenType.TableDelimiter, spans[0].type)
        assertEquals(0, spans[0].start)
    }

    @Test
    fun headerOnlyTable() {
        val spans = highlight("| a | b |\n| - | - |\n")
        assertEquals(1, spans.size)
        assertEquals(TokenType.TableDelimiter, spans[0].type)
    }
}
