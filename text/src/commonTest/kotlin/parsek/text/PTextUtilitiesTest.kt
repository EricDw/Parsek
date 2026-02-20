package parsek.text

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PTextUtilitiesTest {

    // -------------------------------------------------------------------------
    // pTakeWhile
    // -------------------------------------------------------------------------

    @Test
    fun takeWhileConsumesMatchingCharacters() {
        val input = ParserInput.of("abc123".toList(), Unit)
        val result = pTakeWhile<Unit> { it.isLetter() }(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("abc", result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun takeWhileReturnsEmptyStringWhenFirstCharFails() {
        val input = ParserInput.of("123".toList(), Unit)
        val result = pTakeWhile<Unit> { it.isLetter() }(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("", result.value)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun takeWhileConsumesEntireInputWhenAllMatch() {
        val input = ParserInput.of("hello".toList(), Unit)
        val result = pTakeWhile<Unit> { it.isLetter() }(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("hello", result.value)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun takeWhileAlwaysSucceedsOnEmptyInput() {
        val input = ParserInput.of(emptyList<Char>(), Unit)
        val result = pTakeWhile<Unit> { it.isLetter() }(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("", result.value)
    }

    // -------------------------------------------------------------------------
    // pTakeWhile1
    // -------------------------------------------------------------------------

    @Test
    fun takeWhile1ConsumesMatchingCharacters() {
        val input = ParserInput.of("abc123".toList(), Unit)
        val result = pTakeWhile1<Unit> { it.isLetter() }(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("abc", result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun takeWhile1FailsWhenFirstCharDoesNotMatch() {
        val input = ParserInput.of("123".toList(), Unit)
        val result = pTakeWhile1<Unit> { it.isLetter() }(input)
        assertIs<Failure<Char, Unit>>(result)
    }

    @Test
    fun takeWhile1FailsOnEmptyInput() {
        val input = ParserInput.of(emptyList<Char>(), Unit)
        assertIs<Failure<Char, Unit>>(pTakeWhile1<Unit> { it.isLetter() }(input))
    }

    // -------------------------------------------------------------------------
    // pIndent
    // -------------------------------------------------------------------------

    @Test
    fun indentSuccessConsumesExactSpaces() {
        val input = ParserInput.of("   abc".toList(), Unit)
        val result = pIndent<Unit>(3)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf(' ', ' ', ' '), result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun indentZeroAlwaysSucceeds() {
        val input = ParserInput.of("abc".toList(), Unit)
        val result = pIndent<Unit>(0)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(emptyList(), result.value)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun indentFailsWhenTooFewSpaces() {
        val input = ParserInput.of("  abc".toList(), Unit) // only 2 spaces
        val result = pIndent<Unit>(4)(input)
        assertIs<Failure<Char, Unit>>(result)
    }

    // -------------------------------------------------------------------------
    // pUpTo3Spaces
    // -------------------------------------------------------------------------

    @Test
    fun upTo3SpacesZeroSpaces() {
        val input = ParserInput.of("abc".toList(), Unit)
        val result = pUpTo3Spaces<Unit>()(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(emptyList(), result.value)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun upTo3SpacesOneSpace() {
        val input = ParserInput.of(" abc".toList(), Unit)
        val result = pUpTo3Spaces<Unit>()(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(1, result.value.size)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun upTo3SpacesThreeSpaces() {
        val input = ParserInput.of("   abc".toList(), Unit)
        val result = pUpTo3Spaces<Unit>()(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(3, result.value.size)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun upTo3SpacesStopsAtThreeEvenIfMorePresent() {
        val input = ParserInput.of("    abc".toList(), Unit) // 4 spaces
        val result = pUpTo3Spaces<Unit>()(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(3, result.value.size)
        assertEquals(3, result.nextIndex) // 4th space left unconsumed
    }

    @Test
    fun upTo3SpacesAlwaysSucceedsOnEmptyInput() {
        val input = ParserInput.of(emptyList<Char>(), Unit)
        val result = pUpTo3Spaces<Unit>()(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(emptyList(), result.value)
    }
}
