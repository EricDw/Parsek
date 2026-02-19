package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PSequenceTest {

    private val input = ParserInput.of("1a2".toList(), Unit)
    private val digit = pSatisfy<Char, Unit> { it.isDigit() }
    private val letter = pSatisfy<Char, Unit> { it.isLetter() }

    @Test
    fun successRunsAllParsersInOrder() {
        val parser = pSequence(listOf(digit, letter, digit))
        val result = parser(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('1', 'a', '2'), result.value)
        assertEquals(3, result.nextIndex)
        assertSame(input, result.input)
    }

    @Test
    fun failureOnFirstParserMismatch() {
        val parser = pSequence(listOf(letter, digit))
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
        assertSame(input, result.input)
    }

    @Test
    fun failureOnLaterParserMismatch() {
        val parser = pSequence(listOf(digit, digit))
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(1, result.index)
    }

    @Test
    fun emptyListAlwaysSucceeds() {
        val parser = pSequence<Char, Char, Unit>(emptyList())
        val result = parser(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(emptyList(), result.value)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun failureAtEndOfInput() {
        val parser = pSequence(listOf(digit, letter, digit, digit))
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("Unexpected end of input", result.message)
        assertEquals(3, result.index)
    }
}
