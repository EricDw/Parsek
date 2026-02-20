package parsek.text

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PCharCategoryTest {

    // -------------------------------------------------------------------------
    // pAsciiPunctuation
    // -------------------------------------------------------------------------

    @Test
    fun asciiPunctuationSuccessOnEachSpecChar() {
        val specChars = "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
        for (ch in specChars) {
            val input = ParserInput.of(listOf(ch), Unit)
            val result = pAsciiPunctuation<Unit>()(input)
            assertIs<Success<Char, Char, Unit>>(result, "expected success for '$ch'")
            assertEquals(ch, result.value)
        }
    }

    @Test
    fun asciiPunctuationFailureOnLetter() {
        val input = ParserInput.of(listOf('a'), Unit)
        val result = pAsciiPunctuation<Unit>()(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("ASCII punctuation", result.message)
    }

    @Test
    fun asciiPunctuationFailureOnDigit() {
        val input = ParserInput.of(listOf('5'), Unit)
        assertIs<Failure<Char, Unit>>(pAsciiPunctuation<Unit>()(input))
    }

    @Test
    fun asciiPunctuationFailureOnSpace() {
        val input = ParserInput.of(listOf(' '), Unit)
        assertIs<Failure<Char, Unit>>(pAsciiPunctuation<Unit>()(input))
    }

    // -------------------------------------------------------------------------
    // pUnicodePunctuation
    // -------------------------------------------------------------------------

    @Test
    fun unicodePunctuationSuccessOnAsciiPunctuation() {
        val input = ParserInput.of(listOf('!'), Unit)
        assertIs<Success<Char, Char, Unit>>(pUnicodePunctuation<Unit>()(input))
    }

    @Test
    fun unicodePunctuationSuccessOnUnicodeCategory() {
        // '«' is INITIAL_QUOTE_PUNCTUATION (category Pi)
        val input = ParserInput.of(listOf('«'), Unit)
        assertIs<Success<Char, Char, Unit>>(pUnicodePunctuation<Unit>()(input))
    }

    @Test
    fun unicodePunctuationFailureOnLetter() {
        val input = ParserInput.of(listOf('a'), Unit)
        val result = pUnicodePunctuation<Unit>()(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("Unicode punctuation", result.message)
    }

    @Test
    fun unicodePunctuationFailureOnDigit() {
        val input = ParserInput.of(listOf('3'), Unit)
        assertIs<Failure<Char, Unit>>(pUnicodePunctuation<Unit>()(input))
    }

    // -------------------------------------------------------------------------
    // pUnicodeWhitespace
    // -------------------------------------------------------------------------

    @Test
    fun unicodeWhitespaceSuccessOnSpace() {
        val input = ParserInput.of(listOf(' '), Unit)
        assertIs<Success<Char, Char, Unit>>(pUnicodeWhitespace<Unit>()(input))
    }

    @Test
    fun unicodeWhitespaceSuccessOnTab() {
        val input = ParserInput.of(listOf('\t'), Unit)
        assertIs<Success<Char, Char, Unit>>(pUnicodeWhitespace<Unit>()(input))
    }

    @Test
    fun unicodeWhitespaceSuccessOnNewline() {
        val input = ParserInput.of(listOf('\n'), Unit)
        assertIs<Success<Char, Char, Unit>>(pUnicodeWhitespace<Unit>()(input))
    }

    @Test
    fun unicodeWhitespaceSuccessOnCarriageReturn() {
        val input = ParserInput.of(listOf('\r'), Unit)
        assertIs<Success<Char, Char, Unit>>(pUnicodeWhitespace<Unit>()(input))
    }

    @Test
    fun unicodeWhitespaceSuccessOnFormFeed() {
        val input = ParserInput.of(listOf('\u000C'), Unit)
        assertIs<Success<Char, Char, Unit>>(pUnicodeWhitespace<Unit>()(input))
    }

    @Test
    fun unicodeWhitespaceFailureOnLetter() {
        val input = ParserInput.of(listOf('x'), Unit)
        val result = pUnicodeWhitespace<Unit>()(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("Unicode whitespace", result.message)
    }

    @Test
    fun unicodeWhitespaceFailureOnAsciiPunctuation() {
        val input = ParserInput.of(listOf('!'), Unit)
        assertIs<Failure<Char, Unit>>(pUnicodeWhitespace<Unit>()(input))
    }
}
