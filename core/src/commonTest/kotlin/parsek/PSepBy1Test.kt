package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PSepBy1Test {

    private val digit = pSatisfy<Char, Unit> { it: Char -> it.isDigit() }
    private val comma = pSatisfy<Char, Unit> { it: Char -> it == ',' }

    @Test
    fun failureWhenInputIsEmpty() {
        val input = ParserInput.of(emptyList<Char>(), Unit)
        val result = pSepBy1(digit, comma)(input)
        assertIs<Failure<Char, Unit>>(result)
    }

    @Test
    fun failureWhenFirstItemFails() {
        val input = ParserInput.of("abc".toList(), Unit)
        val result = pSepBy1(digit, comma)(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }

    @Test
    fun successWithSingleItem() {
        val input = ParserInput.of("7".toList(), Unit)
        val result = pSepBy1(digit, comma)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('7'), result.value)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun successWithMultipleItems() {
        val input = ParserInput.of("1,2,3".toList(), Unit)
        val result = pSepBy1(digit, comma)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('1', '2', '3'), result.value)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun doesNotConsumeTrailingSeparator() {
        val input = ParserInput.of("1,2,".toList(), Unit)
        val result = pSepBy1(digit, comma)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('1', '2'), result.value)
        assertEquals(3, result.nextIndex) // stops before trailing ','
    }

    @Test
    fun separatorValueIsDiscarded() {
        val input = ParserInput.of("4,5".toList(), Unit)
        val result = pSepBy1(digit, comma)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('4', '5'), result.value)
    }
}
