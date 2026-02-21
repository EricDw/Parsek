package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PTextTest {

    private fun parse(input: String) =
        pText<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Safe character batching
    // -------------------------------------------------------------------------

    @Test
    fun batchesSafeCharacters() {
        val result = parse("hello world")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("hello world"), result.value)
        assertEquals(11, result.nextIndex)
    }

    @Test
    fun stopsAtSpecialChar() {
        val result = parse("hello*world")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("hello"), result.value)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun stopsAtBackslash() {
        val result = parse("foo\\bar")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("foo"), result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun stopsAtBacktick() {
        val result = parse("foo`bar")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("foo"), result.value)
    }

    @Test
    fun stopsAtOpenBracket() {
        val result = parse("foo[bar")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("foo"), result.value)
    }

    @Test
    fun stopsAtAmpersand() {
        val result = parse("foo&bar")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("foo"), result.value)
    }

    @Test
    fun stopsAtAngleBracket() {
        val result = parse("foo<bar")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("foo"), result.value)
    }

    @Test
    fun stopsAtExclamation() {
        val result = parse("foo!bar")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("foo"), result.value)
    }

    @Test
    fun stopsAtLineEnding() {
        val result = parse("foo\nbar")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("foo"), result.value)
    }

    // -------------------------------------------------------------------------
    // Single special character fallback
    // -------------------------------------------------------------------------

    @Test
    fun singleSpecialChar() {
        // When the first char is special, consume exactly one
        val result = parse("*rest")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("*"), result.value)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun singleUnderscore() {
        val result = parse("_rest")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("_"), result.value)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun singleCloseBracket() {
        // ']' is a safe char — it doesn't start any parser
        val result = parse("]rest")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("]rest"), result.value)
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    fun singleCharInput() {
        val result = parse("x")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("x"), result.value)
    }

    @Test
    fun failureEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }
}
