package parsek.markdown.highlight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScannerTest {

    // =====================================================================
    // Helpers
    // =====================================================================

    /**
     * Asserts that no two spans of the same [TokenType] have overlapping ranges.
     */
    private fun assertNoOverlappingSameType(spans: List<Span>) {
        val byType = spans.groupBy { it.type }
        for ((type, group) in byType) {
            val sorted = group.sortedBy { it.start }
            for (i in 0 until sorted.size - 1) {
                assertTrue(
                    sorted[i].end <= sorted[i + 1].start,
                    "Overlapping $type spans: ${sorted[i]} and ${sorted[i + 1]}",
                )
            }
        }
    }

    /**
     * Returns a human-readable dump of spans for diagnostic messages.
     */
    private fun dumpSpans(markdown: String, spans: List<Span>): String {
        return spans.sortedBy { it.start }.joinToString("\n") { span ->
            val excerpt = markdown.substring(
                span.start.coerceIn(0, markdown.length),
                span.end.coerceIn(0, markdown.length),
            )
            "  ${span.type}(${span.start}, ${span.end}) = \"$excerpt\""
        }
    }

    // =====================================================================
    // Block-level (from block pass, already correct)
    // =====================================================================

    @Test
    fun emptyDocument() {
        val spans = scanDocument("")
        assertEquals(emptyList(), spans)
    }

    @Test
    fun blockOnlySpans() {
        val md = "---\n# Hello\n"
        val spans = scanDocument(md)

        assertTrue(
            spans.any { it.type == TokenType.ThematicBreak && it.start == 0 && it.end == 4 },
            "Expected ThematicBreak(0,4), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            spans.any { it.type == TokenType.HeadingMarker && it.start == 4 && it.end == 5 },
            "Expected HeadingMarker(4,5), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            spans.any { it.type == TokenType.HeadingText && it.start == 6 && it.end == 11 },
            "Expected HeadingText(6,11), got:\n${dumpSpans(md, spans)}",
        )
        // Inline pass also emits Text for heading content
        assertTrue(
            spans.any { it.type == TokenType.Text && it.start == 6 && it.end == 11 },
            "Expected Text(6,11), got:\n${dumpSpans(md, spans)}",
        )
    }

    @Test
    fun fencedCodeBlock() {
        val md = "```\nfoo\n```\n"
        val spans = scanDocument(md)

        assertTrue(
            spans.any { it.type == TokenType.CodeFence },
            "Expected CodeFence span, got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            spans.any { it.type == TokenType.CodeContent },
            "Expected CodeContent span, got:\n${dumpSpans(md, spans)}",
        )
    }

    // =====================================================================
    // Emphasis / Strong
    // =====================================================================

    @Test
    fun emphasis() {
        val md = "*foo*\n"
        val spans = scanDocument(md)
        val em = spans.filter { it.type == TokenType.EmphasisMarker }

        assertTrue(
            em.any { it.start == 0 && it.end == 1 },
            "Expected EmphasisMarker(0,1), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            spans.any { it.type == TokenType.Text && it.start == 1 && it.end == 4 },
            "Expected Text(1,4), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            em.any { it.start == 4 && it.end == 5 },
            "Expected EmphasisMarker(4,5), got:\n${dumpSpans(md, spans)}",
        )
    }

    @Test
    fun strong() {
        val md = "**foo**\n"
        val spans = scanDocument(md)
        val strong = spans.filter { it.type == TokenType.StrongMarker }

        assertTrue(
            strong.any { it.start == 0 && it.end == 2 },
            "Expected StrongMarker(0,2), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            spans.any { it.type == TokenType.Text && it.start == 2 && it.end == 5 },
            "Expected Text(2,5), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            strong.any { it.start == 5 && it.end == 7 },
            "Expected StrongMarker(5,7), got:\n${dumpSpans(md, spans)}",
        )
    }

    @Test
    fun emphasisAfterBlock() {
        val md = "# Title\n*foo*\n"
        val spans = scanDocument(md)
        val em = spans.filter { it.type == TokenType.EmphasisMarker }

        // Heading spans at 0..8
        assertTrue(
            spans.any { it.type == TokenType.HeadingMarker && it.start == 0 && it.end == 1 },
            "Expected HeadingMarker(0,1), got:\n${dumpSpans(md, spans)}",
        )
        // Paragraph starts at 8
        assertTrue(
            em.any { it.start == 8 && it.end == 9 },
            "Expected EmphasisMarker(8,9), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            em.any { it.start == 12 && it.end == 13 },
            "Expected EmphasisMarker(12,13), got:\n${dumpSpans(md, spans)}",
        )
    }

    // =====================================================================
    // Code spans
    // =====================================================================

    @Test
    fun codeSpan() {
        val md = "`code`\n"
        val spans = scanDocument(md)
        val delims = spans.filter { it.type == TokenType.CodeSpanDelimiter }
        val content = spans.filter { it.type == TokenType.CodeSpanContent }

        assertTrue(
            delims.any { it.start == 0 && it.end == 1 },
            "Expected CodeSpanDelimiter(0,1), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            content.any { it.start == 1 && it.end == 5 },
            "Expected CodeSpanContent(1,5), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            delims.any { it.start == 5 && it.end == 6 },
            "Expected CodeSpanDelimiter(5,6), got:\n${dumpSpans(md, spans)}",
        )
    }

    @Test
    fun codeSpanAfterHeading() {
        val md = "# Title\n`code`\n"
        val spans = scanDocument(md)
        val delims = spans.filter { it.type == TokenType.CodeSpanDelimiter }

        // Paragraph starts at 8
        assertTrue(
            delims.any { it.start == 8 && it.end == 9 },
            "Expected CodeSpanDelimiter(8,9), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            delims.any { it.start == 13 && it.end == 14 },
            "Expected CodeSpanDelimiter(13,14), got:\n${dumpSpans(md, spans)}",
        )
    }

    // =====================================================================
    // Links
    // =====================================================================

    @Test
    fun inlineLink() {
        val md = "[text](url)\n"
        val spans = scanDocument(md)
        val brackets = spans.filter { it.type == TokenType.LinkBracket }
        val parens = spans.filter { it.type == TokenType.LinkParen }
        val dest = spans.filter { it.type == TokenType.LinkDestination }

        assertTrue(
            brackets.any { it.start == 0 && it.end == 1 },
            "Expected LinkBracket(0,1) for [, got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            spans.any { it.type == TokenType.Text && it.start == 1 && it.end == 5 },
            "Expected Text(1,5) for 'text', got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            brackets.any { it.start == 5 && it.end == 6 },
            "Expected LinkBracket(5,6) for ], got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            parens.any { it.start == 6 && it.end == 7 },
            "Expected LinkParen(6,7) for (, got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            dest.any { it.start == 7 && it.end == 10 },
            "Expected LinkDestination(7,10) for 'url', got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            parens.any { it.start == 10 && it.end == 11 },
            "Expected LinkParen(10,11) for ), got:\n${dumpSpans(md, spans)}",
        )
    }

    @Test
    fun referenceLink() {
        val md = "[foo]: /url\n\n[foo]\n"
        val spans = scanDocument(md)

        // Block-level: link label and destination from the definition
        assertTrue(
            spans.any { it.type == TokenType.LinkLabel },
            "Expected LinkLabel span, got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            spans.any { it.type == TokenType.LinkDestination },
            "Expected LinkDestination span, got:\n${dumpSpans(md, spans)}",
        )

        // Inline: link brackets from the reference link in the paragraph
        // Paragraph starts at 13 (after "[foo]: /url\n\n")
        val brackets = spans.filter { it.type == TokenType.LinkBracket }
        assertTrue(
            brackets.any { it.start == 13 && it.end == 14 },
            "Expected LinkBracket(13,14), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            brackets.any { it.start == 17 && it.end == 18 },
            "Expected LinkBracket(17,18), got:\n${dumpSpans(md, spans)}",
        )
    }

    @Test
    fun linkWithEmphasis() {
        // The original bug case: link text with emphasis inside
        val md = "[*foo*](url)\n"
        val spans = scanDocument(md)
        val brackets = spans.filter { it.type == TokenType.LinkBracket }
        val em = spans.filter { it.type == TokenType.EmphasisMarker }
        val parens = spans.filter { it.type == TokenType.LinkParen }
        val dest = spans.filter { it.type == TokenType.LinkDestination }

        // [
        assertTrue(
            brackets.any { it.start == 0 && it.end == 1 },
            "Expected LinkBracket(0,1), got:\n${dumpSpans(md, spans)}",
        )
        // * (opening emphasis)
        assertTrue(
            em.any { it.start == 1 && it.end == 2 },
            "Expected EmphasisMarker(1,2), got:\n${dumpSpans(md, spans)}",
        )
        // foo
        assertTrue(
            spans.any { it.type == TokenType.Text && it.start == 2 && it.end == 5 },
            "Expected Text(2,5), got:\n${dumpSpans(md, spans)}",
        )
        // * (closing emphasis)
        assertTrue(
            em.any { it.start == 5 && it.end == 6 },
            "Expected EmphasisMarker(5,6), got:\n${dumpSpans(md, spans)}",
        )
        // ]
        assertTrue(
            brackets.any { it.start == 6 && it.end == 7 },
            "Expected LinkBracket(6,7), got:\n${dumpSpans(md, spans)}",
        )
        // (
        assertTrue(
            parens.any { it.start == 7 && it.end == 8 },
            "Expected LinkParen(7,8), got:\n${dumpSpans(md, spans)}",
        )
        // url
        assertTrue(
            dest.any { it.start == 8 && it.end == 11 },
            "Expected LinkDestination(8,11), got:\n${dumpSpans(md, spans)}",
        )
        // )
        assertTrue(
            parens.any { it.start == 11 && it.end == 12 },
            "Expected LinkParen(11,12), got:\n${dumpSpans(md, spans)}",
        )

        assertNoOverlappingSameType(spans)
    }

    // =====================================================================
    // Backslash escapes
    // =====================================================================

    @Test
    fun backslashEscape() {
        val md = "\\*not emphasis\\*\n"
        val spans = scanDocument(md)
        val escapes = spans.filter { it.type == TokenType.EscapeSequence }
        val text = spans.filter { it.type == TokenType.Text }

        assertTrue(
            escapes.any { it.start == 0 && it.end == 2 },
            "Expected EscapeSequence(0,2), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            text.any { it.start == 2 && it.end == 14 },
            "Expected Text(2,14) for 'not emphasis', got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            escapes.any { it.start == 14 && it.end == 16 },
            "Expected EscapeSequence(14,16), got:\n${dumpSpans(md, spans)}",
        )
    }

    // =====================================================================
    // HTML entities
    // =====================================================================

    @Test
    fun htmlEntity() {
        val md = "&amp;\n"
        val spans = scanDocument(md)

        assertTrue(
            spans.any { it.type == TokenType.EntityRef && it.start == 0 && it.end == 5 },
            "Expected EntityRef(0,5), got:\n${dumpSpans(md, spans)}",
        )
    }

    // =====================================================================
    // Multi-line paragraph
    // =====================================================================

    @Test
    fun multiLineParagraph() {
        val md = "Hello\n*world*\n"
        val spans = scanDocument(md)
        val em = spans.filter { it.type == TokenType.EmphasisMarker }

        assertTrue(
            spans.any { it.type == TokenType.Text && it.start == 0 && it.end == 5 },
            "Expected Text(0,5) for 'Hello', got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            spans.any { it.type == TokenType.SoftBreak && it.start == 5 && it.end == 6 },
            "Expected SoftBreak(5,6), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            em.any { it.start == 6 && it.end == 7 },
            "Expected EmphasisMarker(6,7), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            spans.any { it.type == TokenType.Text && it.start == 7 && it.end == 12 },
            "Expected Text(7,12) for 'world', got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            em.any { it.start == 12 && it.end == 13 },
            "Expected EmphasisMarker(12,13), got:\n${dumpSpans(md, spans)}",
        )
    }

    // =====================================================================
    // Paragraph with leading spaces
    // =====================================================================

    @Test
    fun paragraphWithLeadingSpaces() {
        // "  *foo*\n" — trimStart strips 2 spaces, content starts at doc offset 2
        val md = "  *foo*\n"
        val spans = scanDocument(md)
        val em = spans.filter { it.type == TokenType.EmphasisMarker }

        assertTrue(
            em.any { it.start == 2 && it.end == 3 },
            "Expected EmphasisMarker(2,3), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            spans.any { it.type == TokenType.Text && it.start == 3 && it.end == 6 },
            "Expected Text(3,6), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            em.any { it.start == 6 && it.end == 7 },
            "Expected EmphasisMarker(6,7), got:\n${dumpSpans(md, spans)}",
        )
    }

    // =====================================================================
    // Heading with inline content
    // =====================================================================

    @Test
    fun headingInlineContent() {
        val md = "# *foo*\n"
        val spans = scanDocument(md)
        val em = spans.filter { it.type == TokenType.EmphasisMarker }

        // Block-level spans
        assertTrue(
            spans.any { it.type == TokenType.HeadingMarker && it.start == 0 && it.end == 1 },
            "Expected HeadingMarker(0,1), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            spans.any { it.type == TokenType.HeadingText && it.start == 2 && it.end == 7 },
            "Expected HeadingText(2,7), got:\n${dumpSpans(md, spans)}",
        )
        // Inline spans: content starts at offset 2 (after "# ")
        assertTrue(
            em.any { it.start == 2 && it.end == 3 },
            "Expected EmphasisMarker(2,3), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            spans.any { it.type == TokenType.Text && it.start == 3 && it.end == 6 },
            "Expected Text(3,6), got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            em.any { it.start == 6 && it.end == 7 },
            "Expected EmphasisMarker(6,7), got:\n${dumpSpans(md, spans)}",
        )
    }

    // =====================================================================
    // Regression: the original bug — link text overlap
    // =====================================================================

    @Test
    fun linkNoOverlap() {
        val md = "A [CommonMark](https://commonmark.org) test\n"
        val spans = scanDocument(md)

        assertNoOverlappingSameType(spans)

        // Verify specific span excerpts match the source
        for (span in spans) {
            val excerpt = md.substring(
                span.start.coerceIn(0, md.length),
                span.end.coerceIn(0, md.length),
            )
            // No span should produce garbage text
            assertTrue(
                span.end <= md.length,
                "Span ${span.type}(${span.start}, ${span.end}) exceeds document length ${md.length}",
            )
        }

        // Verify key spans
        val brackets = spans.filter { it.type == TokenType.LinkBracket }
        val parens = spans.filter { it.type == TokenType.LinkParen }
        val dest = spans.filter { it.type == TokenType.LinkDestination }

        // "A " is Text(0,2), then [
        assertTrue(
            brackets.any { it.start == 2 && it.end == 3 },
            "Expected LinkBracket(2,3) for [, got:\n${dumpSpans(md, spans)}",
        )
        // "CommonMark" is Text(3,13), then ]
        assertTrue(
            brackets.any { it.start == 13 && it.end == 14 },
            "Expected LinkBracket(13,14) for ], got:\n${dumpSpans(md, spans)}",
        )
        // ( at 14
        assertTrue(
            parens.any { it.start == 14 && it.end == 15 },
            "Expected LinkParen(14,15) for (, got:\n${dumpSpans(md, spans)}",
        )
        // URL "https://commonmark.org" = 22 chars, at 15..37
        assertTrue(
            dest.any { it.start == 15 && it.end == 37 },
            "Expected LinkDestination(15,37), got:\n${dumpSpans(md, spans)}",
        )
        // ) at 37
        assertTrue(
            parens.any { it.start == 37 && it.end == 38 },
            "Expected LinkParen(37,38) for ), got:\n${dumpSpans(md, spans)}",
        )
    }

    // =====================================================================
    // Complex multi-line paragraph with links and emphasis (real-world)
    // =====================================================================

    @Test
    fun complexParagraphNoOverlap() {
        val md = "A **Compose Multiplatform** renderer for [CommonMark](https://commonmark.org)\n" +
            "documents parsed by *Parsek*.\n"
        val spans = scanDocument(md)

        assertNoOverlappingSameType(spans)

        // All spans should be within bounds
        for (span in spans) {
            assertTrue(
                span.start >= 0 && span.end <= md.length,
                "Span ${span.type}(${span.start}, ${span.end}) out of bounds for document length ${md.length}",
            )
        }
    }

    // =====================================================================
    // Autolink
    // =====================================================================

    @Test
    fun autolink() {
        val md = "<https://example.com>\n"
        val spans = scanDocument(md)

        assertTrue(
            spans.any { it.type == TokenType.AutolinkUrl && it.start == 1 && it.end == 20 },
            "Expected AutolinkUrl(1,20), got:\n${dumpSpans(md, spans)}",
        )
    }

    // =====================================================================
    // Inline HTML
    // =====================================================================

    @Test
    fun inlineRawHtml() {
        val md = "a <em>b</em> c\n"
        val spans = scanDocument(md)
        val html = spans.filter { it.type == TokenType.HtmlInline }

        assertTrue(
            html.any { it.start == 2 && it.end == 6 },
            "Expected HtmlInline(2,6) for '<em>', got:\n${dumpSpans(md, spans)}",
        )
        assertTrue(
            html.any { it.start == 7 && it.end == 12 },
            "Expected HtmlInline(7,12) for '</em>', got:\n${dumpSpans(md, spans)}",
        )
    }
}
