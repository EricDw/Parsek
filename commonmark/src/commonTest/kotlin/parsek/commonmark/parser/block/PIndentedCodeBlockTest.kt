package parsek.commonmark.parser.block

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PIndentedCodeBlockTest {

    private fun parse(input: String) =
        pIndentedCodeBlock<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Basic indented lines
    // -------------------------------------------------------------------------

    @Test
    fun singleFourSpaceLine() {
        val result = parse("    foo\n")
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals("foo\n", result.value.literal)
    }

    @Test
    fun singleTabLine() {
        val result = parse("\tfoo\n")
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals("foo\n", result.value.literal)
    }

    @Test
    fun multipleIndentedLines() {
        val result = parse("    foo\n    bar\n")
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals("foo\nbar\n", result.value.literal)
    }

    @Test
    fun eofWithoutTrailingNewline() {
        // A trailing newline is appended to the literal even when the source ends at EOF.
        val result = parse("    foo")
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals("foo\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Additional indentation beyond the 4-space prefix is preserved
    // -------------------------------------------------------------------------

    @Test
    fun additionalIndentationPreserved() {
        val result = parse("    foo\n        bar\n")
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals("foo\n    bar\n", result.value.literal)
    }

    @Test
    fun eightSpaces() {
        val result = parse("        foo\n")
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals("    foo\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Empty indented lines (4 spaces + newline — part of the block)
    // -------------------------------------------------------------------------

    @Test
    fun emptyIndentedLine() {
        // "    " (4 spaces) followed by a newline is an indented line with empty content.
        val result = parse("    foo\n    \n    bar\n")
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals("foo\n\nbar\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Blank lines within the block
    // -------------------------------------------------------------------------

    @Test
    fun blankLineWithin() {
        val result = parse("    foo\n\n    bar\n")
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals("foo\n\nbar\n", result.value.literal)
    }

    @Test
    fun multipleBlankLinesWithin() {
        val result = parse("    foo\n\n\n    bar\n")
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals("foo\n\n\nbar\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Trailing blank lines are NOT consumed
    // -------------------------------------------------------------------------

    @Test
    fun trailingBlankLineNotConsumed() {
        // "    foo\n\n" — the trailing blank line must be left in the input.
        val input = "    foo\n\n"
        val result = parse(input)
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals("foo\n", result.value.literal)
        // "    foo\n" is 8 characters; the trailing "\n" at index 8 is NOT consumed.
        assertEquals(8, result.nextIndex)
    }

    @Test
    fun multipleTrailingBlankLinesNotConsumed() {
        val input = "    foo\n\n\n"
        val result = parse(input)
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals("foo\n", result.value.literal)
        assertEquals(8, result.nextIndex)
    }

    @Test
    fun trailingBlankLineAfterMultipleCodeLines() {
        val input = "    foo\n    bar\n\n"
        val result = parse(input)
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals("foo\nbar\n", result.value.literal)
        // "    foo\n    bar\n" is 16 characters.
        assertEquals(16, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Line ending variants
    // -------------------------------------------------------------------------

    @Test
    fun crLfLineEnding() {
        val result = parse("    foo\r\n    bar\r\n")
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals("foo\nbar\n", result.value.literal)
    }

    @Test
    fun crAloneLineEnding() {
        val result = parse("    foo\r    bar\r")
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals("foo\nbar\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureOnThreeSpaces() {
        assertIs<Failure<Char, Unit>>(parse("   foo\n"))
    }

    @Test
    fun failureOnZeroSpaces() {
        assertIs<Failure<Char, Unit>>(parse("foo\n"))
    }

    @Test
    fun failureOnEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureOnBlankLineOnly() {
        // A blank line alone is not an indented code block.
        assertIs<Failure<Char, Unit>>(parse("\n"))
    }

    @Test
    fun failureOnPlainText() {
        assertIs<Failure<Char, Unit>>(parse("hello\n"))
    }

    // -------------------------------------------------------------------------
    // Result value
    // -------------------------------------------------------------------------

    @Test
    fun returnsIndentedCodeBlockValue() {
        val result = parse("    chunk1\n    chunk2\n")
        assertIs<Success<Char, Block.IndentedCodeBlock, Unit>>(result)
        assertEquals(Block.IndentedCodeBlock("chunk1\nchunk2\n"), result.value)
    }
}
