package parsek.commonmark.parser.block

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.commonmark.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PParagraphTest {

    private fun parse(input: String) =
        pParagraph<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Basic forms
    // -------------------------------------------------------------------------

    @Test
    fun singleLine() {
        val result = parse("hello\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(listOf(Inline.Text("hello")), result.value.inlines)
    }

    @Test
    fun singleLineNoNewline() {
        val result = parse("hello")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(listOf(Inline.Text("hello")), result.value.inlines)
    }

    @Test
    fun multipleLines() {
        val result = parse("foo\nbar\nbaz\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo\nbar\nbaz")), result.value.inlines)
    }

    @Test
    fun twoLines() {
        val result = parse("foo\nbar\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo\nbar")), result.value.inlines)
    }

    // -------------------------------------------------------------------------
    // Stops at blank line (blank line not consumed)
    // -------------------------------------------------------------------------

    @Test
    fun stopsAtBlankLine() {
        val result = parse("foo\n\nbar\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun blankLineNotConsumed() {
        val result = parse("foo\n\nbar\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        // nextIndex points to the start of the blank line, not past it.
        assertEquals("foo\n".length, result.nextIndex)
    }

    @Test
    fun stopsAtSpacesOnlyLine() {
        val result = parse("foo\n   \nbar\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
        assertEquals("foo\n".length, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Leading indentation (0–3 spaces stripped)
    // -------------------------------------------------------------------------

    @Test
    fun oneLeadingSpace() {
        val result = parse(" foo\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun twoLeadingSpaces() {
        val result = parse("  foo\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun threeLeadingSpaces() {
        val result = parse("   foo\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun mixedIndentationOnContinuationLines() {
        val result = parse("foo\n  bar\n baz\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo\nbar\nbaz")), result.value.inlines)
    }

    // -------------------------------------------------------------------------
    // Trailing whitespace stripped from overall content
    // -------------------------------------------------------------------------

    @Test
    fun trailingSpacesOnLastLine() {
        val result = parse("foo   \n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
    }

    @Test
    fun multiLineTrailingSpaces() {
        val result = parse("foo\nbar   \n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        // "foo\nbar   " trimEnd → "foo\nbar"
        assertEquals(listOf(Inline.Text("foo\nbar")), result.value.inlines)
    }

    // -------------------------------------------------------------------------
    // CRLF line endings
    // -------------------------------------------------------------------------

    @Test
    fun crlfLineEndings() {
        val result = parse("foo\r\nbar\r\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo\nbar")), result.value.inlines)
    }

    @Test
    fun crLineEndings() {
        val result = parse("foo\rbar\r")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo\nbar")), result.value.inlines)
    }

    @Test
    fun crlfBlankLineTerminates() {
        val result = parse("foo\r\n\r\nbar\r\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(listOf(Inline.Text("foo")), result.value.inlines)
        assertEquals("foo\r\n".length, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // nextIndex correctness
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexAtEof() {
        // "hello\n" = 6 chars; all consumed.
        val result = parse("hello\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(6, result.nextIndex)
    }

    @Test
    fun nextIndexNoTrailingNewline() {
        // "hello" = 5 chars; all consumed.
        val result = parse("hello")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun nextIndexBeforeBlankLine() {
        // "foo\n" (4 chars) consumed; "\n" is the blank line (not consumed).
        val result = parse("foo\n\nbar\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(4, result.nextIndex)
    }

    @Test
    fun nextIndexMultiLine() {
        // "foo\nbar\n" = 8 chars.
        val result = parse("foo\nbar\n")
        assertIs<Success<Char, Block.Paragraph, Unit>>(result)
        assertEquals(8, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureOnEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureOnBlankLineOnly() {
        assertIs<Failure<Char, Unit>>(parse("\n"))
    }

    @Test
    fun failureOnSpacesOnlyInput() {
        assertIs<Failure<Char, Unit>>(parse("   \n"))
    }
}
