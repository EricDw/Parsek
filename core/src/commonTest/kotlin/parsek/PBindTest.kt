package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PBindTest {

    private val digit = pSatisfy<Char, Unit> { it.isDigit() }
    private val letter = pSatisfy<Char, Unit> { it.isLetter() }

    @Test
    fun succeedsWhenBothParsersMatch() {
        val parser = pBind(digit) { pMap(letter) { l -> "$it$l" } }
        val input = ParserInput.of("3a".toList(), Unit)
        val result = parser(input)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("3a", result.value)
        assertEquals(2, result.nextIndex)
    }

    @Test
    fun propagatesFailureFromFirstParser() {
        val parser = pBind(letter) { pMap(digit) { d -> "$it$d" } }
        val input = ParserInput.of("3a".toList(), Unit)
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }

    @Test
    fun propagatesFailureFromDerivedParser() {
        val parser = pBind(digit) { pMap(digit) { d -> "$it$d" } }
        val input = ParserInput.of("3a".toList(), Unit)
        val result = parser(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(1, result.index)
    }

    @Test
    fun derivedParserDependsOnFirstValue() {
        // Parse a digit n, then parse exactly n letters.
        val countedLetters = pBind(pMap(digit) { it.digitToInt() }) { count ->
            (2..count).fold(pMap(letter) { listOf(it) }) { acc, _ ->
                pMap(pAnd(acc, letter)) { (list, ch) -> list + ch }
            }
        }
        val input = ParserInput.of("3abc".toList(), Unit)
        val result = countedLetters(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('a', 'b', 'c'), result.value)
        assertEquals(4, result.nextIndex)
    }
}
