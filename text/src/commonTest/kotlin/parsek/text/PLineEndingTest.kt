package parsek.text

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PLineEndingTest {

    // -------------------------------------------------------------------------
    // pLineEnding
    // -------------------------------------------------------------------------

    @Test
    fun lineEndingSuccessOnLf() {
        val input = ParserInput.of(listOf('\n'), Unit)
        val result = pLineEnding<Unit>()(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('\n', result.value)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun lineEndingSuccessOnCrlf() {
        val input = ParserInput.of(listOf('\r', '\n'), Unit)
        val result = pLineEnding<Unit>()(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('\n', result.value)
        assertEquals(2, result.nextIndex)
    }

    @Test
    fun lineEndingSuccessOnCrAlone() {
        val input = ParserInput.of(listOf('\r', 'x'), Unit)
        val result = pLineEnding<Unit>()(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('\n', result.value)
        assertEquals(1, result.nextIndex) // only '\r' consumed, 'x' untouched
    }

    @Test
    fun lineEndingNormalisesAllFormsToLf() {
        val forms = listOf(
            listOf('\n'),
            listOf('\r', '\n'),
            listOf('\r', 'x'),
        )
        for (chars in forms) {
            val input = ParserInput.of(chars, Unit)
            val result = pLineEnding<Unit>()(input)
            assertIs<Success<Char, Char, Unit>>(result)
            assertEquals('\n', result.value)
        }
    }

    @Test
    fun lineEndingFailureOnNonLineEnding() {
        val input = ParserInput.of(listOf('a'), Unit)
        val result = pLineEnding<Unit>()(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("line ending", result.message)
    }

    @Test
    fun lineEndingFailureOnEmptyInput() {
        val input = ParserInput.of(emptyList<Char>(), Unit)
        assertIs<Failure<Char, Unit>>(pLineEnding<Unit>()(input))
    }

    // -------------------------------------------------------------------------
    // pBlankLine
    // -------------------------------------------------------------------------

    @Test
    fun blankLineSuccessOnBareNewline() {
        val input = ParserInput.of(listOf('\n'), Unit)
        val result = pBlankLine<Unit>()(input)
        assertIs<Success<Char, Unit, Unit>>(result)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun blankLineSuccessOnSpacesBeforeNewline() {
        val input = ParserInput.of("   \n".toList(), Unit)
        val result = pBlankLine<Unit>()(input)
        assertIs<Success<Char, Unit, Unit>>(result)
        assertEquals(4, result.nextIndex)
    }

    @Test
    fun blankLineSuccessOnTabsBeforeNewline() {
        val input = ParserInput.of("\t\t\n".toList(), Unit)
        val result = pBlankLine<Unit>()(input)
        assertIs<Success<Char, Unit, Unit>>(result)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun blankLineFailureWhenNoLineEnding() {
        val input = ParserInput.of("   ".toList(), Unit)
        val result = pBlankLine<Unit>()(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("blank line", result.message)
    }

    @Test
    fun blankLineFailureOnNonBlankContent() {
        val input = ParserInput.of("abc\n".toList(), Unit)
        val result = pBlankLine<Unit>()(input)
        assertIs<Failure<Char, Unit>>(result)
    }

    // -------------------------------------------------------------------------
    // pRestOfLine
    // -------------------------------------------------------------------------

    @Test
    fun restOfLineConsumesUntilNewline() {
        val input = ParserInput.of("hello\nworld".toList(), Unit)
        val result = pRestOfLine<Unit>()(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("hello", result.value)
        assertEquals(5, result.nextIndex) // '\n' not consumed
    }

    @Test
    fun restOfLineConsumesUntilCr() {
        val input = ParserInput.of("hello\rworld".toList(), Unit)
        val result = pRestOfLine<Unit>()(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("hello", result.value)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun restOfLineReturnsEmptyStringAtLineEnding() {
        val input = ParserInput.of(listOf('\n'), Unit)
        val result = pRestOfLine<Unit>()(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("", result.value)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun restOfLineConsumesEntireInputWhenNoLineEnding() {
        val input = ParserInput.of("hello".toList(), Unit)
        val result = pRestOfLine<Unit>()(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("hello", result.value)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun restOfLineSucceedsWithEmptyStringOnEmptyInput() {
        val input = ParserInput.of(emptyList<Char>(), Unit)
        val result = pRestOfLine<Unit>()(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("", result.value)
    }
}
