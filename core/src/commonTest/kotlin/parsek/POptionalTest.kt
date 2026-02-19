package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class POptionalTest {

    private val letter = pSatisfy<Char, Unit> { it.isLetter() }

    @Test
    fun returnsValueWhenParserMatches() {
        val input = ParserInput.of("a1".toList(), Unit)
        val result = pOptional(letter)(input)
        assertIs<Success<Char, Char?, Unit>>(result)
        assertEquals('a', result.value)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun returnsNullWhenParserFails() {
        val input = ParserInput.of("1a".toList(), Unit)
        val result = pOptional(letter)(input)
        assertIs<Success<Char, Char?, Unit>>(result)
        assertNull(result.value)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun returnsNullOnEmptyInput() {
        val empty = ParserInput.of(emptyList<Char>(), Unit)
        val result = pOptional(letter)(empty)
        assertIs<Success<Char, Char?, Unit>>(result)
        assertNull(result.value)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun doesNotConsumeInputOnMiss() {
        val digit = pSatisfy<Char, Unit> { it.isDigit() }
        val input = ParserInput.of("1".toList(), Unit)
        val opt = pOptional(letter)(input)
        assertIs<Success<Char, Char?, Unit>>(opt)
        val next = digit(ParserInput(input.input, opt.nextIndex, Unit))
        assertIs<Success<Char, Char, Unit>>(next)
        assertEquals('1', next.value)
    }

    @Test
    fun storedInputIsOriginal() {
        val input = ParserInput.of("a".toList(), Unit)
        val result = pOptional(letter)(input)
        assertIs<Success<Char, Char?, Unit>>(result)
        assertEquals(input, result.input)
    }
}
