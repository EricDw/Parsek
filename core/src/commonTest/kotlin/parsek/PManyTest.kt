package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PManyTest {

    private val letter = pSatisfy<Char, Unit> { it.isLetter() }
    private val digit = pSatisfy<Char, Unit> { it.isDigit() }

    @Test
    fun collectsAllMatchingTokens() {
        val input = ParserInput.of("abc!".toList(), Unit)
        val result = pMany(letter)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('a', 'b', 'c'), result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun succeedsWithEmptyListWhenNoTokensMatch() {
        val input = ParserInput.of("123".toList(), Unit)
        val result = pMany(letter)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(emptyList(), result.value)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun succeedsWithEmptyListOnEmptyInput() {
        val empty = ParserInput.of(emptyList<Char>(), Unit)
        val result = pMany(letter)(empty)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(emptyList(), result.value)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun stopsAtFirstNonMatchingToken() {
        val input = ParserInput.of("ab12".toList(), Unit)
        val result = pMany(letter)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('a', 'b'), result.value)
        assertEquals(2, result.nextIndex)
    }

    @Test
    fun consumesEntireInputWhenAllTokensMatch() {
        val input = ParserInput.of("abc".toList(), Unit)
        val result = pMany(letter)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('a', 'b', 'c'), result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun storedInputIsOriginal() {
        val input = ParserInput.of("abc".toList(), Unit)
        val result = pMany(letter)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(input, result.input)
    }
}
