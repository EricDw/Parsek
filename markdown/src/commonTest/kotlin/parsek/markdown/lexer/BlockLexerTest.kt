package parsek.markdown.lexer

import parsek.markdown.scanner.scanDocument
import parsek.markdown.token.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockLexerTest {

    private fun lex(text: String): List<Token> = blockLex(scanDocument(text))

    // ── Blank lines ─────────────────────────────────────────────────────

    @Test
    fun blankLine() {
        val tokens = lex("\n")
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is Token.BlankLine)
    }

    @Test
    fun blankLineWithSpaces() {
        val tokens = lex("   \n")
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is Token.BlankLine)
    }

    @Test
    fun multipleBlankLines() {
        val tokens = lex("\n\n\n")
        assertEquals(3, tokens.size)
        assertTrue(tokens.all { it is Token.BlankLine })
    }

    // ── Thematic breaks ─────────────────────────────────────────────────

    @Test
    fun thematicBreakHyphens() {
        val tokens = lex("---\n")
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is Token.ThematicBreakLine)
    }

    @Test
    fun thematicBreakAsterisks() {
        val tokens = lex("***\n")
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is Token.ThematicBreakLine)
    }

    @Test
    fun thematicBreakUnderscores() {
        val tokens = lex("___\n")
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is Token.ThematicBreakLine)
    }

    @Test
    fun thematicBreakWithSpaces() {
        val tokens = lex("- - -\n")
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is Token.ThematicBreakLine)
    }

    @Test
    fun thematicBreakWithLeadingSpaces() {
        val tokens = lex("   ---\n")
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is Token.ThematicBreakLine)
    }

    @Test
    fun notThematicBreakTooFewChars() {
        val tokens = lex("--\n")
        assertEquals(1, tokens.size)
        // -- is a valid setext underline (level 2), not a paragraph
        assertTrue(tokens[0] is Token.SetextUnderline)
    }

    // ── ATX headings ────────────────────────────────────────────────────

    @Test
    fun atxHeadingLevel1() {
        val tokens = lex("# Heading\n")
        assertEquals(2, tokens.size)
        val marker = tokens[0] as Token.AtxHeadingMarker
        assertEquals(1, marker.level)
        val content = tokens[1] as Token.AtxHeadingContent
        assertEquals("Heading", lexemesToText(content.lexemes))
    }

    @Test
    fun atxHeadingLevel6() {
        val tokens = lex("###### Heading\n")
        assertEquals(2, tokens.size)
        val marker = tokens[0] as Token.AtxHeadingMarker
        assertEquals(6, marker.level)
    }

    @Test
    fun atxHeadingLevel7Invalid() {
        val tokens = lex("####### Not a heading\n")
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is Token.ParagraphLine)
    }

    @Test
    fun atxHeadingEmptyContent() {
        val tokens = lex("# \n")
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is Token.AtxHeadingMarker)
    }

    @Test
    fun atxHeadingNoContent() {
        val tokens = lex("#\n")
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is Token.AtxHeadingMarker)
    }

    @Test
    fun atxHeadingWithClosingHashes() {
        val tokens = lex("## Heading ##\n")
        assertEquals(2, tokens.size)
        val content = tokens[1] as Token.AtxHeadingContent
        assertEquals("Heading", lexemesToText(content.lexemes))
    }

    // ── Fenced code blocks ──────────────────────────────────────────────

    @Test
    fun fencedCodeBacktick() {
        val tokens = lex("```\ncode\n```\n")
        assertTrue(tokens[0] is Token.CodeFenceOpen)
        val open = tokens[0] as Token.CodeFenceOpen
        assertEquals('`', open.fenceChar)
        assertEquals(3, open.fenceLength)
        assertTrue(tokens[1] is Token.CodeContent)
        assertEquals("code\n", (tokens[1] as Token.CodeContent).literal)
        assertTrue(tokens[2] is Token.CodeFenceClose)
    }

    @Test
    fun fencedCodeTilde() {
        val tokens = lex("~~~\ncode\n~~~\n")
        assertTrue(tokens[0] is Token.CodeFenceOpen)
        assertEquals('~', (tokens[0] as Token.CodeFenceOpen).fenceChar)
    }

    @Test
    fun fencedCodeWithInfo() {
        val tokens = lex("```kotlin\ncode\n```\n")
        assertTrue(tokens[0] is Token.CodeFenceOpen)
        assertTrue(tokens[1] is Token.CodeFenceInfo)
        assertEquals("kotlin", (tokens[1] as Token.CodeFenceInfo).info)
        assertTrue(tokens[2] is Token.CodeContent)
    }

    @Test
    fun fencedCodeUnclosed() {
        val tokens = lex("```\ncode\n")
        assertTrue(tokens[0] is Token.CodeFenceOpen)
        assertTrue(tokens[1] is Token.CodeContent)
        // No close token
        assertEquals(2, tokens.size)
    }

    // ── Indented code ───────────────────────────────────────────────────

    @Test
    fun indentedCodeLine() {
        val tokens = lex("    code\n")
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is Token.IndentedCodeLine)
        assertEquals("code\n", (tokens[0] as Token.IndentedCodeLine).literal)
    }

    @Test
    fun indentedCodeMultipleLines() {
        val tokens = lex("    line1\n    line2\n")
        assertEquals(2, tokens.size)
        assertTrue(tokens.all { it is Token.IndentedCodeLine })
    }

    // ── Setext headings ─────────────────────────────────────────────────

    @Test
    fun setextLevel1() {
        val tokens = lex("Heading\n===\n")
        assertEquals(2, tokens.size)
        assertTrue(tokens[0] is Token.ParagraphLine)
        assertTrue(tokens[1] is Token.SetextUnderline)
        assertEquals(1, (tokens[1] as Token.SetextUnderline).level)
    }

    @Test
    fun setextLevel2() {
        val tokens = lex("Heading\n---\n")
        // --- matches thematic break (higher precedence), so the lexer produces
        // ParagraphLine + ThematicBreakLine. The parser handles disambiguation.
        assertEquals(2, tokens.size)
        assertTrue(tokens[0] is Token.ParagraphLine)
        assertTrue(tokens[1] is Token.ThematicBreakLine)
    }

    // ── Paragraphs ──────────────────────────────────────────────────────

    @Test
    fun simpleParagraph() {
        val tokens = lex("Hello world\n")
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is Token.ParagraphLine)
    }

    @Test
    fun multiLineParagraph() {
        val tokens = lex("Line one\nLine two\n")
        assertEquals(2, tokens.size)
        assertTrue(tokens.all { it is Token.ParagraphLine })
    }

    // ── HTML blocks ─────────────────────────────────────────────────────

    @Test
    fun htmlBlockType1() {
        val tokens = lex("<pre>\nfoo\n</pre>\n")
        assertTrue(tokens.all { it is Token.HtmlBlockLine })
    }

    @Test
    fun htmlBlockType2() {
        val tokens = lex("<!-- comment -->\n")
        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is Token.HtmlBlockLine)
    }

    @Test
    fun htmlBlockType6() {
        val tokens = lex("<div>\nfoo\n</div>\n")
        // Type 6 ends at blank line — content goes until blank or EOF
        assertTrue(tokens.all { it is Token.HtmlBlockLine })
    }
}
