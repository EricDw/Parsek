package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PLinkLabelTest {

    private fun parse(input: String) =
        pLinkLabel<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Basic cases
    // -------------------------------------------------------------------------

    @Test
    fun simpleLabel() {
        val result = parse("[foo]")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("foo", result.value)
    }

    @Test
    fun labelWithSpaces() {
        val result = parse("[foo bar]")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("foo bar", result.value)
    }

    // -------------------------------------------------------------------------
    // Normalisation: case folding
    // -------------------------------------------------------------------------

    @Test
    fun caseFolded() {
        val result = parse("[Foo Bar]")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("foo bar", result.value)
    }

    @Test
    fun allUpperCase() {
        val result = parse("[FOO]")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("foo", result.value)
    }

    // -------------------------------------------------------------------------
    // Normalisation: whitespace collapse
    // -------------------------------------------------------------------------

    @Test
    fun whitespaceCollapsed() {
        val result = parse("[foo  bar]")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("foo bar", result.value)
    }

    @Test
    fun leadingTrailingWhitespace() {
        val result = parse("[  foo  ]")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("foo", result.value)
    }

    // -------------------------------------------------------------------------
    // Backslash escapes (preserved verbatim in label)
    // -------------------------------------------------------------------------

    @Test
    fun withBackslashEscape() {
        // "[foo\*bar]" — the '\*' is a backslash escape; preserved in label
        val result = parse("[foo\\*bar]")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("foo\\*bar", result.value)
    }

    // -------------------------------------------------------------------------
    // nextIndex
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexSimple() {
        // "[foo]" = 5 characters
        val result = parse("[foo]")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun nextIndexLonger() {
        // "[foo bar]" = 9 characters
        val result = parse("[foo bar]")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals(9, result.nextIndex)
    }

    @Test
    fun doesNotConsumeFollowingChars() {
        // "[foo] rest" — only "[foo]" (5 chars) is consumed.
        val result = parse("[foo] rest")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals(5, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureEmptyLabel() {
        // "[]" — blank after normalisation → fails
        assertIs<Failure<Char, Unit>>(parse("[]"))
    }

    @Test
    fun failureWhitespaceOnlyLabel() {
        // "[   ]" — blank after normalisation → fails
        assertIs<Failure<Char, Unit>>(parse("[   ]"))
    }

    @Test
    fun failureNoOpenBracket() {
        assertIs<Failure<Char, Unit>>(parse("foo]"))
    }

    @Test
    fun failureNoCloseBracket() {
        assertIs<Failure<Char, Unit>>(parse("[foo"))
    }

    @Test
    fun failureUnescapedOpenBracket() {
        assertIs<Failure<Char, Unit>>(parse("[foo[bar]"))
    }

    @Test
    fun failureTooLong() {
        // Content > 999 characters → fails
        val input = "[" + "a".repeat(1000) + "]"
        assertIs<Failure<Char, Unit>>(parse(input))
    }

    @Test
    fun failureEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }
}
