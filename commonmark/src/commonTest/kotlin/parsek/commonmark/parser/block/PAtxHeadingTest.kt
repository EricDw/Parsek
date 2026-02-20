package parsek.commonmark.parser.block

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.commonmark.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PAtxHeadingTest {

    private fun parse(input: String) =
        pAtxHeading<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Heading levels 1–6
    // -------------------------------------------------------------------------

    @Test
    fun level1() {
        val result = parse("# foo")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(1, result.value.level)
    }

    @Test
    fun level2() {
        val result = parse("## foo")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(2, result.value.level)
    }

    @Test
    fun level3() {
        val result = parse("### foo")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(3, result.value.level)
    }

    @Test
    fun level4() {
        val result = parse("#### foo")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(4, result.value.level)
    }

    @Test
    fun level5() {
        val result = parse("##### foo")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(5, result.value.level)
    }

    @Test
    fun level6() {
        val result = parse("###### foo")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(6, result.value.level)
    }

    // -------------------------------------------------------------------------
    // Inline content (stub Text node)
    // -------------------------------------------------------------------------

    @Test
    fun inlineContent() {
        val result = parse("# Hello, World!")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(listOf(Inline.Text("Hello, World!")), result.value.inlines)
    }

    @Test
    fun leadingSpacesInContentAreStripped() {
        // Multiple spaces after the '#' marker are stripped from content.
        val result = parse("#    foo")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun trailingSpacesInContentAreStripped() {
        val result = parse("# foo   ")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    // -------------------------------------------------------------------------
    // Closing '#' run stripping
    // -------------------------------------------------------------------------

    @Test
    fun closingHashRunStripped() {
        val result = parse("# foo ##")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun closingHashRunWithTrailingSpacesStripped() {
        val result = parse("# foo ##   ")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun closingHashRunLongStripped() {
        val result = parse("# foo ###################################")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun hashNotPrecededBySpaceIsNotStripped() {
        // Per spec: closing '#' run must be preceded by a space or tab.
        val result = parse("# foo#")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo#")), result.value.inlines)
    }

    @Test
    fun contentStartingWithHashIsNotStripped() {
        // "# ##" — the '##' at the start of content has no preceding space.
        val result = parse("# ##")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(listOf(Inline.Text("##")), result.value.inlines)
    }

    // -------------------------------------------------------------------------
    // Empty headings
    // -------------------------------------------------------------------------

    @Test
    fun emptyHeadingAtEof() {
        // A single '#' with no content succeeds with an empty inline list.
        val result = parse("#")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(emptyList(), result.value.inlines)
    }

    @Test
    fun emptyHeadingWithNewline() {
        val result = parse("#\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(emptyList(), result.value.inlines)
        assertEquals(2, result.nextIndex)
    }

    @Test
    fun emptyHeadingSpaceOnly() {
        // "# " — space is consumed but content after stripping is empty.
        val result = parse("# ")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(emptyList(), result.value.inlines)
    }

    @Test
    fun contentHashesNotStrippedWhenNoPrecedingSpaceInContent() {
        // "# ###" — the '###' is the entire content after the mandatory separator
        // space; there is no space *within* the content before the hash run, so it
        // is not treated as a closing sequence.  Spec analogous to "# #" → <h1>#</h1>.
        val result = parse("# ###")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(listOf(Inline.Text("###")), result.value.inlines)
    }

    // -------------------------------------------------------------------------
    // Line ending consumption
    // -------------------------------------------------------------------------

    @Test
    fun consumesNewline() {
        val result = parse("# foo\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(6, result.nextIndex)
    }

    @Test
    fun consumesCrLf() {
        val result = parse("# foo\r\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(7, result.nextIndex)
    }

    @Test
    fun consumesCrAlone() {
        val result = parse("# foo\r")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(6, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Leading indentation (0–3 spaces allowed)
    // -------------------------------------------------------------------------

    @Test
    fun oneLeadingSpace() {
        val result = parse(" # foo")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(1, result.value.level)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun twoLeadingSpaces() {
        val result = parse("  ## foo")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(2, result.value.level)
    }

    @Test
    fun threeLeadingSpaces() {
        val result = parse("   ### foo")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(3, result.value.level)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureOnLevel7() {
        // Seven '#' characters is not a valid ATX heading.
        assertIs<Failure<Char, Unit>>(parse("####### foo"))
    }

    @Test
    fun failureOnFourLeadingSpaces() {
        // Four leading spaces turns the line into an indented code block context.
        assertIs<Failure<Char, Unit>>(parse("    # foo"))
    }

    @Test
    fun failureOnNoSpaceAfterHash() {
        // '#foo' is not a heading — no space between '#' and content.
        assertIs<Failure<Char, Unit>>(parse("#foo"))
    }

    @Test
    fun failureOnEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureOnPlainText() {
        assertIs<Failure<Char, Unit>>(parse("hello"))
    }

    @Test
    fun failureOnThematicBreak() {
        // A thematic break line should not be parsed as a heading.
        assertIs<Failure<Char, Unit>>(parse("---"))
    }
}
