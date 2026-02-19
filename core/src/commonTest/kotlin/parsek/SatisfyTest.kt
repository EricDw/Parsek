package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class SatisfyTest {

    private val digits = ParserInput.of("0123456789".toList(), Unit)
    private val isDigit: (Char) -> Boolean = { it.isDigit() }

    @Test
    fun successOnMatchingElement() {
        val parser = pSatisfy<Char, Unit>(isDigit)
        val result = parser(digits)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('0', result.value)
        assertEquals(1, result.nextIndex)
        assertSame(digits, result.input)
    }

    @Test
    fun failureOnNonMatchingElement() {
        val parser = pSatisfy<Char, Unit> { it == 'z' }
        val result = parser(digits)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
        assertSame(digits, result.input)
    }

    @Test
    fun failureAtEndOfInput() {
        val parser = pSatisfy<Char, Unit>(isDigit)
        val empty = ParserInput.of(emptyList<Char>(), Unit)
        val result = parser(empty)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("Unexpected end of input", result.message)
        assertEquals(0, result.index)
        assertSame(empty, result.input)
    }

    @Test
    fun advancesIndexOnConsecutiveParsing() {
        val parser = pSatisfy<Char, Unit>(isDigit)
        val first = parser(digits)
        assertIs<Success<Char, Char, Unit>>(first)
        val second = parser(ParserInput(digits.input, first.nextIndex, Unit))
        assertIs<Success<Char, Char, Unit>>(second)
        assertEquals('1', second.value)
        assertEquals(2, second.nextIndex)
    }
}
