package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PAndTest {

    private val input = ParserInput.of("1a!".toList(), Unit)
    private val digit = pSatisfy<Char, Unit> { it.isDigit() }
    private val letter = pSatisfy<Char, Unit> { it.isLetter() }
    private val bang = pSatisfy<Char, Unit> { it == '!' }

    @Test
    fun successWhenBothParsersMatch() {
        val parser = pAnd(digit, letter)
        val result = parser(input)
        assertIs<Success<Char, Pair<Char, Char>, Unit>>(result)
        assertEquals('1' to 'a', result.value)
        assertEquals(2, result.nextIndex)
        assertSame(input, result.input)
    }

    @Test
    fun advancesPastBothTokens() {
        val parser = pAnd(pAnd(digit, letter), bang)
        val result = parser(input)
        assertIs<Success<Char, Pair<Pair<Char, Char>, Char>, Unit>>(result)
        assertEquals(('1' to 'a') to '!', result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun failureWhenFirstParserFails() {
        val parser = pAnd(letter, digit)
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }

    @Test
    fun failureWhenSecondParserFails() {
        val parser = pAnd(digit, digit)
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(1, result.index)
    }

    @Test
    fun failureAtEndOfInput() {
        val empty = ParserInput.of(emptyList<Char>(), Unit)
        val parser = pAnd(digit, letter)
        val result = parser(empty)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("Unexpected end of input", result.message)
    }
}
