package parsek.markdown2.parser

import parsek.markdown.ast.Block
import parsek.markdown.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockParserTest {

    // ── Thematic breaks ─────────────────────────────────────────────────

    @Test
    fun thematicBreak() {
        val doc = parseDocument("---\n")
        assertEquals(1, doc.blocks.size)
        assertTrue(doc.blocks[0] is Block.ThematicBreak)
    }

    @Test
    fun multipleThematicBreaks() {
        val doc = parseDocument("---\n\n***\n\n___\n")
        val breaks = doc.blocks.filterIsInstance<Block.ThematicBreak>()
        assertEquals(3, breaks.size)
    }

    // ── ATX headings ────────────────────────────────────────────────────

    @Test
    fun atxHeadingLevel1() {
        val doc = parseDocument("# Hello\n")
        assertEquals(1, doc.blocks.size)
        val heading = doc.blocks[0] as Block.Heading
        assertEquals(1, heading.level)
        assertEquals("Hello", (heading.inlines[0] as Inline.Text).literal)
    }

    @Test
    fun atxHeadingAllLevels() {
        for (level in 1..6) {
            val hashes = "#".repeat(level)
            val doc = parseDocument("$hashes Heading $level\n")
            val heading = doc.blocks[0] as Block.Heading
            assertEquals(level, heading.level)
        }
    }

    @Test
    fun atxHeadingEmpty() {
        val doc = parseDocument("#\n")
        assertEquals(1, doc.blocks.size)
        val heading = doc.blocks[0] as Block.Heading
        assertEquals(1, heading.level)
        assertTrue(heading.inlines.isEmpty())
    }

    @Test
    fun atxHeadingWithClosingHashes() {
        val doc = parseDocument("## Foo ##\n")
        val heading = doc.blocks[0] as Block.Heading
        assertEquals(2, heading.level)
        assertEquals("Foo", (heading.inlines[0] as Inline.Text).literal)
    }

    // ── Fenced code blocks ──────────────────────────────────────────────

    @Test
    fun fencedCodeBlock() {
        val doc = parseDocument("```\nhello\n```\n")
        assertEquals(1, doc.blocks.size)
        val code = doc.blocks[0] as Block.FencedCodeBlock
        assertEquals(null, code.info)
        assertEquals("hello\n", code.literal)
    }

    @Test
    fun fencedCodeBlockWithInfo() {
        val doc = parseDocument("```kotlin\nval x = 1\n```\n")
        assertEquals(1, doc.blocks.size)
        val code = doc.blocks[0] as Block.FencedCodeBlock
        assertEquals("kotlin", code.info)
        assertEquals("val x = 1\n", code.literal)
    }

    @Test
    fun fencedCodeBlockUnclosed() {
        val doc = parseDocument("```\nhello\nworld\n")
        assertEquals(1, doc.blocks.size)
        val code = doc.blocks[0] as Block.FencedCodeBlock
        assertEquals("hello\nworld\n", code.literal)
    }

    @Test
    fun fencedCodeBlockEmpty() {
        val doc = parseDocument("```\n```\n")
        assertEquals(1, doc.blocks.size)
        val code = doc.blocks[0] as Block.FencedCodeBlock
        assertEquals("", code.literal)
    }

    @Test
    fun fencedCodeBlockTilde() {
        val doc = parseDocument("~~~\nhello\n~~~\n")
        assertEquals(1, doc.blocks.size)
        assertTrue(doc.blocks[0] is Block.FencedCodeBlock)
    }

    // ── Indented code blocks ────────────────────────────────────────────

    @Test
    fun indentedCodeBlock() {
        val doc = parseDocument("    hello\n")
        assertEquals(1, doc.blocks.size)
        val code = doc.blocks[0] as Block.IndentedCodeBlock
        assertEquals("hello\n", code.literal)
    }

    @Test
    fun indentedCodeBlockMultipleLines() {
        val doc = parseDocument("    line1\n    line2\n")
        assertEquals(1, doc.blocks.size)
        val code = doc.blocks[0] as Block.IndentedCodeBlock
        assertEquals("line1\nline2\n", code.literal)
    }

    // ── Paragraphs ──────────────────────────────────────────────────────

    @Test
    fun paragraph() {
        val doc = parseDocument("Hello world\n")
        assertEquals(1, doc.blocks.size)
        val para = doc.blocks[0] as Block.Paragraph
        assertEquals("Hello world", (para.inlines[0] as Inline.Text).literal)
    }

    @Test
    fun multiLineParagraph() {
        val doc = parseDocument("Line one\nLine two\n")
        assertEquals(1, doc.blocks.size)
        val para = doc.blocks[0] as Block.Paragraph
        // Inline parser produces: Text("Line one"), SoftBreak, Text("Line two")
        assertEquals(3, para.inlines.size)
        assertEquals("Line one", (para.inlines[0] as Inline.Text).literal)
        assertTrue(para.inlines[1] is Inline.SoftBreak)
        assertEquals("Line two", (para.inlines[2] as Inline.Text).literal)
    }

    // ── HTML blocks ─────────────────────────────────────────────────────

    @Test
    fun htmlBlock() {
        val doc = parseDocument("<div>\nhello\n</div>\n")
        assertEquals(1, doc.blocks.size)
        assertTrue(doc.blocks[0] is Block.HtmlBlock)
    }

    // ── Mixed blocks ────────────────────────────────────────────────────

    @Test
    fun mixedBlocks() {
        val doc = parseDocument("# Title\n\nParagraph.\n\n---\n\n```\ncode\n```\n")
        val types = doc.blocks.map { it::class.simpleName }
        assertEquals(listOf("Heading", "Paragraph", "ThematicBreak", "FencedCodeBlock"), types)
    }
}
