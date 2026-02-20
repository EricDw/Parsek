package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PLookAheadTest {

    private val input = ParserInput.of("abc".toList(), Unit)
    private val letter = pSatisfy<Char, Unit> { it.isLetter() }

    @Test
    fun successDoesNotConsumeInput() {
        val result = pLookAhead(letter)(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('a', result.value)
        assertEquals(0, result.nextIndex)
        assertSame(input, result.input)
    }

    @Test
    fun failurePropagatedUnchanged() {
        val digit = pSatisfy<Char, Unit> { it.isDigit() }
        val result = pLookAhead(digit)(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
        assertSame(input, result.input)
    }

    @Test
    fun doesNotAdvanceIndexOnSuccess() {
        val peek = pLookAhead(letter)
        val first = peek(input)
        assertIs<Success<Char, Char, Unit>>(first)
        // Running the real parser after lookahead still sees the same token
        val second = letter(ParserInput(input.input, first.nextIndex, Unit))
        assertIs<Success<Char, Char, Unit>>(second)
        assertEquals('a', second.value)
        assertEquals(1, second.nextIndex)
    }

    @Test
    fun worksAtEndOfInput() {
        val atEnd = ParserInput.of(emptyList<Char>(), Unit)
        val result = pLookAhead(letter)(atEnd)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }

    @Test
    fun canPeekMultiTokenParser() {
        val twoLetters = pAnd(letter, letter)
        val result = pLookAhead(twoLetters)(input)
        assertIs<Success<Char, Pair<Char, Char>, Unit>>(result)
        assertEquals('a' to 'b', result.value)
        assertEquals(0, result.nextIndex)
    }
}
