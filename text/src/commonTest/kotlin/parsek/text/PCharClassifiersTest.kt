package parsek.text

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PCharClassifiersTest {

    // -------------------------------------------------------------------------
    // pDigit
    // -------------------------------------------------------------------------

    @Test
    fun digitSuccessOnDecimalDigit() {
        for (ch in '0'..'9') {
            val input = ParserInput.of(listOf(ch), Unit)
            val result = pDigit<Unit>()(input)
            assertIs<Success<Char, Char, Unit>>(result)
            assertEquals(ch, result.value)
            assertEquals(1, result.nextIndex)
        }
    }

    @Test
    fun digitFailureOnLetter() {
        val input = ParserInput.of(listOf('a'), Unit)
        val result = pDigit<Unit>()(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("digit", result.message)
    }

    @Test
    fun digitFailureOnEmptyInput() {
        val input = ParserInput.of(emptyList<Char>(), Unit)
        assertIs<Failure<Char, Unit>>(pDigit<Unit>()(input))
    }

    // -------------------------------------------------------------------------
    // pHexDigit
    // -------------------------------------------------------------------------

    @Test
    fun hexDigitSuccessOnDecimalDigit() {
        val input = ParserInput.of(listOf('9'), Unit)
        val result = pHexDigit<Unit>()(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('9', result.value)
    }

    @Test
    fun hexDigitSuccessOnLowercaseHex() {
        for (ch in 'a'..'f') {
            val input = ParserInput.of(listOf(ch), Unit)
            assertIs<Success<Char, Char, Unit>>(pHexDigit<Unit>()(input))
        }
    }

    @Test
    fun hexDigitSuccessOnUppercaseHex() {
        for (ch in 'A'..'F') {
            val input = ParserInput.of(listOf(ch), Unit)
            assertIs<Success<Char, Char, Unit>>(pHexDigit<Unit>()(input))
        }
    }

    @Test
    fun hexDigitFailureOnNonHex() {
        val input = ParserInput.of(listOf('g'), Unit)
        val result = pHexDigit<Unit>()(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("hex digit", result.message)
    }

    // -------------------------------------------------------------------------
    // pLetter
    // -------------------------------------------------------------------------

    @Test
    fun letterSuccessOnAsciiLetter() {
        for (ch in listOf('a', 'z', 'A', 'Z')) {
            val input = ParserInput.of(listOf(ch), Unit)
            assertIs<Success<Char, Char, Unit>>(pLetter<Unit>()(input))
        }
    }

    @Test
    fun letterFailureOnDigit() {
        val input = ParserInput.of(listOf('5'), Unit)
        val result = pLetter<Unit>()(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("letter", result.message)
    }

    @Test
    fun letterFailureOnEmptyInput() {
        val input = ParserInput.of(emptyList<Char>(), Unit)
        assertIs<Failure<Char, Unit>>(pLetter<Unit>()(input))
    }

    // -------------------------------------------------------------------------
    // pSpace
    // -------------------------------------------------------------------------

    @Test
    fun spaceSuccessOnSpaceChar() {
        val input = ParserInput.of(listOf(' '), Unit)
        val result = pSpace<Unit>()(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals(' ', result.value)
    }

    @Test
    fun spaceFailureOnTab() {
        val input = ParserInput.of(listOf('\t'), Unit)
        val result = pSpace<Unit>()(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("space", result.message)
    }

    // -------------------------------------------------------------------------
    // pTab
    // -------------------------------------------------------------------------

    @Test
    fun tabSuccessOnTabChar() {
        val input = ParserInput.of(listOf('\t'), Unit)
        val result = pTab<Unit>()(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('\t', result.value)
    }

    @Test
    fun tabFailureOnSpace() {
        val input = ParserInput.of(listOf(' '), Unit)
        val result = pTab<Unit>()(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("tab", result.message)
    }

    // -------------------------------------------------------------------------
    // pSpaceOrTab
    // -------------------------------------------------------------------------

    @Test
    fun spaceOrTabSuccessOnSpace() {
        val input = ParserInput.of(listOf(' '), Unit)
        val result = pSpaceOrTab<Unit>()(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals(' ', result.value)
    }

    @Test
    fun spaceOrTabSuccessOnTab() {
        val input = ParserInput.of(listOf('\t'), Unit)
        val result = pSpaceOrTab<Unit>()(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('\t', result.value)
    }

    @Test
    fun spaceOrTabFailureOnOtherChar() {
        val input = ParserInput.of(listOf('x'), Unit)
        val result = pSpaceOrTab<Unit>()(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("space or tab", result.message)
    }
}
