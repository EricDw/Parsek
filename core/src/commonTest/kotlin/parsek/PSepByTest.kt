package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PSepByTest {

    private val digit = pSatisfy<Char, Unit> { it: Char -> it.isDigit() }
    private val comma = pSatisfy<Char, Unit> { it: Char -> it == ',' }

    @Test
    fun successWithEmptyInputReturnsEmptyList() {
        val input = ParserInput.of(emptyList<Char>(), Unit)
        val result = pSepBy(digit, comma)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(emptyList(), result.value)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun successWhenFirstItemFailsReturnsEmptyList() {
        val input = ParserInput.of("abc".toList(), Unit)
        val result = pSepBy(digit, comma)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(emptyList(), result.value)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun successWithSingleItem() {
        val input = ParserInput.of("9".toList(), Unit)
        val result = pSepBy(digit, comma)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('9'), result.value)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun successWithMultipleItems() {
        val input = ParserInput.of("1,2,3".toList(), Unit)
        val result = pSepBy(digit, comma)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('1', '2', '3'), result.value)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun doesNotConsumeTrailingSeparator() {
        val input = ParserInput.of("1,2,".toList(), Unit)
        val result = pSepBy(digit, comma)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('1', '2'), result.value)
        assertEquals(3, result.nextIndex) // stops before trailing ','
    }

    @Test
    fun alwaysSucceedsEvenWhenItemNeverMatches() {
        val input = ParserInput.of("!".toList(), Unit)
        val result = pSepBy(digit, comma)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(emptyList(), result.value)
        assertEquals(0, result.nextIndex)
    }
}
