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

class ListHighlightTest {

    /** A minimal block factory that only parses paragraphs. */
    private val blockFactory: () -> Parser<Char, Block, SpanSink> = {
        Parser { input ->
            when (val r = pParagraph<SpanSink>()(input)) {
                is Success -> Success(r.value as Block, r.nextIndex, r.input)
                is Failure -> r
            }
        }
    }

    // ---------------------------------------------------------------------
    // pListItemHighlight
    // ---------------------------------------------------------------------

    private fun highlightItem(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pListItemHighlight(blockFactory)(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun bulletItem() {
        // "- hello\n"
        val spans = highlightItem("- hello\n")
        assertEquals(
            listOf(Span(TokenType.ListMarker, 0, 1)),  // "-"
            spans,
        )
    }

    @Test
    fun orderedItem() {
        // "1. hello\n"
        val spans = highlightItem("1. hello\n")
        assertEquals(
            listOf(Span(TokenType.ListMarker, 0, 2)),  // "1."
            spans,
        )
    }

    @Test
    fun orderedItemWithParen() {
        // "1) hello\n"
        val spans = highlightItem("1) hello\n")
        assertEquals(
            listOf(Span(TokenType.ListMarker, 0, 2)),  // "1)"
            spans,
        )
    }

    @Test
    fun bulletItemWithLeadingSpaces() {
        // " - hello\n"
        val spans = highlightItem(" - hello\n")
        assertEquals(
            listOf(Span(TokenType.ListMarker, 1, 2)),  // "-"
            spans,
        )
    }

    @Test
    fun itemFailureProducesNoSpans() {
        val sink = SpanSink()
        val input = ParserInput.of("not a list\n".toList(), sink)
        val result = pListItemHighlight(blockFactory)(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }

    // ---------------------------------------------------------------------
    // pListHighlight
    // ---------------------------------------------------------------------

    private fun highlightList(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pListHighlight(blockFactory)(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun bulletList() {
        // "- a\n- b\n"
        val spans = highlightList("- a\n- b\n")
        assertEquals(
            listOf(
                Span(TokenType.ListMarker, 0, 1),  // "-"
                Span(TokenType.ListMarker, 4, 5),  // "-"
            ),
            spans,
        )
    }

    @Test
    fun orderedList() {
        // "1. a\n2. b\n"
        val spans = highlightList("1. a\n2. b\n")
        assertEquals(
            listOf(
                Span(TokenType.ListMarker, 0, 2),  // "1."
                Span(TokenType.ListMarker, 5, 7),  // "2."
            ),
            spans,
        )
    }

    @Test
    fun listWithMultiLineItem() {
        // "- line1\n  line2\n- item2\n"
        // continuation line "  line2" is NOT a new marker
        val spans = highlightList("- line1\n  line2\n- item2\n")
        assertEquals(
            listOf(
                Span(TokenType.ListMarker, 0, 1),   // "-"
                Span(TokenType.ListMarker, 16, 17),  // "-"
            ),
            spans,
        )
    }

    @Test
    fun listFailureProducesNoSpans() {
        val sink = SpanSink()
        val input = ParserInput.of("not a list\n".toList(), sink)
        val result = pListHighlight(blockFactory)(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
