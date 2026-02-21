package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PLinkDestinationTest {

    private fun parse(input: String) =
        pLinkDestination<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Angle-bracket form
    // -------------------------------------------------------------------------

    @Test
    fun angleBracketSimple() {
        val result = parse("<http://example.com>")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("http://example.com", result.value)
    }

    @Test
    fun angleBracketEmpty() {
        val result = parse("<>")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("", result.value)
    }

    @Test
    fun angleBracketWithSpaces() {
        val result = parse("<url with spaces>")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("url with spaces", result.value)
    }

    @Test
    fun angleBracketBackslashEscape() {
        // "<foo\>bar>" — '\>' is a backslash escape → destination "foo>bar"
        val result = parse("<foo\\>bar>")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("foo>bar", result.value)
    }

    // -------------------------------------------------------------------------
    // Bare form
    // -------------------------------------------------------------------------

    @Test
    fun bareSimple() {
        val result = parse("http://example.com")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("http://example.com", result.value)
    }

    @Test
    fun bareWithBalancedParens() {
        val result = parse("foo(bar)baz")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("foo(bar)baz", result.value)
    }

    @Test
    fun bareNestedParens() {
        val result = parse("((foo))")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("((foo))", result.value)
    }

    @Test
    fun bareStopsAtSpace() {
        // Parser stops at the space; only "foo" is consumed.
        val result = parse("foo bar")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("foo", result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun bareStopsAtUnmatchedCloseParen() {
        // Unmatched ')' at depth 0 stops the scan.
        val result = parse("foo)bar")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("foo", result.value)
        assertEquals(3, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // nextIndex
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexAngleBracket() {
        // "<foo>" = 5 characters
        val result = parse("<foo>")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun nextIndexBare() {
        // "foo" = 3 characters
        val result = parse("foo")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun doesNotConsumeFollowingChars() {
        // "<foo>" = 5 characters; " rest" is not consumed.
        val result = parse("<foo> rest")
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals(5, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureAngleBracketNoClose() {
        assertIs<Failure<Char, Unit>>(parse("<unclosed"))
    }

    @Test
    fun failureAngleBracketLineEnding() {
        assertIs<Failure<Char, Unit>>(parse("<foo\nbar>"))
    }

    @Test
    fun failureAngleBracketUnescapedOpen() {
        assertIs<Failure<Char, Unit>>(parse("<foo<bar>"))
    }

    @Test
    fun failureBareEmpty() {
        // A bare destination starting with a space is empty → fails.
        assertIs<Failure<Char, Unit>>(parse(" foo"))
    }

    @Test
    fun failureBareUnbalancedParens() {
        assertIs<Failure<Char, Unit>>(parse("foo(bar"))
    }

    @Test
    fun failureEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }
}
