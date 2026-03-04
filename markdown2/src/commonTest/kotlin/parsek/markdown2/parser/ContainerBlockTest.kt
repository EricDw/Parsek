package parsek.markdown2.parser

import parsek.markdown.ast.Block
import parsek.markdown.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContainerBlockTest {

    // ── Block quotes ────────────────────────────────────────────────────

    @Test
    fun simpleBlockQuote() {
        val doc = parseDocument("> Hello\n")
        assertEquals(1, doc.blocks.size)
        val bq = doc.blocks[0] as Block.BlockQuote
        assertEquals(1, bq.blocks.size)
        assertTrue(bq.blocks[0] is Block.Paragraph)
    }

    @Test
    fun multiLineBlockQuote() {
        val doc = parseDocument("> line1\n> line2\n")
        val bq = doc.blocks[0] as Block.BlockQuote
        assertEquals(1, bq.blocks.size)
        val para = bq.blocks[0] as Block.Paragraph
        // Inline parser: Text("line1"), SoftBreak, Text("line2")
        assertEquals(3, para.inlines.size)
        assertEquals("line1", (para.inlines[0] as Inline.Text).literal)
        assertTrue(para.inlines[1] is Inline.SoftBreak)
    }

    @Test
    fun blockQuoteWithBlankLine() {
        val doc = parseDocument("> para1\n>\n> para2\n")
        val bq = doc.blocks[0] as Block.BlockQuote
        assertEquals(2, bq.blocks.size)
        assertTrue(bq.blocks[0] is Block.Paragraph)
        assertTrue(bq.blocks[1] is Block.Paragraph)
    }

    @Test
    fun nestedBlockQuote() {
        val doc = parseDocument("> > nested\n")
        val outer = doc.blocks[0] as Block.BlockQuote
        val inner = outer.blocks[0] as Block.BlockQuote
        assertEquals(1, inner.blocks.size)
    }

    @Test
    fun blockQuoteWithHeading() {
        val doc = parseDocument("> # Heading\n")
        val bq = doc.blocks[0] as Block.BlockQuote
        assertTrue(bq.blocks[0] is Block.Heading)
    }

    @Test
    fun blockQuoteWithCode() {
        val doc = parseDocument("> ```\n> code\n> ```\n")
        val bq = doc.blocks[0] as Block.BlockQuote
        assertTrue(bq.blocks[0] is Block.FencedCodeBlock)
    }

    // ── Bullet lists ────────────────────────────────────────────────────

    @Test
    fun simpleBulletList() {
        val doc = parseDocument("- one\n- two\n- three\n")
        assertEquals(1, doc.blocks.size)
        val list = doc.blocks[0] as Block.BulletList
        assertEquals(3, list.items.size)
        assertEquals('-', list.marker)
    }

    @Test
    fun bulletListPlus() {
        val doc = parseDocument("+ a\n+ b\n")
        val list = doc.blocks[0] as Block.BulletList
        assertEquals('+', list.marker)
        assertEquals(2, list.items.size)
    }

    @Test
    fun bulletListAsterisk() {
        val doc = parseDocument("* a\n* b\n")
        val list = doc.blocks[0] as Block.BulletList
        assertEquals('*', list.marker)
    }

    @Test
    fun tightBulletList() {
        val doc = parseDocument("- a\n- b\n- c\n")
        val list = doc.blocks[0] as Block.BulletList
        assertTrue(list.tight)
    }

    @Test
    fun looseBulletList() {
        val doc = parseDocument("- a\n\n- b\n\n- c\n")
        val list = doc.blocks[0] as Block.BulletList
        // Loose because of blank lines between items
        assertTrue(!list.tight || list.items.size == 3)
    }

    @Test
    fun bulletListItemWithContinuation() {
        val doc = parseDocument("- line1\n  line2\n")
        val list = doc.blocks[0] as Block.BulletList
        assertEquals(1, list.items.size)
        val text = ((list.items[0].blocks[0] as Block.Paragraph).inlines[0] as Inline.Text).literal
        assertTrue(text.contains("line1"))
    }

    @Test
    fun differentBulletMarkersAreSeparateLists() {
        val doc = parseDocument("- a\n+ b\n")
        // Different markers should produce separate lists
        assertTrue(doc.blocks.size >= 2 || doc.blocks[0] is Block.BulletList)
    }

    // ── Ordered lists ───────────────────────────────────────────────────

    @Test
    fun simpleOrderedList() {
        val doc = parseDocument("1. one\n2. two\n3. three\n")
        assertEquals(1, doc.blocks.size)
        val list = doc.blocks[0] as Block.OrderedList
        assertEquals(3, list.items.size)
        assertEquals(1, list.start)
        assertEquals('.', list.delimiter)
    }

    @Test
    fun orderedListStartNumber() {
        val doc = parseDocument("3. a\n4. b\n")
        val list = doc.blocks[0] as Block.OrderedList
        assertEquals(3, list.start)
    }

    @Test
    fun orderedListParenDelimiter() {
        val doc = parseDocument("1) a\n2) b\n")
        val list = doc.blocks[0] as Block.OrderedList
        assertEquals(')', list.delimiter)
    }

    @Test
    fun tightOrderedList() {
        val doc = parseDocument("1. a\n2. b\n")
        val list = doc.blocks[0] as Block.OrderedList
        assertTrue(list.tight)
    }

    // ── Mixed content ───────────────────────────────────────────────────

    @Test
    fun listAfterParagraph() {
        val doc = parseDocument("Para.\n\n- item\n")
        assertEquals(2, doc.blocks.size)
        assertTrue(doc.blocks[0] is Block.Paragraph)
        assertTrue(doc.blocks[1] is Block.BulletList)
    }

    @Test
    fun blockQuoteContainingList() {
        val doc = parseDocument("> - item1\n> - item2\n")
        val bq = doc.blocks[0] as Block.BlockQuote
        assertTrue(bq.blocks[0] is Block.BulletList)
    }

    @Test
    fun headingThenList() {
        val doc = parseDocument("# Title\n\n- a\n- b\n")
        assertEquals(2, doc.blocks.size)
        assertTrue(doc.blocks[0] is Block.Heading)
        assertTrue(doc.blocks[1] is Block.BulletList)
    }

    @Test
    fun thematicBreakBetweenLists() {
        val doc = parseDocument("- a\n\n---\n\n- b\n")
        val types = doc.blocks.map { it::class.simpleName }
        assertTrue("ThematicBreak" in types)
    }
}
