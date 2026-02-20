package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PLabelTest {

    private val input = ParserInput.of("abc".toList(), Unit)
    private val digit = pSatisfy<Char, Unit> { it.isDigit() }
    private val letter = pSatisfy<Char, Unit> { it.isLetter() }

    @Test
    fun successPassesThroughUnchanged() {
        val parser = pLabel(letter, "a letter")
        val result = parser(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('a', result.value)
        assertEquals(1, result.nextIndex)
        assertSame(input, result.input)
    }

    @Test
    fun failureMessageIsReplaced() {
        val parser = pLabel(digit, "a digit")
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("a digit", result.message)
    }

    @Test
    fun failureIndexIsPreserved() {
        val parser = pLabel(digit, "a digit")
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
        assertSame(input, result.input)
    }

    @Test
    fun failureAtEndOfInputMessageIsReplaced() {
        val empty = ParserInput.of(emptyList<Char>(), Unit)
        val parser = pLabel(letter, "a letter")
        val result = parser(empty)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("a letter", result.message)
        assertEquals(0, result.index)
    }

    @Test
    fun labelOnComposedParser() {
        val twoDigits = pLabel(pAnd(digit, digit), "two digits")
        val result = twoDigits(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("two digits", result.message)
    }
}
