package parsek.text

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PIntTest {

    private fun input(s: String) = ParserInput.of(s.toList(), Unit)

    @Test
    fun successUnsignedInt() {
        val result = pInt<Unit>()(input("42"))
        assertIs<Success<Char, Int, Unit>>(result)
        assertEquals(42, result.value)
        assertEquals(2, result.nextIndex)
    }

    @Test
    fun successWithPlusSign() {
        val result = pInt<Unit>()(input("+42"))
        assertIs<Success<Char, Int, Unit>>(result)
        assertEquals(42, result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun successWithMinusSign() {
        val result = pInt<Unit>()(input("-42"))
        assertIs<Success<Char, Int, Unit>>(result)
        assertEquals(-42, result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun successZero() {
        val result = pInt<Unit>()(input("0"))
        assertIs<Success<Char, Int, Unit>>(result)
        assertEquals(0, result.value)
    }

    @Test
    fun successSingleDigit() {
        val result = pInt<Unit>()(input("7"))
        assertIs<Success<Char, Int, Unit>>(result)
        assertEquals(7, result.value)
    }

    @Test
    fun successMaxInt() {
        val result = pInt<Unit>()(input(Int.MAX_VALUE.toString()))
        assertIs<Success<Char, Int, Unit>>(result)
        assertEquals(Int.MAX_VALUE, result.value)
    }

    @Test
    fun successMinInt() {
        val result = pInt<Unit>()(input(Int.MIN_VALUE.toString()))
        assertIs<Success<Char, Int, Unit>>(result)
        assertEquals(Int.MIN_VALUE, result.value)
    }

    @Test
    fun advancesIndexCorrectly() {
        val result = pInt<Unit>()(input("42abc"))
        assertIs<Success<Char, Int, Unit>>(result)
        assertEquals(42, result.value)
        assertEquals(2, result.nextIndex)
    }

    @Test
    fun failureOnEmpty() {
        val result = pInt<Unit>()(input(""))
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("integer", result.message)
        assertEquals(0, result.index)
    }

    @Test
    fun failureOnNonDigit() {
        val result = pInt<Unit>()(input("abc"))
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("integer", result.message)
        assertEquals(0, result.index)
    }

    @Test
    fun failureOnSignOnly() {
        val result = pInt<Unit>()(input("+"))
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("integer", result.message)
    }

    @Test
    fun failureOnOverflow() {
        val overMax = (Int.MAX_VALUE.toLong() + 1).toString()
        val result = pInt<Unit>()(input(overMax))
        assertIs<Failure<Char, Unit>>(result)
        assertTrue(result.message.startsWith("Integer out of range:"))
    }

    @Test
    fun failureOnUnderflow() {
        val underMin = (Int.MIN_VALUE.toLong() - 1).toString()
        val result = pInt<Unit>()(input(underMin))
        assertIs<Failure<Char, Unit>>(result)
        assertTrue(result.message.startsWith("Integer out of range:"))
    }
}
