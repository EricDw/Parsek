package parsek.commonmark.parser.block

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.commonmark.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PSetextHeadingTest {

    private fun parse(input: String) =
        pSetextHeading<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Basic heading levels
    // -------------------------------------------------------------------------

    @Test
    fun level1EqualsUnderline() {
        val result = parse("foo\n===\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(1, result.value.level)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun level2DashUnderline() {
        val result = parse("foo\n---\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(2, result.value.level)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun level1SingleEqualsSign() {
        val result = parse("foo\n=\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(1, result.value.level)
    }

    @Test
    fun level2SingleDash() {
        val result = parse("foo\n-\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(2, result.value.level)
    }

    @Test
    fun level1LongUnderline() {
        val result = parse("foo\n====================\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(1, result.value.level)
    }

    // -------------------------------------------------------------------------
    // Multi-line content
    // -------------------------------------------------------------------------

    @Test
    fun multiLineContent() {
        val result = parse("foo\nbar\n===\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(1, result.value.level)
        assertEquals(listOf(Inline.Text("foo\nbar")), result.value.inlines)
    }

    @Test
    fun threeContentLines() {
        val result = parse("foo\nbar\nbaz\n===\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals("foo\nbar\nbaz", (result.value.inlines.first() as Inline.Text).literal)
    }

    // -------------------------------------------------------------------------
    // Underline indentation (0–3 spaces allowed)
    // -------------------------------------------------------------------------

    @Test
    fun underlineWithOneLeadingSpace() {
        val result = parse("foo\n ===\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(1, result.value.level)
    }

    @Test
    fun underlineWithTwoLeadingSpaces() {
        val result = parse("foo\n  ===\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(1, result.value.level)
    }

    @Test
    fun underlineWithThreeLeadingSpaces() {
        val result = parse("foo\n   ===\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(1, result.value.level)
    }

    @Test
    fun underlineWithTrailingSpaces() {
        val result = parse("foo\n===   \n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(1, result.value.level)
    }

    @Test
    fun underlineWithTrailingTab() {
        val result = parse("foo\n===\t\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(1, result.value.level)
    }

    // -------------------------------------------------------------------------
    // Content indentation (0–3 leading spaces are stripped)
    // -------------------------------------------------------------------------

    @Test
    fun contentWithOneLeadingSpace() {
        val result = parse(" foo\n===\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun contentWithThreeLeadingSpaces() {
        val result = parse("   foo\n===\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun contentTrailingWhitespaceStripped() {
        val result = parse("foo   \n===\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    // -------------------------------------------------------------------------
    // Underline at EOF (no trailing newline)
    // -------------------------------------------------------------------------

    @Test
    fun underlineAtEof() {
        val result = parse("foo\n===")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(1, result.value.level)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun contentAndUnderlineNoTrailingNewline() {
        val result = parse("foo\nbar\n---")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(2, result.value.level)
    }

    // -------------------------------------------------------------------------
    // CRLF line endings
    // -------------------------------------------------------------------------

    @Test
    fun crlfLineEndings() {
        val result = parse("foo\r\n===\r\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(1, result.value.level)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun crLineEndings() {
        val result = parse("foo\r===\r")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(1, result.value.level)
    }

    // -------------------------------------------------------------------------
    // nextIndex correctness
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexConsumesUnderlineLine() {
        // "foo\n===\n" = 8 characters
        val result = parse("foo\n===\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals(8, result.nextIndex)
    }

    @Test
    fun nextIndexDoesNotConsumeFollowingLine() {
        val result = parse("foo\n===\nnext line\n")
        assertIs<Success<Char, Block.Heading, Unit>>(result)
        assertEquals("foo\n===\n".length, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureOnNoUnderline() {
        // A plain content line with no underline following it is not a setext heading.
        assertIs<Failure<Char, Unit>>(parse("foo\n"))
    }

    @Test
    fun failureOnBlankLineBeforeUnderline() {
        // A blank line between content and the underline interrupts the heading.
        assertIs<Failure<Char, Unit>>(parse("foo\n\n===\n"))
    }

    @Test
    fun failureOnUnderlineAloneEquals() {
        // An `=` underline with no preceding content is not a setext heading.
        assertIs<Failure<Char, Unit>>(parse("===\n"))
    }

    @Test
    fun failureOnUnderlineAloneDash() {
        assertIs<Failure<Char, Unit>>(parse("---\n"))
    }

    @Test
    fun failureOnFourLeadingSpacesOnUnderline() {
        // 4 leading spaces on the underline disqualifies it as a setext underline.
        assertIs<Failure<Char, Unit>>(parse("foo\n    ===\n"))
    }

    @Test
    fun failureOnMixedUnderlineChars() {
        // An underline line with mixed `=` and `-` is not valid.
        assertIs<Failure<Char, Unit>>(parse("foo\n==-\n"))
    }

    @Test
    fun failureOnNonUnderlineCharAfterDashes() {
        // `--- x` has non-whitespace after the dashes → not a setext underline.
        assertIs<Failure<Char, Unit>>(parse("foo\n--- x\n"))
    }

    @Test
    fun failureOnEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureOnBlankLineOnly() {
        assertIs<Failure<Char, Unit>>(parse("\n"))
    }

    @Test
    fun failureOnPlainText() {
        assertIs<Failure<Char, Unit>>(parse("not a heading\n"))
    }
}
