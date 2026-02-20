package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PAnyTest {

    private val input = ParserInput.of("abc".toList(), Unit)

    @Test
    fun successConsumesFirstToken() {
        val result = pAny<Char, Unit>()(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('a', result.value)
        assertEquals(1, result.nextIndex)
        assertSame(input, result.input)
    }

    @Test
    fun failureAtEndOfInput() {
        val empty = ParserInput.of(emptyList<Char>(), Unit)
        val result = pAny<Char, Unit>()(empty)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("Unexpected end of input", result.message)
        assertEquals(0, result.index)
    }

    @Test
    fun advancesIndexOnConsecutiveCalls() {
        val parser = pAny<Char, Unit>()
        val first = parser(input)
        assertIs<Success<Char, Char, Unit>>(first)
        val second = parser(ParserInput(input.input, first.nextIndex, Unit))
        assertIs<Success<Char, Char, Unit>>(second)
        assertEquals('b', second.value)
        assertEquals(2, second.nextIndex)
    }

    @Test
    fun consumesAllTokensUntilEof() {
        val parser = pAny<Char, Unit>()
        val eof = pEof<Char, Unit>()
        var current = input
        val collected = mutableListOf<Char>()
        while (true) {
            when (val r = parser(current)) {
                is Failure -> break
                is Success -> {
                    collected.add(r.value)
                    current = ParserInput(input.input, r.nextIndex, Unit)
                }
            }
        }
        assertEquals(listOf('a', 'b', 'c'), collected)
        assertIs<Success<Char, Unit, Unit>>(eof(current))
    }

    @Test
    fun worksWithNonCharTokenType() {
        val intInput = ParserInput.of(listOf(1, 2, 3), Unit)
        val result = pAny<Int, Unit>()(intInput)
        assertIs<Success<Int, Int, Unit>>(result)
        assertEquals(1, result.value)
        assertEquals(1, result.nextIndex)
    }
}
