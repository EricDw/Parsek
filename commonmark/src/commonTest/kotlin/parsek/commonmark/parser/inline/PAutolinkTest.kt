package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PAutolinkTest {

    private fun parse(input: String) =
        pAutolink<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // URI autolinks
    // -------------------------------------------------------------------------

    @Test
    fun httpUri() {
        val result = parse("<http://example.com>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Autolink("http://example.com"), result.value)
    }

    @Test
    fun httpsUri() {
        val result = parse("<https://foo.bar.baz/path?q=1>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Autolink("https://foo.bar.baz/path?q=1"), result.value)
    }

    @Test
    fun minimalScheme() {
        // 2-letter scheme is the minimum
        val result = parse("<ab:path>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Autolink("ab:path"), result.value)
    }

    @Test
    fun emptyPath() {
        // Path may be empty after the colon
        val result = parse("<foo:>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Autolink("foo:"), result.value)
    }

    @Test
    fun ftpUri() {
        val result = parse("<ftp://files.example.com/file.txt>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Autolink("ftp://files.example.com/file.txt"), result.value)
    }

    // -------------------------------------------------------------------------
    // Email autolinks
    // -------------------------------------------------------------------------

    @Test
    fun simpleEmail() {
        val result = parse("<foo@bar.com>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Autolink("foo@bar.com"), result.value)
    }

    @Test
    fun emailWithSubdomain() {
        val result = parse("<foo@bar.example.com>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Autolink("foo@bar.example.com"), result.value)
    }

    @Test
    fun emailWithSpecialLocalChars() {
        val result = parse("<foo+special@Bar.baz-bar0.com>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Autolink("foo+special@Bar.baz-bar0.com"), result.value)
    }

    @Test
    fun emailSingleComponentDomain() {
        // A domain with no dots is valid
        val result = parse("<a@b>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.Autolink("a@b"), result.value)
    }

    // -------------------------------------------------------------------------
    // nextIndex
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexUri() {
        // "<http://foo.com>" = 16 characters
        val result = parse("<http://foo.com>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(16, result.nextIndex)
    }

    @Test
    fun nextIndexEmail() {
        // "<foo@bar.com>" = 13 characters
        val result = parse("<foo@bar.com>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(13, result.nextIndex)
    }

    @Test
    fun doesNotConsumeFollowingChars() {
        val result = parse("<http://foo.com> rest")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(16, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureUriWithSpace() {
        assertIs<Failure<Char, Unit>>(parse("<http://foo bar.com>"))
    }

    @Test
    fun failureSchemeOneLetter() {
        // Scheme must be 2+ letters
        assertIs<Failure<Char, Unit>>(parse("<a:foo>"))
    }

    @Test
    fun failureSchemeTooLong() {
        // Scheme must be ≤ 32 letters
        val longScheme = "<" + "a".repeat(33) + ":foo>"
        assertIs<Failure<Char, Unit>>(parse(longScheme))
    }

    @Test
    fun failureNotAnAutolink() {
        // Plain tag name — no scheme and no '@'
        assertIs<Failure<Char, Unit>>(parse("<em>"))
    }

    @Test
    fun failureEmpty() {
        assertIs<Failure<Char, Unit>>(parse("<>"))
    }

    @Test
    fun failureNoAngleBracket() {
        assertIs<Failure<Char, Unit>>(parse("http://example.com"))
    }

    @Test
    fun failureEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureEmailNoAt() {
        assertIs<Failure<Char, Unit>>(parse("<foobar.com>"))
    }

    @Test
    fun failureEmailDomainStartsWithHyphen() {
        assertIs<Failure<Char, Unit>>(parse("<foo@-bar.com>"))
    }

    @Test
    fun failureEmailDomainEndsWithDot() {
        assertIs<Failure<Char, Unit>>(parse("<foo@bar.>"))
    }
}
