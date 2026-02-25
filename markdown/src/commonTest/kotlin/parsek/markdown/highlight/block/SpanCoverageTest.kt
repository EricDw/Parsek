package parsek.markdown.highlight.block

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.markdown.ast.Block
import parsek.markdown.highlight.Span
import parsek.markdown.highlight.SpanSink
import parsek.markdown.parser.block.pParagraph
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Verifies the "weak round-trip" property for every block highlight wrapper:
 *
 * 1. Spans are sorted by start index.
 * 2. Spans do not overlap.
 * 3. Every span falls within `[0, consumedEnd)`.
 * 4. The text extracted via spans is a subset of the original input.
 *
 * This does NOT require spans to cover every character — gaps (whitespace,
 * line endings, structural punctuation) are allowed.
 */
class SpanCoverageTest {

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private data class HighlightResult(
        val source: String,
        val spans: List<Span>,
        val consumedEnd: Int,
    )

    private fun <O> highlight(parser: Parser<Char, O, SpanSink>, source: String): HighlightResult {
        val sink = SpanSink()
        val input = ParserInput.of(source.toList(), sink)
        val result = parser(input)
        if (result !is Success) fail("Parser failed on: ${source.take(40)}...")
        return HighlightResult(source, sink.spans, result.nextIndex)
    }

    private fun assertSpanInvariants(hr: HighlightResult) {
        val (source, spans, consumedEnd) = hr

        // 1. All spans within consumed range.
        for (span in spans) {
            assertTrue(span.start >= 0, "span.start < 0: $span")
            assertTrue(span.end <= consumedEnd, "span.end (${ span.end }) > consumedEnd ($consumedEnd): $span")
            assertTrue(span.start < span.end, "empty span: $span")
        }

        // 2. Sorted by start.
        for (i in 1 until spans.size) {
            assertTrue(
                spans[i].start >= spans[i - 1].start,
                "Spans not sorted: ${spans[i - 1]} before ${spans[i]}",
            )
        }

        // 3. Non-overlapping.
        for (i in 1 until spans.size) {
            assertTrue(
                spans[i].start >= spans[i - 1].end,
                "Spans overlap: ${spans[i - 1]} and ${spans[i]}",
            )
        }

        // 4. Extracted text matches original at those positions.
        for (span in spans) {
            val extracted = source.substring(span.start, span.end)
            assertTrue(extracted.isNotEmpty(), "Empty extraction for $span")
        }
    }

    // ---------------------------------------------------------------------
    // Thematic break
    // ---------------------------------------------------------------------

    @Test
    fun thematicBreakCoverage() {
        for (input in listOf("---\n", "* * *\n", "___\n", "   ***\n")) {
            assertSpanInvariants(highlight(pThematicBreakHighlight(), input))
        }
    }

    // ---------------------------------------------------------------------
    // ATX heading
    // ---------------------------------------------------------------------

    @Test
    fun atxHeadingCoverage() {
        for (input in listOf(
            "# Heading\n",
            "## Hello World\n",
            "### foo ###\n",
            "#\n",
            "# \n",
            "  ## bar\n",
        )) {
            assertSpanInvariants(highlight(pAtxHeadingHighlight(), input))
        }
    }

    // ---------------------------------------------------------------------
    // Setext heading
    // ---------------------------------------------------------------------

    @Test
    fun setextHeadingCoverage() {
        for (input in listOf(
            "Foo\n===\n",
            "Bar\n---\n",
            "Line1\nLine2\n===\n",
        )) {
            assertSpanInvariants(highlight(pSetextHeadingHighlight(), input))
        }
    }

    // ---------------------------------------------------------------------
    // Indented code block
    // ---------------------------------------------------------------------

    @Test
    fun indentedCodeBlockCoverage() {
        for (input in listOf(
            "    code\n",
            "    a\n    b\n",
            "    a\n\n    b\n",
        )) {
            assertSpanInvariants(highlight(pIndentedCodeBlockHighlight(), input))
        }
    }

    // ---------------------------------------------------------------------
    // Fenced code block
    // ---------------------------------------------------------------------

    @Test
    fun fencedCodeBlockCoverage() {
        for (input in listOf(
            "```\ncode\n```\n",
            "```kotlin\nval x = 1\n```\n",
            "~~~\nhi\n~~~\n",
            "```\ncode\n",
            "```\n```\n",
            "```\na\nb\nc\n```\n",
        )) {
            assertSpanInvariants(highlight(pFencedCodeBlockHighlight(), input))
        }
    }

    // ---------------------------------------------------------------------
    // HTML block
    // ---------------------------------------------------------------------

    @Test
    fun htmlBlockCoverage() {
        for (input in listOf(
            "<div>\nhello\n</div>\n",
            "<pre>\ncode\n</pre>\n",
            "<!-- comment -->\n",
        )) {
            assertSpanInvariants(highlight(pHtmlBlockHighlight(), input))
        }
    }

    // ---------------------------------------------------------------------
    // Link reference definition
    // ---------------------------------------------------------------------

    @Test
    fun linkReferenceDefinitionCoverage() {
        for (input in listOf(
            "[foo]: /url\n",
            "[foo]: /url \"title\"\n",
            "[bar]: <http://example.com>\n",
        )) {
            assertSpanInvariants(highlight(pLinkReferenceDefinitionHighlight(), input))
        }
    }

    // ---------------------------------------------------------------------
    // Paragraph (no block-level spans — just verify zero spans is valid)
    // ---------------------------------------------------------------------

    @Test
    fun paragraphCoverage() {
        for (input in listOf(
            "Hello world\n",
            "Line one\nLine two\n",
        )) {
            val hr = highlight(pParagraphHighlight(), input)
            // Paragraph emits no spans; invariants still hold trivially.
            assertSpanInvariants(hr)
            assertTrue(hr.spans.isEmpty(), "Paragraph should emit no block spans")
        }
    }

    // ---------------------------------------------------------------------
    // Block quote
    // ---------------------------------------------------------------------

    /** A minimal block factory for container block tests. */
    private val blockFactory: () -> Parser<Char, Block, SpanSink> = {
        Parser { input ->
            when (val r = pParagraph<SpanSink>()(input)) {
                is Success -> Success(r.value as Block, r.nextIndex, r.input)
                is Failure -> r
            }
        }
    }

    @Test
    fun blockQuoteCoverage() {
        for (input in listOf(
            "> hello\n",
            "> a\n> b\n",
            "  > indented\n",
            ">no space\n",
        )) {
            assertSpanInvariants(highlight(pBlockQuoteHighlight(blockFactory), input))
        }
    }

    // ---------------------------------------------------------------------
    // List
    // ---------------------------------------------------------------------

    @Test
    fun listItemCoverage() {
        for (input in listOf(
            "- hello\n",
            "1. hello\n",
            " + world\n",
            "10) item\n",
        )) {
            assertSpanInvariants(highlight(pListItemHighlight(blockFactory), input))
        }
    }

    @Test
    fun listCoverage() {
        for (input in listOf(
            "- a\n- b\n",
            "1. a\n2. b\n",
            "* x\n* y\n* z\n",
        )) {
            assertSpanInvariants(highlight(pListHighlight(blockFactory), input))
        }
    }
}
