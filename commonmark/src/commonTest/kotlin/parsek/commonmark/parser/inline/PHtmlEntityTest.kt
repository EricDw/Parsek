package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PHtmlEntityTest {

    private fun parse(input: String) =
        pHtmlEntity<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Named entities
    // -------------------------------------------------------------------------

    @Test
    fun namedAmp() {
        val result = parse("&amp;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HtmlEntity("&amp;"), result.value)
    }

    @Test
    fun namedLt() {
        val result = parse("&lt;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HtmlEntity("&lt;"), result.value)
    }

    @Test
    fun namedQuot() {
        val result = parse("&quot;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HtmlEntity("&quot;"), result.value)
    }

    @Test
    fun namedWithDigits() {
        // Entity names may contain digits (e.g. &frac12;).
        val result = parse("&frac12;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HtmlEntity("&frac12;"), result.value)
    }

    @Test
    fun namedSingleChar() {
        val result = parse("&a;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HtmlEntity("&a;"), result.value)
    }

    // -------------------------------------------------------------------------
    // Decimal numeric entities
    // -------------------------------------------------------------------------

    @Test
    fun decimalSimple() {
        val result = parse("&#42;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HtmlEntity("&#42;"), result.value)
    }

    @Test
    fun decimalZero() {
        val result = parse("&#0;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HtmlEntity("&#0;"), result.value)
    }

    @Test
    fun decimalMaxDigits() {
        // 7 decimal digits — at the spec limit.
        val result = parse("&#1234567;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HtmlEntity("&#1234567;"), result.value)
    }

    // -------------------------------------------------------------------------
    // Hexadecimal numeric entities
    // -------------------------------------------------------------------------

    @Test
    fun hexLowerX() {
        val result = parse("&#x2A;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HtmlEntity("&#x2A;"), result.value)
    }

    @Test
    fun hexUpperX() {
        val result = parse("&#X2a;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HtmlEntity("&#X2a;"), result.value)
    }

    @Test
    fun hexZero() {
        val result = parse("&#x0;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HtmlEntity("&#x0;"), result.value)
    }

    @Test
    fun hexMaxDigits() {
        // 6 hex digits — at the spec limit.
        val result = parse("&#xABCDEF;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.HtmlEntity("&#xABCDEF;"), result.value)
    }

    // -------------------------------------------------------------------------
    // nextIndex
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexNamed() {
        // "&amp;" = 5 characters.
        val result = parse("&amp;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun nextIndexDecimal() {
        // "&#42;" = 5 characters.
        val result = parse("&#42;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun nextIndexHex() {
        // "&#x2A;" = 6 characters.
        val result = parse("&#x2A;")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(6, result.nextIndex)
    }

    @Test
    fun doesNotConsumeFollowingChars() {
        val result = parse("&amp;foo")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(5, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureNoName() {
        // '&' immediately followed by ';' — empty entity name.
        assertIs<Failure<Char, Unit>>(parse("&;"))
    }

    @Test
    fun failureDecimalNoDigits() {
        assertIs<Failure<Char, Unit>>(parse("&#;"))
    }

    @Test
    fun failureHexNoDigits() {
        assertIs<Failure<Char, Unit>>(parse("&#x;"))
    }

    @Test
    fun failureDecimalTooManyDigits() {
        // 8 decimal digits — exceeds the spec limit of 7.
        assertIs<Failure<Char, Unit>>(parse("&#12345678;"))
    }

    @Test
    fun failureHexTooManyDigits() {
        // 7 hex digits — exceeds the spec limit of 6.
        assertIs<Failure<Char, Unit>>(parse("&#x1234567;"))
    }

    @Test
    fun failureNoSemicolon() {
        assertIs<Failure<Char, Unit>>(parse("&amp"))
    }

    @Test
    fun failureNotAmpersand() {
        assertIs<Failure<Char, Unit>>(parse("amp;"))
    }

    @Test
    fun failureEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureAmpersandAtEof() {
        assertIs<Failure<Char, Unit>>(parse("&"))
    }
}
