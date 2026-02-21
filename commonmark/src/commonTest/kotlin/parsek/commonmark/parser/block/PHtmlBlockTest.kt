package parsek.commonmark.parser.block

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PHtmlBlockTest {

    private fun parse(input: String) =
        pHtmlBlock<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Type 1 — <pre>, <script>, <style>, <textarea>
    // -------------------------------------------------------------------------

    @Test
    fun type1PreBlock() {
        val result = parse("<pre>\nfoo\n</pre>\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<pre>\nfoo\n</pre>\n", result.value.literal)
    }

    @Test
    fun type1ScriptBlock() {
        val result = parse("<script>\nvar x = 1;\n</script>\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<script>\nvar x = 1;\n</script>\n", result.value.literal)
    }

    @Test
    fun type1StyleBlock() {
        val result = parse("<style>\n.foo { color: red; }\n</style>\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<style>\n.foo { color: red; }\n</style>\n", result.value.literal)
    }

    @Test
    fun type1TextareaBlock() {
        val result = parse("<textarea>\nhello\n</textarea>\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<textarea>\nhello\n</textarea>\n", result.value.literal)
    }

    @Test
    fun type1CaseInsensitive() {
        val result = parse("<PRE>\nhello\n</PRE>\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<PRE>\nhello\n</PRE>\n", result.value.literal)
    }

    @Test
    fun type1EndOnSameLine() {
        // Opening and closing tag on the same line — block ends after the first line.
        val result = parse("<pre>foo</pre>\nnext\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<pre>foo</pre>\n", result.value.literal)
        assertEquals("<pre>foo</pre>\n".length, result.nextIndex)
    }

    @Test
    fun type1ClosingTagMidLine() {
        // Closing tag followed by extra content on the same line — line is still the end.
        val result = parse("<pre>\nhello</pre> extra\nnext\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<pre>\nhello</pre> extra\n", result.value.literal)
    }

    @Test
    fun type1ExtendsToEof() {
        val result = parse("<pre>\nhello")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<pre>\nhello", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Type 2 — <!-- ... -->
    // -------------------------------------------------------------------------

    @Test
    fun type2SingleLine() {
        val result = parse("<!-- comment -->\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<!-- comment -->\n", result.value.literal)
    }

    @Test
    fun type2MultiLine() {
        val result = parse("<!--\nmulti\nline\n-->\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<!--\nmulti\nline\n-->\n", result.value.literal)
    }

    @Test
    fun type2ExtendsToEof() {
        val result = parse("<!-- unclosed")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<!-- unclosed", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Type 3 — <? ... ?>
    // -------------------------------------------------------------------------

    @Test
    fun type3SingleLine() {
        val result = parse("<?xml version=\"1.0\"?>\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<?xml version=\"1.0\"?>\n", result.value.literal)
    }

    @Test
    fun type3MultiLine() {
        val result = parse("<?php\necho 'hello';\n?>\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<?php\necho 'hello';\n?>\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Type 4 — <! + ASCII uppercase letter
    // -------------------------------------------------------------------------

    @Test
    fun type4Doctype() {
        // <!DOCTYPE html> contains '>' on the first line — block is a single line.
        val result = parse("<!DOCTYPE html>\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<!DOCTYPE html>\n", result.value.literal)
    }

    @Test
    fun type4MultiLine() {
        // No '>' on first two lines; block ends on the third line.
        val result = parse("<!ENTITY\nfoo\n>\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<!ENTITY\nfoo\n>\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Type 5 — <![CDATA[ ... ]]>
    // -------------------------------------------------------------------------

    @Test
    fun type5CdataSection() {
        val result = parse("<![CDATA[\nfoo & bar\n]]>\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<![CDATA[\nfoo & bar\n]]>\n", result.value.literal)
    }

    @Test
    fun type5SingleLine() {
        val result = parse("<![CDATA[foo]]>\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<![CDATA[foo]]>\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Type 6 — block-level tag
    // -------------------------------------------------------------------------

    @Test
    fun type6OpenTag() {
        val result = parse("<div>\nhello\nworld\n\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<div>\nhello\nworld\n", result.value.literal)
    }

    @Test
    fun type6CloseTag() {
        val result = parse("</div>\nhello\n\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("</div>\nhello\n", result.value.literal)
    }

    @Test
    fun type6BlankLineNotConsumed() {
        val result = parse("<div>\nhello\n\nnext\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<div>\nhello\n", result.value.literal)
        // nextIndex points to the start of the blank line, not past it.
        assertEquals("<div>\nhello\n".length, result.nextIndex)
    }

    @Test
    fun type6TagWithAttributes() {
        val result = parse("<table class=\"foo\">\n\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<table class=\"foo\">\n", result.value.literal)
    }

    @Test
    fun type6SelfClosingTag() {
        val result = parse("<hr/>\n\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<hr/>\n", result.value.literal)
    }

    @Test
    fun type6ExtendsToEof() {
        val result = parse("<div>\nno blank line")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<div>\nno blank line", result.value.literal)
    }

    @Test
    fun type6HeadingTag() {
        val result = parse("<h1>\n# not a heading\n</h1>\n\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<h1>\n# not a heading\n</h1>\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Type 7 — complete open or close tag (not type 1–6)
    // -------------------------------------------------------------------------

    @Test
    fun type7OpenTag() {
        val result = parse("<foo bar=\"baz\">\n\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<foo bar=\"baz\">\n", result.value.literal)
    }

    @Test
    fun type7CloseTag() {
        val result = parse("</foo>\n\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("</foo>\n", result.value.literal)
    }

    @Test
    fun type7SelfClosingTag() {
        val result = parse("<foo />\n\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<foo />\n", result.value.literal)
    }

    @Test
    fun type7BlankLineNotConsumed() {
        val result = parse("<foo>\nsome content\n\nnext\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<foo>\nsome content\n", result.value.literal)
        assertEquals("<foo>\nsome content\n".length, result.nextIndex)
    }

    @Test
    fun type7ExtendsToEof() {
        val result = parse("<custom-tag>")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<custom-tag>", result.value.literal)
    }

    @Test
    fun type7TagNotInBlockLevelList() {
        // <span> is not a block-level tag so it is type 7 (not type 6).
        val result = parse("<span>\n\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<span>\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // Leading indentation (0–3 spaces allowed; 4 is not)
    // -------------------------------------------------------------------------

    @Test
    fun oneLeadingSpace() {
        val result = parse(" <!-- comment -->\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals(" <!-- comment -->\n", result.value.literal)
    }

    @Test
    fun threeLeadingSpaces() {
        val result = parse("   <div>\nhello\n\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("   <div>\nhello\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // CRLF line endings are normalised to LF
    // -------------------------------------------------------------------------

    @Test
    fun crlfLineEndings() {
        val result = parse("<div>\r\nhello\r\n\r\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<div>\nhello\n", result.value.literal)
    }

    @Test
    fun crLineEndings() {
        val result = parse("<!-- comment -->\r")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals("<!-- comment -->\n", result.value.literal)
    }

    // -------------------------------------------------------------------------
    // nextIndex correctness
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexAfterType2Block() {
        // "<!-- x -->\n" = 11 characters.
        val result = parse("<!-- x -->\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals(11, result.nextIndex)
    }

    @Test
    fun nextIndexAfterType6Block() {
        // "<div>\n\n" — block is "<div>\n" (6 chars), blank line not consumed.
        val result = parse("<div>\n\n")
        assertIs<Success<Char, Block.HtmlBlock, Unit>>(result)
        assertEquals(6, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureOnPlainText() {
        assertIs<Failure<Char, Unit>>(parse("hello\n"))
    }

    @Test
    fun failureOnEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureOnFourLeadingSpaces() {
        assertIs<Failure<Char, Unit>>(parse("    <div>\nhello\n\n"))
    }

    @Test
    fun failureOnBareAngleBracket() {
        assertIs<Failure<Char, Unit>>(parse("<\n"))
    }

    @Test
    fun failureOnType4LowercaseLetter() {
        // Type 4 requires an ASCII *uppercase* letter after '<!'.
        assertIs<Failure<Char, Unit>>(parse("<!doctype html>\n"))
    }

    @Test
    fun failureOnIncompleteTag() {
        // '<foo' without '>' is not a complete tag — does not match type 7.
        assertIs<Failure<Char, Unit>>(parse("<foo\n"))
    }
}
