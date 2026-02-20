package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PNotTest {

    private val input = ParserInput.of("abc".toList(), Unit)
    private val digit = pSatisfy<Char, Unit> { it.isDigit() }
    private val letter = pSatisfy<Char, Unit> { it.isLetter() }

    @Test
    fun successWhenInnerParserFails() {
        val result = pNot(digit)(input)
        assertIs<Success<Char, Unit, Unit>>(result)
        assertEquals(Unit, result.value)
        assertEquals(0, result.nextIndex)
        assertSame(input, result.input)
    }

    @Test
    fun failureWhenInnerParserSucceeds() {
        val result = pNot(letter)(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
        assertSame(input, result.input)
    }

    @Test
    fun doesNotConsumeInputOnSuccess() {
        val notDigit = pNot(digit)
        val peek = notDigit(input)
        assertIs<Success<Char, Unit, Unit>>(peek)
        // The real parser still sees the original token
        val real = letter(ParserInput(input.input, peek.nextIndex, Unit))
        assertIs<Success<Char, Char, Unit>>(real)
        assertEquals('a', real.value)
    }

    @Test
    fun doesNotConsumeInputOnFailure() {
        val result = pNot(letter)(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }

    @Test
    fun successAtEndOfInput() {
        val atEnd = ParserInput.of(emptyList<Char>(), Unit)
        val result = pNot(letter)(atEnd)
        assertIs<Success<Char, Unit, Unit>>(result)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun usedAsNegativeGuardBeforeConsume() {
        // Parse a letter only when not followed by a digit (using pAnd + pNot as guard)
        val notDigitThenLetter = pMap(pAnd(pNot(digit), letter)) { (_, c) -> c }
        val result = notDigitThenLetter(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('a', result.value)
        assertEquals(1, result.nextIndex)

        val digits = ParserInput.of("1bc".toList(), Unit)
        val failed = notDigitThenLetter(digits)
        assertIs<Failure<Char, Unit>>(failed)
        assertEquals(0, failed.index)
    }
}
