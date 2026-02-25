package parsek.markdown.highlight.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.markdown.ast.Inline
import parsek.markdown.highlight.Span
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LinkHighlightTest {

    private val contentParser: (List<Char>, SpanSink) -> List<Inline> = { chars, _ ->
        if (chars.isEmpty()) emptyList()
        else listOf(Inline.Text(chars.joinToString("")))
    }

    private fun highlightLink(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pLinkHighlight(contentParser)(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    private fun highlightImage(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pImageHighlight(contentParser)(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    // -------------------------------------------------------------------------
    // Inline links
    // -------------------------------------------------------------------------

    @Test
    fun inlineLink() {
        // "[foo](/url)"
        val spans = highlightLink("[foo](/url)")
        assertEquals(
            listOf(
                Span(TokenType.LinkBracket, 0, 1),       // [
                Span(TokenType.LinkBracket, 4, 5),        // ]
                Span(TokenType.LinkParen, 5, 6),          // (
                Span(TokenType.LinkDestination, 6, 10),   // /url
                Span(TokenType.LinkParen, 10, 11),        // )
            ),
            spans,
        )
    }

    @Test
    fun inlineLinkWithTitle() {
        // "[foo](/url \"title\")"
        val spans = highlightLink("[foo](/url \"title\")")
        assertEquals(
            listOf(
                Span(TokenType.LinkBracket, 0, 1),        // [
                Span(TokenType.LinkBracket, 4, 5),         // ]
                Span(TokenType.LinkParen, 5, 6),           // (
                Span(TokenType.LinkDestination, 6, 10),    // /url
                Span(TokenType.LinkTitle, 11, 18),         // "title"
                Span(TokenType.LinkParen, 18, 19),         // )
            ),
            spans,
        )
    }

    @Test
    fun linkFailure() {
        val sink = SpanSink()
        val input = ParserInput.of("not a link".toList(), sink)
        val result = pLinkHighlight(contentParser)(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }

    // -------------------------------------------------------------------------
    // Images
    // -------------------------------------------------------------------------

    @Test
    fun inlineImage() {
        // "![alt](/url)"
        val spans = highlightImage("![alt](/url)")
        assertEquals(
            listOf(
                Span(TokenType.ImageMarker, 0, 1),        // !
                Span(TokenType.LinkBracket, 1, 2),        // [
                Span(TokenType.LinkBracket, 5, 6),         // ]
                Span(TokenType.LinkParen, 6, 7),           // (
                Span(TokenType.LinkDestination, 7, 11),    // /url
                Span(TokenType.LinkParen, 11, 12),         // )
            ),
            spans,
        )
    }

    @Test
    fun imageFailure() {
        val sink = SpanSink()
        val input = ParserInput.of("not an image".toList(), sink)
        val result = pImageHighlight(contentParser)(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
