package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PChoiceTest {

    private val input = ParserInput.of("b".toList(), Unit)
    private val a = pSatisfy<Char, Unit> { it == 'a' }
    private val b = pSatisfy<Char, Unit> { it == 'b' }
    private val c = pSatisfy<Char, Unit> { it == 'c' }

    @Test
    fun successOnFirstMatch() {
        val result = pChoice(listOf(a, b, c))(ParserInput.of("a".toList(), Unit))
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('a', result.value)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun successOnMiddleMatch() {
        val result = pChoice(listOf(a, b, c))(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('b', result.value)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun successOnLastMatch() {
        val result = pChoice(listOf(a, b, c))(ParserInput.of("c".toList(), Unit))
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('c', result.value)
    }

    @Test
    fun failureWhenNoAlternativeMatches() {
        val result = pChoice(listOf(a, b, c))(ParserInput.of("z".toList(), Unit))
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }

    @Test
    fun failureOnEmptyList() {
        val result = pChoice<Char, Char, Unit>(emptyList())(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("No alternatives", result.message)
        assertEquals(0, result.index)
        assertSame(input, result.input)
    }

    @Test
    fun singleElementListBehavesLikeThatParser() {
        val success = pChoice(listOf(b))(input)
        assertIs<Success<Char, Char, Unit>>(success)
        assertEquals('b', success.value)

        val failure = pChoice(listOf(a))(input)
        assertIs<Failure<Char, Unit>>(failure)
    }

    @Test
    fun returnsfurthestFailureWhenAllFail() {
        // First parser consumes 'a' then fails; second fails at 0.
        // pChoice should return the failure at the furthest index.
        val abInput = ParserInput.of("ab".toList(), Unit)
        val consumeAThenFail = pAnd(pSatisfy<Char, Unit> { it == 'a' }, pSatisfy { it == 'z' })
        val failAtZero = pSatisfy<Char, Unit> { it == 'z' }
        val result = pChoice(listOf(consumeAThenFail, failAtZero))(abInput)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(1, result.index)
    }

    @Test
    fun equivalentToChainingPOr() {
        val viaChoice = pChoice(listOf(a, b, c))
        val viaOr = pOr(pOr(a, b), c)
        val inputs = listOf("a", "b", "c", "z").map { ParserInput.of(it.toList(), Unit) }
        for (inp in inputs) {
            val r1 = viaChoice(inp)
            val r2 = viaOr(inp)
            assertEquals(r1::class, r2::class)
        }
    }
}
