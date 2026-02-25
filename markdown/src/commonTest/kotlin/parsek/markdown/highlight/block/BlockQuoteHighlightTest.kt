package parsek.markdown.highlight.block

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.markdown.ast.Block
import parsek.markdown.highlight.Span
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.parser.block.pParagraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BlockQuoteHighlightTest {

    /** A minimal block factory that only parses paragraphs. */
    private val blockFactory: () -> Parser<Char, Block, SpanSink> = {
        Parser { input ->
            when (val r = pParagraph<SpanSink>()(input)) {
                is Success -> Success(r.value as Block, r.nextIndex, r.input)
                is Failure -> r
            }
        }
    }

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pBlockQuoteHighlight(blockFactory)(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun singleLineBlockQuote() {
        // "> hello\n"
        val spans = highlight("> hello\n")
        assertEquals(
            listOf(Span(TokenType.BlockQuoteMarker, 0, 2)),  // "> "
            spans,
        )
    }

    @Test
    fun multiLineBlockQuote() {
        // "> line1\n> line2\n"
        val spans = highlight("> line1\n> line2\n")
        assertEquals(
            listOf(
                Span(TokenType.BlockQuoteMarker, 0, 2),   // "> "
                Span(TokenType.BlockQuoteMarker, 8, 10),  // "> "
            ),
            spans,
        )
    }

    @Test
    fun markerWithLeadingSpaces() {
        // "  > hello\n"
        val spans = highlight("  > hello\n")
        assertEquals(
            listOf(Span(TokenType.BlockQuoteMarker, 0, 4)),  // "  > "
            spans,
        )
    }

    @Test
    fun markerWithoutSpace() {
        // ">hello\n"
        val spans = highlight(">hello\n")
        assertEquals(
            listOf(Span(TokenType.BlockQuoteMarker, 0, 1)),  // ">"
            spans,
        )
    }

    @Test
    fun failureProducesNoSpans() {
        val sink = SpanSink()
        val input = ParserInput.of("no quote\n".toList(), sink)
        val result = pBlockQuoteHighlight(blockFactory)(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
