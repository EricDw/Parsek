package parsek.commonmark.parser.block

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.commonmark.ast.Inline
import parsek.pMap
import parsek.pOr
import parsek.text.pBlankLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PListTest {

    /** Basic inner parser — paragraphs only. */
    private fun parse(input: String) =
        pList<Unit> { pParagraph() }(ParserInput.of(input.toList(), Unit))

    /** Inner parser that handles blank lines (needed for loose-list tests). */
    private fun parseBlanks(input: String): parsek.ParseResult<Char, Block, Unit> {
        fun blockParser(): Parser<Char, Block, Unit> =
            pOr(pMap(pBlankLine<Unit>()) { Block.BlankLine }, pParagraph())
        return pList(::blockParser)(ParserInput.of(input.toList(), Unit))
    }

    // -------------------------------------------------------------------------
    // Single-item lists
    // -------------------------------------------------------------------------

    @Test
    fun singleBulletItem() {
        val result = parse("- foo\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.BulletList
        assertEquals('-', list.marker)
        assertEquals(1, list.items.size)
        val para = list.items[0].blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    @Test
    fun singleOrderedItem() {
        val result = parse("1. foo\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.OrderedList
        assertEquals(1, list.start)
        assertEquals('.', list.delimiter)
        assertEquals(1, list.items.size)
    }

    // -------------------------------------------------------------------------
    // Tight multi-item bullet lists
    // -------------------------------------------------------------------------

    @Test
    fun tightBulletList() {
        val result = parse("- foo\n- bar\n- baz\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.BulletList
        assertTrue(list.tight)
        assertEquals('-', list.marker)
        assertEquals(3, list.items.size)
        assertEquals(
            listOf(Inline.Text("foo")),
            (list.items[0].blocks.first() as Block.Paragraph).inlines,
        )
        assertEquals(
            listOf(Inline.Text("bar")),
            (list.items[1].blocks.first() as Block.Paragraph).inlines,
        )
    }

    @Test
    fun tightBulletListStar() {
        val result = parse("* foo\n* bar\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.BulletList
        assertEquals('*', list.marker)
        assertTrue(list.tight)
        assertEquals(2, list.items.size)
    }

    @Test
    fun tightBulletListPlus() {
        val result = parse("+ foo\n+ bar\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.BulletList
        assertEquals('+', list.marker)
        assertTrue(list.tight)
    }

    // -------------------------------------------------------------------------
    // Tight multi-item ordered lists
    // -------------------------------------------------------------------------

    @Test
    fun tightOrderedList() {
        val result = parse("1. foo\n2. bar\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.OrderedList
        assertTrue(list.tight)
        assertEquals('.', list.delimiter)
        assertEquals(1, list.start)
        assertEquals(2, list.items.size)
    }

    @Test
    fun orderedListWithParen() {
        val result = parse("1) foo\n2) bar\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.OrderedList
        assertEquals(')', list.delimiter)
        assertEquals(2, list.items.size)
    }

    @Test
    fun orderedListStartNumber() {
        // Start number is taken from the first item's marker.
        val result = parse("3. foo\n4. bar\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.OrderedList
        assertEquals(3, list.start)
        assertEquals(2, list.items.size)
    }

    // -------------------------------------------------------------------------
    // Loose lists (blank line between items)
    // -------------------------------------------------------------------------

    @Test
    fun looseListBlankBetweenItems() {
        val result = parse("- foo\n\n- bar\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.BulletList
        assertEquals(false, list.tight)
        assertEquals(2, list.items.size)
    }

    @Test
    fun looseOrderedList() {
        val result = parse("1. foo\n\n2. bar\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.OrderedList
        assertEquals(false, list.tight)
        assertEquals(2, list.items.size)
    }

    @Test
    fun looseListInternalBlank() {
        // An item with an internal blank line makes the whole list loose.
        val result = parseBlanks("- foo\n\n  bar\n- baz\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.BulletList
        assertEquals(false, list.tight)
        assertEquals(2, list.items.size)
    }

    // -------------------------------------------------------------------------
    // Compatibility — different markers stop the list
    // -------------------------------------------------------------------------

    @Test
    fun differentBulletCharStopsList() {
        // '- foo' and '* bar' have different bullet chars — only first item.
        val result = parse("- foo\n* bar\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.BulletList
        assertEquals(1, list.items.size)
        // nextIndex points to start of "* bar\n".
        assertEquals("- foo\n".length, result.nextIndex)
    }

    @Test
    fun differentOrderedDelimiterStopsList() {
        // '1. foo' and '2) bar' have different delimiters — only first item.
        val result = parse("1. foo\n2) bar\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.OrderedList
        assertEquals(1, list.items.size)
        assertEquals("1. foo\n".length, result.nextIndex)
    }

    @Test
    fun bulletThenOrderedStopsList() {
        val result = parse("- foo\n1. bar\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.BulletList
        assertEquals(1, list.items.size)
        assertEquals("- foo\n".length, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Trailing blank NOT consumed
    // -------------------------------------------------------------------------

    @Test
    fun trailingBlankNotConsumed() {
        // "- foo\n" consumed; "\n" is a trailing blank (not consumed).
        val result = parse("- foo\n\n")
        assertIs<Success<Char, Block, Unit>>(result)
        assertEquals("- foo\n".length, result.nextIndex)
    }

    @Test
    fun blankBetweenItemsConsumed() {
        // "- foo\n\n- bar\n": blank between items IS consumed.
        val result = parse("- foo\n\n- bar\n")
        assertIs<Success<Char, Block, Unit>>(result)
        assertEquals("- foo\n\n- bar\n".length, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // nextIndex correctness
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexTightTwoItems() {
        // "- foo\n- bar\n" = 12 characters.
        val result = parse("- foo\n- bar\n")
        assertIs<Success<Char, Block, Unit>>(result)
        assertEquals(12, result.nextIndex)
    }

    @Test
    fun nextIndexDoesNotConsumeFollowingParagraph() {
        val result = parse("- foo\nbar\n")
        assertIs<Success<Char, Block, Unit>>(result)
        val list = result.value as Block.BulletList
        // "bar" is not a list item — it's a continuation of "foo" if W allows it,
        // but W=2 and "bar" has 0 spaces → not a continuation. So only "- foo" is consumed.
        assertEquals("- foo\n".length, result.nextIndex)
        assertEquals(1, list.items.size)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureOnPlainText() {
        assertIs<Failure<Char, Unit>>(parse("foo\n"))
    }

    @Test
    fun failureOnEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureOnBlankLine() {
        assertIs<Failure<Char, Unit>>(parse("\n"))
    }

    @Test
    fun failureOnFourLeadingSpaces() {
        assertIs<Failure<Char, Unit>>(parse("    - foo\n"))
    }
}
