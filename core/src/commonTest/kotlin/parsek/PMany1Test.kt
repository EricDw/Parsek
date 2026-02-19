package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PMany1Test {

    private val letter = pSatisfy<Char, Unit> { it.isLetter() }
    private val digit = pSatisfy<Char, Unit> { it.isDigit() }

    @Test
    fun collectsAllMatchingTokens() {
        val input = ParserInput.of("abc!".toList(), Unit)
        val result = pMany1(letter)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('a', 'b', 'c'), result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun succeedsWithSingleMatch() {
        val input = ParserInput.of("a1".toList(), Unit)
        val result = pMany1(letter)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('a'), result.value)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun failsWhenNoTokensMatch() {
        val input = ParserInput.of("123".toList(), Unit)
        val result = pMany1(letter)(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }

    @Test
    fun failsOnEmptyInput() {
        val empty = ParserInput.of(emptyList<Char>(), Unit)
        val result = pMany1(letter)(empty)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }

    @Test
    fun stopsAtFirstNonMatchingToken() {
        val input = ParserInput.of("ab12".toList(), Unit)
        val result = pMany1(letter)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('a', 'b'), result.value)
        assertEquals(2, result.nextIndex)
    }
}
