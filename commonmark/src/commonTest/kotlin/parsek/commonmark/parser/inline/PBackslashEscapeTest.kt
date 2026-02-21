package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PBackslashEscapeTest {

    private fun parse(input: String) =
        pBackslashEscape<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Various ASCII punctuation characters
    // -------------------------------------------------------------------------

    @Test
    fun exclamation() {
        val result = parse("\\!")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("!"), result.value)
    }

    @Test
    fun dot() {
        val result = parse("\\.")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("."), result.value)
    }

    @Test
    fun backslash() {
        val result = parse("\\\\")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("\\"), result.value)
    }

    @Test
    fun asterisk() {
        val result = parse("\\*")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("*"), result.value)
    }

    @Test
    fun openBracket() {
        val result = parse("\\[")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("["), result.value)
    }

    @Test
    fun closeBracket() {
        val result = parse("\\]")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("]"), result.value)
    }

    @Test
    fun hash() {
        val result = parse("\\#")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("#"), result.value)
    }

    @Test
    fun underscore() {
        val result = parse("\\_")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("_"), result.value)
    }

    @Test
    fun backtick() {
        val result = parse("\\`")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("`"), result.value)
    }

    @Test
    fun atSign() {
        val result = parse("\\@")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Text("@"), result.value)
    }

    // -------------------------------------------------------------------------
    // nextIndex
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexIsTwo() {
        val result = parse("\\!")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(2, result.nextIndex)
    }

    @Test
    fun doesNotConsumeFollowingChars() {
        val result = parse("\\!abc")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(2, result.nextIndex)
        assertEquals(Inline.Text("!"), result.value)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureOnLetter() {
        assertIs<Failure<Char, Unit>>(parse("\\a"))
    }

    @Test
    fun failureOnDigit() {
        assertIs<Failure<Char, Unit>>(parse("\\1"))
    }

    @Test
    fun failureOnSpace() {
        assertIs<Failure<Char, Unit>>(parse("\\ "))
    }

    @Test
    fun failureOnBackslashAtEof() {
        assertIs<Failure<Char, Unit>>(parse("\\"))
    }

    @Test
    fun failureOnEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureOnNoBackslash() {
        assertIs<Failure<Char, Unit>>(parse("!"))
    }
}
