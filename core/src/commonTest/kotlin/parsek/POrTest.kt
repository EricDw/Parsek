package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class POrTest {

    private val input = ParserInput.of("a1".toList(), Unit)
    private val digit = pSatisfy<Char, Unit> { it.isDigit() }
    private val letter = pSatisfy<Char, Unit> { it.isLetter() }

    @Test
    fun succeedsWithFirstWhenItMatches() {
        val parser = pOr(letter, digit)
        val result = parser(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('a', result.value)
        assertEquals(1, result.nextIndex)
        assertSame(input, result.input)
    }

    @Test
    fun succeedsWithSecondWhenFirstFails() {
        val parser = pOr(digit, letter)
        val result = parser(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('a', result.value)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun prefersFirstOverSecondWhenBothMatch() {
        val alwaysX: Parser<Char, Char, Unit> = Parser { i -> Success('x', i.index + 1, i) }
        val parser = pOr(letter, alwaysX)
        val result = parser(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('a', result.value)
    }

    @Test
    fun failureReturnsFurthestIndexWhenBothFail() {
        val bang = pSatisfy<Char, Unit> { it == '!' }
        // digit fails at index 0, bang fails at index 0 — same index, second wins
        val parser = pOr(digit, bang)
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }

    @Test
    fun failureAtEndOfInput() {
        val empty = ParserInput.of(emptyList<Char>(), Unit)
        val parser = pOr(digit, letter)
        val result = parser(empty)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("Unexpected end of input", result.message)
    }
}
