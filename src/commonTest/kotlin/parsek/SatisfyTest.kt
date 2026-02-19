package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class SatisfyTest {

    private val digits = ParserInput.of("0123456789".toList())
    private val isDigit: (Char) -> Boolean = { it.isDigit() }

    @Test
    fun successOnMatchingElement() {
        val parser = satisfy(isDigit)
        val result = parser(digits)
        assertIs<Success<Char, Char>>(result)
        assertEquals('0', result.value)
        assertEquals(1, result.nextIndex)
        assertSame(digits, result.input)
    }

    @Test
    fun failureOnNonMatchingElement() {
        val parser = satisfy<Char> { it == 'z' }
        val result = parser(digits)
        assertIs<Failure<Char>>(result)
        assertEquals(0, result.index)
        assertSame(digits, result.input)
    }

    @Test
    fun failureAtEndOfInput() {
        val parser = satisfy(isDigit)
        val empty = ParserInput.of(emptyList<Char>())
        val result = parser(empty)
        assertIs<Failure<Char>>(result)
        assertEquals("Unexpected end of input", result.message)
        assertEquals(0, result.index)
        assertSame(empty, result.input)
    }

    @Test
    fun advancesIndexOnConsecutiveParsing() {
        val parser = satisfy(isDigit)
        val first = parser(digits)
        assertIs<Success<Char, Char>>(first)
        val second = parser(ParserInput(digits.input, first.nextIndex))
        assertIs<Success<Char, Char>>(second)
        assertEquals('1', second.value)
        assertEquals(2, second.nextIndex)
    }
}
