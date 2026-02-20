package parsek.commonmark.parser.block

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PThematicBreakTest {

    private fun parse(input: String) =
        pThematicBreak<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Valid thematic breaks — dashes
    // -------------------------------------------------------------------------

    @Test
    fun threeHyphens() {
        assertIs<Success<Char, Block.ThematicBreak, Unit>>(parse("---"))
    }

    @Test
    fun threeHyphensWithNewline() {
        val result = parse("---\n")
        assertIs<Success<Char, Block.ThematicBreak, Unit>>(result)
        assertEquals(4, result.nextIndex)
    }

    @Test
    fun manyHyphens() {
        assertIs<Success<Char, Block.ThematicBreak, Unit>>(parse("-------"))
    }

    @Test
    fun hyphensWithSpacesBetween() {
        assertIs<Success<Char, Block.ThematicBreak, Unit>>(parse("- - -"))
    }

    @Test
    fun hyphensWithTrailingSpaces() {
        assertIs<Success<Char, Block.ThematicBreak, Unit>>(parse("---   "))
    }

    // -------------------------------------------------------------------------
    // Valid thematic breaks — asterisks and underscores
    // -------------------------------------------------------------------------

    @Test
    fun threeAsterisks() {
        assertIs<Success<Char, Block.ThematicBreak, Unit>>(parse("***"))
    }

    @Test
    fun asterisksSpaced() {
        assertIs<Success<Char, Block.ThematicBreak, Unit>>(parse("* * *"))
    }

    @Test
    fun threeUnderscores() {
        assertIs<Success<Char, Block.ThematicBreak, Unit>>(parse("___"))
    }

    @Test
    fun underscoresSpaced() {
        assertIs<Success<Char, Block.ThematicBreak, Unit>>(parse("_ _ _"))
    }

    // -------------------------------------------------------------------------
    // Leading indentation (0–3 spaces allowed)
    // -------------------------------------------------------------------------

    @Test
    fun oneLeadingSpace() {
        assertIs<Success<Char, Block.ThematicBreak, Unit>>(parse(" ---"))
    }

    @Test
    fun twoLeadingSpaces() {
        assertIs<Success<Char, Block.ThematicBreak, Unit>>(parse("  ---"))
    }

    @Test
    fun threeLeadingSpaces() {
        assertIs<Success<Char, Block.ThematicBreak, Unit>>(parse("   ---"))
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureOnTwoMarkers() {
        val result = parse("--")
        assertIs<Failure<Char, Unit>>(result)
    }

    @Test
    fun failureOnFourLeadingSpaces() {
        // 4 spaces makes it an indented code block context, not a thematic break
        val result = parse("    ---")
        assertIs<Failure<Char, Unit>>(result)
    }

    @Test
    fun failureOnMixedMarkers() {
        val result = parse("*-*")
        assertIs<Failure<Char, Unit>>(result)
    }

    @Test
    fun failureOnNonWhitespaceAfterMarkers() {
        val result = parse("--- x")
        assertIs<Failure<Char, Unit>>(result)
    }

    @Test
    fun failureOnEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureOnPlainText() {
        assertIs<Failure<Char, Unit>>(parse("hello"))
    }

    // -------------------------------------------------------------------------
    // Result value
    // -------------------------------------------------------------------------

    @Test
    fun alwaysReturnsThematicBreakValue() {
        val result = parse("***")
        assertIs<Success<Char, Block.ThematicBreak, Unit>>(result)
        assertEquals(Block.ThematicBreak, result.value)
    }
}
