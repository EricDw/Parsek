package parsek.commonmark.parser.block

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PFencedCodeBlockTest {

    private fun parse(input: String) =
        pFencedCodeBlock<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Basic backtick and tilde fences
    // -------------------------------------------------------------------------

    @Test
    fun backtickFence() {
        val result = parse("```\nhello\n```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("hello\n", result.value.literal)
        assertNull(result.value.info)
    }

    @Test
    fun tildeFence() {
        val result = parse("~~~\nhello\n~~~\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("hello\n", result.value.literal)
        assertNull(result.value.info)
    }

    @Test
    fun fourBacktickFence() {
        val result = parse("````\nhello\n````\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("hello\n", result.value.literal)
    }

    @Test
    fun manyTildeFence() {
        val result = parse("~~~~~\nhello\n~~~~~\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("hello\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Info string
    // -------------------------------------------------------------------------

    @Test
    fun infoString() {
        val result = parse("```ruby\nhello\n```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("ruby", result.value.info)
    }

    @Test
    fun infoStringTrimmed() {
        val result = parse("```   ruby   \nhello\n```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("ruby", result.value.info)
    }

    @Test
    fun infoStringWithSpaceInMiddle() {
        val result = parse("```ruby json\nhello\n```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        // Info string is everything up to the newline, trimmed.
        assertEquals("ruby json", result.value.info)
    }

    @Test
    fun tildeInfoStringWithTilde() {
        // Tilde fences allow tildes in the info string.
        val result = parse("~~~ruby~extra\nhello\n~~~\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("ruby~extra", result.value.info)
    }

    @Test
    fun emptyInfoString() {
        val result = parse("```   \nhello\n```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertNull(result.value.info)
    }

    // -------------------------------------------------------------------------
    // Empty and single-line content
    // -------------------------------------------------------------------------

    @Test
    fun emptyBlock() {
        val result = parse("```\n```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("", result.value.literal)
    }

    @Test
    fun singleContentLine() {
        val result = parse("```\nfoo\n```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("foo\n", result.value.literal)
    }

    @Test
    fun multipleContentLines() {
        val result = parse("```\nfoo\nbar\nbaz\n```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("foo\nbar\nbaz\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Blank lines within content are preserved
    // -------------------------------------------------------------------------

    @Test
    fun blankLineWithin() {
        val result = parse("```\nfoo\n\nbar\n```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("foo\n\nbar\n", result.value.literal)
    }

    @Test
    fun multipleBlankLinesWithin() {
        val result = parse("```\nfoo\n\n\nbar\n```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("foo\n\n\nbar\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // No closing fence — block extends to EOF
    // -------------------------------------------------------------------------

    @Test
    fun noClosingFenceEof() {
        val result = parse("```\nhello")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("hello\n", result.value.literal)
    }

    @Test
    fun noClosingFenceMultilineEof() {
        val result = parse("```\nfoo\nbar")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("foo\nbar\n", result.value.literal)
    }

    @Test
    fun openingFenceOnlyEof() {
        val result = parse("```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Closing fence length must be >= opening fence length
    // -------------------------------------------------------------------------

    @Test
    fun closingFenceLongerThanOpening() {
        // Opening is 3 backticks, closing is 4 — valid.
        val result = parse("```\nhello\n````\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("hello\n", result.value.literal)
    }

    @Test
    fun closingFenceShorterThanOpeningIsContent() {
        // Opening is 4 backticks, closing attempt of 3 is treated as content.
        val result = parse("````\nhello\n```\nstill content\n````\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("hello\n```\nstill content\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Closing fence character must match opening
    // -------------------------------------------------------------------------

    @Test
    fun backtickFenceNotClosedByTilde() {
        // A tilde closing sequence inside a backtick fence is content.
        val result = parse("```\nhello\n~~~\nworld\n```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("hello\n~~~\nworld\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Leading indentation on opening fence and content stripping
    // -------------------------------------------------------------------------

    @Test
    fun oneSpaceIndentedFence() {
        val result = parse(" ```\nhello\n```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("hello\n", result.value.literal)
    }

    @Test
    fun threeSpaceIndentedFence() {
        val result = parse("   ```\nhello\n```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("hello\n", result.value.literal)
    }

    @Test
    fun indentedFenceStripsLeadingSpacesFromContent() {
        // Opening fence indented 2 spaces: strip up to 2 leading spaces per content line.
        val result = parse("  ```\n  hello\n    world\nhello\n  ```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        // "  hello" → strip 2 → "hello"
        // "    world" → strip 2 → "  world"
        // "hello" → strip 0 (no leading space) → "hello"
        assertEquals("hello\n  world\nhello\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Closing fence may have 0–3 leading spaces
    // -------------------------------------------------------------------------

    @Test
    fun closingFenceWithLeadingSpaces() {
        val result = parse("```\nhello\n   ```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("hello\n", result.value.literal)
    }

    @Test
    fun closingFenceWithTrailingSpaces() {
        val result = parse("```\nhello\n```   \n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("hello\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Line ending variants
    // -------------------------------------------------------------------------

    @Test
    fun crLfLineEndings() {
        val result = parse("```\r\nhello\r\n```\r\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals("hello\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // nextIndex correctness
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexAfterClosedFence() {
        // "```\nhi\n```\n" = 3+1+2+1+3+1 = 11 characters.
        val result = parse("```\nhi\n```\n")
        assertIs<Success<Char, Block.FencedCodeBlock, Unit>>(result)
        assertEquals(11, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureOnTwoBackticks() {
        assertIs<Failure<Char, Unit>>(parse("``\nhello\n``\n"))
    }

    @Test
    fun failureOnEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureOnPlainText() {
        assertIs<Failure<Char, Unit>>(parse("hello\n"))
    }

    @Test
    fun failureOnFourLeadingSpaces() {
        // 4 leading spaces → indented code block territory, not a fence.
        assertIs<Failure<Char, Unit>>(parse("    ```\nhello\n```\n"))
    }

    @Test
    fun failureOnBacktickInInfoStringOfBacktickFence() {
        assertIs<Failure<Char, Unit>>(parse("``` hello`world\nfoo\n```\n"))
    }
}