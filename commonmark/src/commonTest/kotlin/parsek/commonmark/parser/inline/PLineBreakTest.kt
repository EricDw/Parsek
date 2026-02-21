package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PLineBreakTest {

    private fun parse(input: String) =
        pLineBreak<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Hard break — 2+ trailing spaces + line ending
    // -------------------------------------------------------------------------

    @Test
    fun hardBreakTwoSpaces() {
        val result = parse("  \n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HardBreak, result.value)
    }

    @Test
    fun hardBreakThreeSpaces() {
        val result = parse("   \n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HardBreak, result.value)
    }

    @Test
    fun hardBreakTwoSpacesCrlf() {
        val result = parse("  \r\n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HardBreak, result.value)
    }

    @Test
    fun hardBreakTwoSpacesCr() {
        val result = parse("  \r")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HardBreak, result.value)
    }

    // -------------------------------------------------------------------------
    // Hard break — backslash + line ending
    // -------------------------------------------------------------------------

    @Test
    fun hardBreakBackslashLf() {
        val result = parse("\\\n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HardBreak, result.value)
    }

    @Test
    fun hardBreakBackslashCrlf() {
        val result = parse("\\\r\n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HardBreak, result.value)
    }

    @Test
    fun hardBreakBackslashCr() {
        val result = parse("\\\r")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HardBreak, result.value)
    }

    // -------------------------------------------------------------------------
    // Soft break — plain line ending
    // -------------------------------------------------------------------------

    @Test
    fun softBreakLf() {
        val result = parse("\n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.SoftBreak, result.value)
    }

    @Test
    fun softBreakCrlf() {
        val result = parse("\r\n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.SoftBreak, result.value)
    }

    @Test
    fun softBreakCr() {
        val result = parse("\r")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.SoftBreak, result.value)
    }

    @Test
    fun softBreakWithLeadingSpace() {
        // One leading space is stripped; result is still SoftBreak.
        val result = parse(" \n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.SoftBreak, result.value)
    }

    @Test
    fun softBreakWithLeadingTab() {
        val result = parse("\t\n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.SoftBreak, result.value)
    }

    // -------------------------------------------------------------------------
    // nextIndex
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexHardBreakTwoSpacesLf() {
        // "  \n" = 3 characters.
        val result = parse("  \n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun nextIndexHardBreakBackslashLf() {
        // "\\\n" = 2 characters.
        val result = parse("\\\n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(2, result.nextIndex)
    }

    @Test
    fun nextIndexHardBreakCrlf() {
        // "  \r\n" = 4 characters.
        val result = parse("  \r\n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(4, result.nextIndex)
    }

    @Test
    fun nextIndexSoftBreakLf() {
        // "\n" = 1 character.
        val result = parse("\n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun nextIndexSoftBreakWithLeadingSpace() {
        // " \n" — leading space stripped, then \n consumed = 2 characters.
        val result = parse(" \n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(2, result.nextIndex)
    }

    @Test
    fun nextIndexSoftBreakCrlf() {
        // "\r\n" = 2 characters.
        val result = parse("\r\n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(2, result.nextIndex)
    }

    @Test
    fun doesNotConsumeFollowingChars() {
        val result = parse("\nfoo")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(1, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Hard break takes priority over soft break
    // -------------------------------------------------------------------------

    @Test
    fun twoSpacesBecomesHardNotSoft() {
        // Two spaces + \n must yield HardBreak, not SoftBreak.
        val result = parse("  \n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HardBreak, result.value)
    }

    @Test
    fun oneSpaceIsOnlySoft() {
        // One space is not enough for a hard break.
        val result = parse(" \n")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.SoftBreak, result.value)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureOnPlainText() {
        assertIs<Failure<Char, Unit>>(parse("foo"))
    }

    @Test
    fun failureOnEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureOnBackslashNotAtLineEnding() {
        // '\' followed by a letter is a backslash escape, not a hard break.
        assertIs<Failure<Char, Unit>>(parse("\\a"))
    }

    @Test
    fun failureOnSpaceNotAtLineEnding() {
        // Spaces not followed by a line ending are not a line break.
        assertIs<Failure<Char, Unit>>(parse("  foo"))
    }
}
