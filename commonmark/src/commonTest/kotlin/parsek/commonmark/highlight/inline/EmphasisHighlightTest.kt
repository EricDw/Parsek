package parsek.commonmark.highlight.inline

import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Inline
import parsek.commonmark.highlight.Span
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import parsek.commonmark.parser.inline.EmphasisToken
import parsek.commonmark.parser.inline.processEmphasis
import parsek.pChoice
import parsek.pMany
import parsek.pMap
import kotlin.test.Test
import kotlin.test.assertEquals

class EmphasisHighlightTest {

    /**
     * Tokenizes a string using delimiter run tracking and text fallback,
     * processes emphasis, and emits spans.
     */
    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val records = mutableListOf<DelimiterRecord>()
        val input = ParserInput.of(s.toList(), sink)

        val tokenParser = pChoice(
            pDelimiterRunHighlight(records),
            pMap(parsek.commonmark.parser.inline.pText<SpanSink>()) { EmphasisToken.Content(it) },
        )

        val result = pMany(tokenParser)(input)
        if (result !is Success) return emptyList()

        val tokens = result.value
        val inlines = processEmphasis(tokens)
        emitEmphasisSpans(tokens, inlines, records, sink)

        return sink.spans
    }

    @Test
    fun simpleEmphasis() {
        // "*foo*"
        val spans = highlight("*foo*")
        // Should have EmphasisMarker spans for the opening and closing *
        val emphSpans = spans.filter {
            it.type == TokenType.EmphasisMarker || it.type == TokenType.StrongMarker
        }
        assertEquals(2, emphSpans.size)
        assertEquals(TokenType.EmphasisMarker, emphSpans[0].type)
        assertEquals(TokenType.EmphasisMarker, emphSpans[1].type)
    }

    @Test
    fun strongEmphasis() {
        // "**foo**"
        val spans = highlight("**foo**")
        val strongSpans = spans.filter { it.type == TokenType.StrongMarker }
        assertEquals(2, strongSpans.size)
    }

    @Test
    fun unmatchedDelimiters() {
        // "*foo" — no closing delimiter, should produce no emphasis spans
        val spans = highlight("*foo")
        val emphSpans = spans.filter {
            it.type == TokenType.EmphasisMarker || it.type == TokenType.StrongMarker
        }
        assertEquals(0, emphSpans.size)
    }
}
