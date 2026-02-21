package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PLinkTitleTest {

    private fun parse(input: String) =
        pLinkTitle<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Double-quoted form
    // -------------------------------------------------------------------------

    @Test
    fun doubleQuotedSimple() {
        val result = parse("\"title\"")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("title", result.value)
    }

    @Test
    fun doubleQuotedEmpty() {
        val result = parse("\"\"")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("", result.value)
    }

    @Test
    fun doubleQuotedBackslashEscape() {
        // "foo\"bar" — '\\"' is a backslash escape inside a double-quoted title
        val result = parse("\"foo\\\"bar\"")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("foo\"bar", result.value)
    }

    // -------------------------------------------------------------------------
    // Single-quoted form
    // -------------------------------------------------------------------------

    @Test
    fun singleQuotedSimple() {
        val result = parse("'title'")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("title", result.value)
    }

    @Test
    fun singleQuotedEmpty() {
        val result = parse("''")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("", result.value)
    }

    // -------------------------------------------------------------------------
    // Paren form
    // -------------------------------------------------------------------------

    @Test
    fun parenSimple() {
        val result = parse("(title)")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("title", result.value)
    }

    @Test
    fun parenEmpty() {
        val result = parse("()")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("", result.value)
    }

    // -------------------------------------------------------------------------
    // Multi-line (no blank line)
    // -------------------------------------------------------------------------

    @Test
    fun multiLineDoubleQuoted() {
        val result = parse("\"foo\nbar\"")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("foo\nbar", result.value)
    }

    // -------------------------------------------------------------------------
    // nextIndex
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexDoubleQuoted() {
        // "\"foo\"" = 5 characters
        val result = parse("\"foo\"")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun nextIndexSingleQuoted() {
        // "'foo'" = 5 characters
        val result = parse("'foo'")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun nextIndexParen() {
        // "(foo)" = 5 characters
        val result = parse("(foo)")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun doesNotConsumeFollowingChars() {
        // "\"foo\"" = 5 characters; " rest" is not consumed.
        val result = parse("\"foo\" rest")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals(5, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureNoOpenDelimiter() {
        assertIs<Failure<Char, Unit>>(parse("title"))
    }

    @Test
    fun failureDoubleQuotedNoClose() {
        assertIs<Failure<Char, Unit>>(parse("\"unclosed"))
    }

    @Test
    fun failureSingleQuotedNoClose() {
        assertIs<Failure<Char, Unit>>(parse("'unclosed"))
    }

    @Test
    fun failureParenNoClose() {
        assertIs<Failure<Char, Unit>>(parse("(unclosed"))
    }

    @Test
    fun failureBlankLineInDoubleQuoted() {
        assertIs<Failure<Char, Unit>>(parse("\"foo\n\nbar\""))
    }

    @Test
    fun failureBlankLineInSingleQuoted() {
        assertIs<Failure<Char, Unit>>(parse("'foo\n\nbar'"))
    }

    @Test
    fun failureBlankLineInParen() {
        assertIs<Failure<Char, Unit>>(parse("(foo\n\nbar)"))
    }

    @Test
    fun failureParenUnescapedOpen() {
        assertIs<Failure<Char, Unit>>(parse("(foo(bar)"))
    }

    @Test
    fun failureEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }
}
