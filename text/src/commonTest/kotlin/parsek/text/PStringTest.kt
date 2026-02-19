package parsek.text

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PStringTest {

    private val input = ParserInput.of("Hello, World!".toList(), Unit)

    @Test
    fun successOnMatchingString() {
        val parser = pString<Unit>("Hello")
        val result = parser(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("Hello", result.value)
        assertEquals(5, result.nextIndex)
        assertSame(input, result.input)
    }

    @Test
    fun failureOnNonMatchingString() {
        val parser = pString<Unit>("World")
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
        assertSame(input, result.input)
    }

    @Test
    fun failureAtEndOfInput() {
        val parser = pString<Unit>("Hello")
        val short = ParserInput.of("He".toList(), Unit)
        val result = parser(short)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("Unexpected end of input", result.message)
    }

    @Test
    fun emptyStringAlwaysSucceeds() {
        val parser = pString<Unit>("")
        val result = parser(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("", result.value)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun advancesIndexCorrectly() {
        val hello = pString<Unit>("Hello")
        val first = hello(input)
        assertIs<Success<Char, String, Unit>>(first)
        val comma = pChar<Unit>(',')
        val second = comma(ParserInput(input.input, first.nextIndex, Unit))
        assertIs<Success<Char, Char, Unit>>(second)
        assertEquals(',', second.value)
        assertEquals(6, second.nextIndex)
    }

    @Test
    fun successIgnoreCase() {
        val parser = pString<Unit>("hello", ignoreCase = true)
        val result = parser(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("Hello", result.value)
    }

    @Test
    fun failureIgnoreCaseWithDifferentString() {
        val parser = pString<Unit>("world", ignoreCase = true)
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }
}
