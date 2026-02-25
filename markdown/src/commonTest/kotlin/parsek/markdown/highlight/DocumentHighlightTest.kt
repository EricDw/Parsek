package parsek.markdown.highlight

import parsek.ParserInput
import parsek.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DocumentHighlightTest {

    private fun highlightDocument(s: String): Pair<Success<*, *, *>, List<Span>> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pDocumentHighlight()(input)
        assertIs<Success<*, *, *>>(result, "Expected Success but got: $result")
        return result to sink.spans
    }

    @Test
    fun blockOnlyDocument() {
        // Thematic break + ATX heading → block-level spans at absolute positions.
        val (_, spans) = highlightDocument("---\n# Hello\n")
        // Thematic break: "---\n" at 0..4 (includes trailing newline)
        assertTrue(
            spans.any { it.type == TokenType.ThematicBreak && it.start == 0 && it.end == 4 },
            "Expected ThematicBreak(0,4), got: $spans",
        )
        // Heading marker: "#" at 4..5
        assertTrue(
            spans.any { it.type == TokenType.HeadingMarker && it.start == 4 && it.end == 5 },
            "Expected HeadingMarker(4,5), got: $spans",
        )
        // Heading text: "Hello" at 6..11
        assertTrue(
            spans.any { it.type == TokenType.HeadingText && it.start == 6 && it.end == 11 },
            "Expected HeadingText(6,11), got: $spans",
        )
    }

    @Test
    fun inlineEmphasis() {
        // Paragraph with `*foo*` → EmphasisMarker spans at 0-based inline positions.
        val (_, spans) = highlightDocument("*foo*\n")
        // Inline spans are 0-based relative to the paragraph's inline content.
        // The raw content is "*foo*", so:
        // EmphasisMarker at 0..1 (opening *) and 4..5 (closing *)
        assertTrue(
            spans.any { it.type == TokenType.EmphasisMarker },
            "Expected EmphasisMarker spans, got: $spans",
        )
    }

    @Test
    fun mixedDocument() {
        // Heading + paragraph with code span + fenced code block.
        val input = "# Title\n`code`\n```\nfoo\n```\n"
        val (_, spans) = highlightDocument(input)

        // Block-level: heading marker, heading text.
        assertTrue(spans.any { it.type == TokenType.HeadingMarker })
        assertTrue(spans.any { it.type == TokenType.HeadingText })

        // Inline: code span delimiter and content (0-based within paragraph).
        assertTrue(spans.any { it.type == TokenType.CodeSpanDelimiter })
        assertTrue(spans.any { it.type == TokenType.CodeSpanContent })

        // Block-level: code fence and code content.
        assertTrue(spans.any { it.type == TokenType.CodeFence })
        assertTrue(spans.any { it.type == TokenType.CodeContent })
    }

    @Test
    fun emptyDocument() {
        val (_, spans) = highlightDocument("")
        assertEquals(emptyList(), spans)
    }

    @Test
    fun linkReferenceResolution() {
        // Link ref def + paragraph that references it → inline link spans.
        val input = "[foo]: /url\n\n[foo]\n"
        val (_, spans) = highlightDocument(input)

        // Block-level: link label and destination from the definition.
        assertTrue(
            spans.any { it.type == TokenType.LinkLabel },
            "Expected LinkLabel span from ref def, got: $spans",
        )
        assertTrue(
            spans.any { it.type == TokenType.LinkDestination },
            "Expected LinkDestination span from ref def, got: $spans",
        )

        // Inline: link brackets from the reference link in the paragraph.
        assertTrue(
            spans.any { it.type == TokenType.LinkBracket },
            "Expected LinkBracket spans from inline reference link, got: $spans",
        )
    }
}
