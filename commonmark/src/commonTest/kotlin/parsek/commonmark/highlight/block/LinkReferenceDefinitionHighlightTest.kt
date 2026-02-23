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

class LinkReferenceDefinitionHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pLinkReferenceDefinitionHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun simpleDefinition() {
        // "[foo]: /url\n"
        val spans = highlight("[foo]: /url\n")
        assertEquals(
            listOf(
                Span(TokenType.LinkLabel, 0, 5),         // [foo]
                Span(TokenType.LinkDestination, 7, 11),  // /url
            ),
            spans,
        )
    }

    @Test
    fun definitionWithTitle() {
        // "[foo]: /url \"title\"\n"
        val spans = highlight("[foo]: /url \"title\"\n")
        assertEquals(
            listOf(
                Span(TokenType.LinkLabel, 0, 5),          // [foo]
                Span(TokenType.LinkDestination, 7, 11),   // /url
                Span(TokenType.LinkTitle, 12, 19),        // "title"
            ),
            spans,
        )
    }

    @Test
    fun angleBracketDestination() {
        // "[bar]: <http://example.com>\n"
        val spans = highlight("[bar]: <http://example.com>\n")
        assertEquals(
            listOf(
                Span(TokenType.LinkLabel, 0, 5),          // [bar]
                Span(TokenType.LinkDestination, 7, 27),   // <http://example.com>
            ),
            spans,
        )
    }

    @Test
    fun noTitle() {
        // "[baz]: /path\n"
        val spans = highlight("[baz]: /path\n")
        assertEquals(
            listOf(
                Span(TokenType.LinkLabel, 0, 5),          // [baz]
                Span(TokenType.LinkDestination, 7, 12),   // /path
            ),
            spans,
        )
    }

    @Test
    fun failureProducesNoSpans() {
        val sink = SpanSink()
        val input = ParserInput.of("not a link ref\n".toList(), sink)
        val result = pLinkReferenceDefinitionHighlight()(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
