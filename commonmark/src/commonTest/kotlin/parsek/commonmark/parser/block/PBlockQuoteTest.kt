package parsek.commonmark.parser.block

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.commonmark.ast.Inline
import parsek.pMap
import parsek.pOr
import parsek.text.pBlankLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PBlockQuoteTest {

    /** Inner parser for tests that only need paragraphs. */
    private fun parse(input: String) =
        pBlockQuote<Unit> { pParagraph() }(ParserInput.of(input.toList(), Unit))

    /**
     * Inner parser that handles blank lines + paragraphs.
     * Needed for tests that have multiple blocks separated by blank lines inside a quote.
     */
    private fun parseBlanks(input: String): parsek.ParseResult<Char, Block.BlockQuote, Unit> {
        fun blockParser(): Parser<Char, Block, Unit> =
            pOr(pMap(pBlankLine<Unit>()) { Block.BlankLine }, pParagraph())
        return pBlockQuote(::blockParser)(ParserInput.of(input.toList(), Unit))
    }

    /** Inner parser for tests that need headings and paragraphs. */
    private fun parseMulti(input: String): parsek.ParseResult<Char, Block.BlockQuote, Unit> {
        fun blockParser(): Parser<Char, Block, Unit> =
            pOr(pAtxHeading(), pParagraph())
        return pBlockQuote(::blockParser)(ParserInput.of(input.toList(), Unit))
    }

    /** Inner parser for nested block-quote tests. */
    private fun parseNested(input: String): parsek.ParseResult<Char, Block.BlockQuote, Unit> {
        fun blockParser(): Parser<Char, Block, Unit> =
            pOr(pBlockQuote(::blockParser), pParagraph())
        return pBlockQuote(::blockParser)(ParserInput.of(input.toList(), Unit))
    }

    // -------------------------------------------------------------------------
    // Basic single-block quotes
    // -------------------------------------------------------------------------

    @Test
    fun singleLineQuote() {
        val result = parse("> foo\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        assertEquals(1, result.value.blocks.size)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    @Test
    fun multiLineQuote() {
        val result = parse("> foo\n> bar\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo\nbar")), para.inlines)
    }

    @Test
    fun noSpaceAfterMarker() {
        val result = parse(">foo\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    @Test
    fun twoSpacesAfterMarkerOnlyOneStripped() {
        // ">  foo" — marker strips '>' and one space; one space remains in raw content,
        // then pParagraph strips up to 3 leading spaces, yielding "foo".
        val result = parse(">  foo\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    // -------------------------------------------------------------------------
    // Leading spaces before the marker (0–3 allowed)
    // -------------------------------------------------------------------------

    @Test
    fun oneLeadingSpaceBeforeMarker() {
        val result = parse(" > foo\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    @Test
    fun threeLeadingSpacesBeforeMarker() {
        val result = parse("   > foo\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    // -------------------------------------------------------------------------
    // Multiple inner blocks
    // -------------------------------------------------------------------------

    @Test
    fun headingFollowedByParagraph() {
        val result = parseMulti("> # Heading\n> paragraph\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        assertEquals(2, result.value.blocks.size)
        assertIs<Block.Heading>(result.value.blocks[0])
        assertIs<Block.Paragraph>(result.value.blocks[1])
        val h = result.value.blocks[0] as Block.Heading
        assertEquals(1, h.level)
        assertEquals(listOf(Inline.Text("Heading")), h.inlines)
    }

    @Test
    fun twoParagraphs() {
        // The blank `>` line (strips to "") separates the two paragraphs inside.
        // A block parser that handles blank lines is required here.
        val result = parseBlanks("> foo\n>\n> bar\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        val nonBlank = result.value.blocks.filterIsInstance<Block.Paragraph>()
        assertEquals(2, nonBlank.size)
        assertEquals(listOf(Inline.Text("foo")), nonBlank[0].inlines)
        assertEquals(listOf(Inline.Text("bar")), nonBlank[1].inlines)
    }

    // -------------------------------------------------------------------------
    // Lazy continuation
    // -------------------------------------------------------------------------

    @Test
    fun lazyContinuation() {
        val result = parse("> foo\nbar\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo\nbar")), para.inlines)
    }

    @Test
    fun multipleLazyContinuationLines() {
        val result = parse("> foo\nbar\nbaz\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo\nbar\nbaz")), para.inlines)
    }

    // -------------------------------------------------------------------------
    // Termination at blank line (blank line not consumed)
    // -------------------------------------------------------------------------

    @Test
    fun blankLineTerminatesBlockQuote() {
        val result = parse("> foo\n\nnext\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    @Test
    fun blankLineNotConsumed() {
        val result = parse("> foo\n\nnext\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        // nextIndex points to the blank line, not past it.
        assertEquals("> foo\n".length, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Nested block quotes
    // -------------------------------------------------------------------------

    @Test
    fun nestedBlockQuote() {
        val result = parseNested("> > foo\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        assertEquals(1, result.value.blocks.size)
        val inner = result.value.blocks.first()
        assertIs<Block.BlockQuote>(inner)
        val innerPara = inner.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), innerPara.inlines)
    }

    @Test
    fun doublyNestedBlockQuote() {
        val result = parseNested("> > > foo\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        val mid = result.value.blocks.first() as Block.BlockQuote
        val inner = mid.blocks.first() as Block.BlockQuote
        val para = inner.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    // -------------------------------------------------------------------------
    // CRLF line endings
    // -------------------------------------------------------------------------

    @Test
    fun crlfLineEndings() {
        val result = parse("> foo\r\n> bar\r\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo\nbar")), para.inlines)
    }

    @Test
    fun crLineEndings() {
        val result = parse("> foo\r> bar\r")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo\nbar")), para.inlines)
    }

    // -------------------------------------------------------------------------
    // nextIndex correctness
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexAfterSingleLineQuote() {
        // "> foo\n" = 6 characters
        val result = parse("> foo\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        assertEquals(6, result.nextIndex)
    }

    @Test
    fun nextIndexAfterMultiLineQuote() {
        // "> foo\n> bar\n" = 12 characters
        val result = parse("> foo\n> bar\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        assertEquals(12, result.nextIndex)
    }

    @Test
    fun nextIndexDoesNotConsumeFollowingContent() {
        val result = parse("> foo\nnext line\n")
        assertIs<Success<Char, Block.BlockQuote, Unit>>(result)
        // nextIndex is after the block quote content, before "next line".
        // "> foo\n" = 6 chars; "next line\n" is lazy continuation so it's included.
        // The whole input is consumed.
        assertEquals("> foo\nnext line\n".length, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureOnPlainText() {
        assertIs<Failure<Char, Unit>>(parse("foo\n"))
    }

    @Test
    fun failureOnEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureOnBlankLine() {
        assertIs<Failure<Char, Unit>>(parse("\n"))
    }

    @Test
    fun failureOnFourLeadingSpaces() {
        // 4 leading spaces disqualify the '>' as a block-quote marker.
        assertIs<Failure<Char, Unit>>(parse("    > foo\n"))
    }
}
