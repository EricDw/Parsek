package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PRawHtmlTest {

    private fun parse(input: String) =
        pRawHtml<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Open tags
    // -------------------------------------------------------------------------

    @Test
    fun simpleOpenTag() {
        val result = parse("<em>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("<em>"), result.value)
    }

    @Test
    fun openTagWithDoubleQuotedAttr() {
        val result = parse("""<a href="foo">""")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("""<a href="foo">"""), result.value)
    }

    @Test
    fun openTagWithSingleQuotedAttr() {
        val result = parse("<a href='foo'>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("<a href='foo'>"), result.value)
    }

    @Test
    fun openTagWithUnquotedAttr() {
        val result = parse("<a href=foo>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("<a href=foo>"), result.value)
    }

    @Test
    fun selfClosingTag() {
        val result = parse("<br />")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("<br />"), result.value)
    }

    @Test
    fun selfClosingNoSpace() {
        val result = parse("<br/>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("<br/>"), result.value)
    }

    @Test
    fun openTagMultipleAttrs() {
        val result = parse("""<img src="foo" alt="bar">""")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("""<img src="foo" alt="bar">"""), result.value)
    }

    // -------------------------------------------------------------------------
    // Close tags
    // -------------------------------------------------------------------------

    @Test
    fun simpleCloseTag() {
        val result = parse("</em>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("</em>"), result.value)
    }

    @Test
    fun closeTagWithTrailingSpace() {
        val result = parse("</em >")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("</em >"), result.value)
    }

    // -------------------------------------------------------------------------
    // HTML comments
    // -------------------------------------------------------------------------

    @Test
    fun simpleComment() {
        val result = parse("<!-- comment -->")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("<!-- comment -->"), result.value)
    }

    @Test
    fun emptyComment() {
        val result = parse("<!---->")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("<!---->"), result.value)
    }

    @Test
    fun commentWithHyphens() {
        // Content may contain single hyphens
        val result = parse("<!-- foo - bar -->")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("<!-- foo - bar -->"), result.value)
    }

    @Test
    fun failureCommentStartsWithGt() {
        assertIs<Failure<Char, Unit>>(parse("<!-->"))
    }

    @Test
    fun failureCommentStartsWithArrow() {
        assertIs<Failure<Char, Unit>>(parse("<!---/>"))
    }

    @Test
    fun failureCommentContainsDoubleHyphen() {
        assertIs<Failure<Char, Unit>>(parse("<!-- foo--bar -->"))
    }

    @Test
    fun failureCommentContentEndsWithHyphen() {
        // "<!--foo--->" — content "foo-" ends with '-', caught by the no-'--' rule
        assertIs<Failure<Char, Unit>>(parse("<!--foo--->"))
    }

    // -------------------------------------------------------------------------
    // Processing instructions
    // -------------------------------------------------------------------------

    @Test
    fun processingInstruction() {
        val result = parse("<?php echo 'hi'; ?>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("<?php echo 'hi'; ?>"), result.value)
    }

    @Test
    fun emptyProcessingInstruction() {
        val result = parse("<??>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("<??>"), result.value)
    }

    // -------------------------------------------------------------------------
    // Declarations
    // -------------------------------------------------------------------------

    @Test
    fun doctype() {
        val result = parse("<!DOCTYPE html>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("<!DOCTYPE html>"), result.value)
    }

    @Test
    fun failureDeclarationLowercaseLetter() {
        // Declaration requires an uppercase ASCII letter after '<!'
        assertIs<Failure<Char, Unit>>(parse("<!doctype html>"))
    }

    // -------------------------------------------------------------------------
    // CDATA sections
    // -------------------------------------------------------------------------

    @Test
    fun cdataSection() {
        val result = parse("<![CDATA[<greeting>Hello</greeting>]]>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("<![CDATA[<greeting>Hello</greeting>]]>"), result.value)
    }

    @Test
    fun emptyCdata() {
        val result = parse("<![CDATA[]]>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.RawHtml("<![CDATA[]]>"), result.value)
    }

    // -------------------------------------------------------------------------
    // nextIndex
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexOpenTag() {
        // "<em>" = 4 characters
        val result = parse("<em>")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(4, result.nextIndex)
    }

    @Test
    fun nextIndexComment() {
        // "<!-- x -->" = 10 characters
        val result = parse("<!-- x -->")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(10, result.nextIndex)
    }

    @Test
    fun doesNotConsumeFollowingChars() {
        val result = parse("<em>foo")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(4, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureTagNameStartsWithDigit() {
        assertIs<Failure<Char, Unit>>(parse("<123>"))
    }

    @Test
    fun failureNoOpenAngle() {
        assertIs<Failure<Char, Unit>>(parse("em>"))
    }

    @Test
    fun failureEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failurePlainText() {
        assertIs<Failure<Char, Unit>>(parse("foo"))
    }
}
