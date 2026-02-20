package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PEofTest {

    @Test
    fun successOnEmptyInput() {
        val empty = ParserInput.of(emptyList<Char>(), Unit)
        val result = pEof<Char, Unit>()(empty)
        assertIs<Success<Char, Unit, Unit>>(result)
        assertEquals(Unit, result.value)
        assertEquals(0, result.nextIndex)
        assertSame(empty, result.input)
    }

    @Test
    fun successAfterAllInputConsumed() {
        val input = ParserInput.of(listOf('a'), Unit)
        val atEnd = ParserInput(input.input, 1, Unit)
        val result = pEof<Char, Unit>()(atEnd)
        assertIs<Success<Char, Unit, Unit>>(result)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun failureWithRemainingInput() {
        val input = ParserInput.of("abc".toList(), Unit)
        val result = pEof<Char, Unit>()(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
        assertSame(input, result.input)
    }

    @Test
    fun failureMessageContainsUnexpectedToken() {
        val input = ParserInput.of("z".toList(), Unit)
        val result = pEof<Char, Unit>()(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("Expected end of input but got z at index 0", result.message)
    }

    @Test
    fun doesNotConsumeInputOnSuccess() {
        val empty = ParserInput.of(emptyList<Int>(), Unit)
        val result = pEof<Int, Unit>()(empty)
        assertIs<Success<Int, Unit, Unit>>(result)
        assertEquals(0, result.nextIndex)
    }
}
