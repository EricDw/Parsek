package parsek.text

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PCharTest {

    private val input = ParserInput.of("Hello".toList(), Unit)

    @Test
    fun successOnMatchingChar() {
        val parser = pChar<Unit>('H')
        val result = parser(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('H', result.value)
        assertEquals(1, result.nextIndex)
        assertSame(input, result.input)
    }

    @Test
    fun failureOnNonMatchingChar() {
        val parser = pChar<Unit>('z')
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
        assertSame(input, result.input)
    }

    @Test
    fun failureAtEndOfInput() {
        val parser = pChar<Unit>('H')
        val empty = ParserInput.of(emptyList<Char>(), Unit)
        val result = parser(empty)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("Unexpected end of input", result.message)
        assertEquals(0, result.index)
    }

    @Test
    fun advancesIndexOnConsecutiveParsing() {
        val h = pChar<Unit>('H')
        val e = pChar<Unit>('e')
        val first = h(input)
        assertIs<Success<Char, Char, Unit>>(first)
        val second = e(ParserInput(input.input, first.nextIndex, Unit))
        assertIs<Success<Char, Char, Unit>>(second)
        assertEquals('e', second.value)
        assertEquals(2, second.nextIndex)
    }

    @Test
    fun successIgnoreCase() {
        val parser = pChar<Unit>('h', ignoreCase = true)
        val result = parser(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('H', result.value)
    }

    @Test
    fun failureIgnoreCaseWithDifferentChar() {
        val parser = pChar<Unit>('z', ignoreCase = true)
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }
}
