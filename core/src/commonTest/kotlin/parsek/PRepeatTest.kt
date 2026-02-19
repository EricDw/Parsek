package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PRepeatTest {

    private val input = ParserInput.of("aaa!".toList(), Unit)
    private val letter = pSatisfy<Char, Unit> { it.isLetter() }

    @Test
    fun succeedsWithExactCount() {
        val result = pRepeat(3, letter)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('a', 'a', 'a'), result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun zeroCountSucceedsWithEmptyList() {
        val result = pRepeat(0, letter)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(emptyList(), result.value)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun failsWhenInputExhaustedBeforeCount() {
        val result = pRepeat(4, letter)(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(3, result.index)
    }

    @Test
    fun failsOnFirstTokenMismatch() {
        val bang = pSatisfy<Char, Unit> { it == '!' }
        val result = pRepeat(2, bang)(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }

    @Test
    fun storedInputIsOriginal() {
        val result = pRepeat(3, letter)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(input, result.input)
    }
}
