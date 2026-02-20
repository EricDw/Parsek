package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PBetweenTest {

    private val open  = pSatisfy<Char, Unit> { it: Char -> it == '(' }
    private val close = pSatisfy<Char, Unit> { it: Char -> it == ')' }
    private val digit = pSatisfy<Char, Unit> { it: Char -> it.isDigit() }

    @Test
    fun successReturnsInnerValue() {
        val input = ParserInput.of("(5)".toList(), Unit)
        val result = pBetween(open, close, digit)(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('5', result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun failureWhenOpenFails() {
        val input = ParserInput.of("5)".toList(), Unit)
        val result = pBetween(open, close, digit)(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }

    @Test
    fun failureWhenInnerFails() {
        val input = ParserInput.of("(x)".toList(), Unit)
        val result = pBetween(open, close, digit)(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(1, result.index)
    }

    @Test
    fun failureWhenCloseFails() {
        val input = ParserInput.of("(5x".toList(), Unit)
        val result = pBetween(open, close, digit)(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(2, result.index)
    }

    @Test
    fun delimiterValuesAreDiscarded() {
        val input = ParserInput.of("(3)".toList(), Unit)
        val result = pBetween(open, close, digit)(input)
        assertIs<Success<Char, Char, Unit>>(result)
        // result.value is '3', not a Pair or Triple
        assertEquals('3', result.value)
    }

    @Test
    fun worksWithDifferentOpenAndCloseTypes() {
        val openBracket  = pSatisfy<Char, Unit> { it: Char -> it == '[' }
        val closeBracket = pSatisfy<Char, Unit> { it: Char -> it == ']' }
        val input = ParserInput.of("[7]".toList(), Unit)
        val result = pBetween(openBracket, closeBracket, digit)(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('7', result.value)
        assertEquals(3, result.nextIndex)
    }
}
