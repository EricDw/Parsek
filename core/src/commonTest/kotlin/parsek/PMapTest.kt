package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PMapTest {

    private val input = ParserInput.of("3a".toList(), Unit)
    private val digit = pSatisfy<Char, Unit> { it.isDigit() }
    private val letter = pSatisfy<Char, Unit> { it.isLetter() }

    @Test
    fun transformsValueOnSuccess() {
        val parser = pMap(digit) { it.digitToInt() }
        val result = parser(input)
        assertIs<Success<Char, Int, Unit>>(result)
        assertEquals(3, result.value)
        assertEquals(1, result.nextIndex)
        assertSame(input, result.input)
    }

    @Test
    fun propagatesFailureUnchanged() {
        val parser = pMap(letter) { it.uppercaseChar() }
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }

    @Test
    fun composesWithPAnd() {
        val pair = pAnd(digit, letter)
        val parser = pMap(pair) { (d, l) -> "$d$l" }
        val result = parser(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("3a", result.value)
        assertEquals(2, result.nextIndex)
    }

    @Test
    fun chainedMapsApplyInOrder() {
        val parser = pMap(pMap(digit) { it.digitToInt() }) { it * 2 }
        val result = parser(input)
        assertIs<Success<Char, Int, Unit>>(result)
        assertEquals(6, result.value)
    }
}
