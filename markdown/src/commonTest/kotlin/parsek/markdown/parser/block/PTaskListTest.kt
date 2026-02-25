package parsek.markdown.parser.block

import parsek.ParserInput
import parsek.Success
import parsek.markdown.ast.Block
import parsek.markdown.ast.Document
import parsek.markdown.parser.pDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PTaskListTest {

    private fun parseDoc(input: String): Document {
        val result = pDocument<Unit>()(ParserInput.of(input.toList(), Unit))
        assertIs<Success<Char, Document, Unit>>(result)
        return result.value
    }

    // -------------------------------------------------------------------------
    // GFM spec example 279 — basic task list
    // -------------------------------------------------------------------------

    @Test
    fun basicTaskList() {
        val doc = parseDoc("- [ ] foo\n- [x] bar\n")
        assertEquals(1, doc.blocks.size)
        val list = assertIs<Block.BulletList>(doc.blocks[0])
        assertEquals(2, list.items.size)

        // First item: unchecked
        assertEquals(false, list.items[0].checked)
        // Second item: checked
        assertEquals(true, list.items[1].checked)
    }

    // -------------------------------------------------------------------------
    // GFM spec example 280 — nested task lists
    // -------------------------------------------------------------------------

    @Test
    fun nestedTaskList() {
        val doc = parseDoc("- [x] foo\n  - [ ] bar\n  - [x] baz\n- [ ] bim\n")
        assertEquals(1, doc.blocks.size)
        val list = assertIs<Block.BulletList>(doc.blocks[0])
        assertEquals(2, list.items.size)

        assertEquals(true, list.items[0].checked)
        assertEquals(false, list.items[1].checked)

        // Inner list in first item
        val innerBlocks = list.items[0].blocks
        val innerList = innerBlocks.filterIsInstance<Block.BulletList>()
        assertEquals(1, innerList.size)
        assertEquals(false, innerList[0].items[0].checked)
        assertEquals(true, innerList[0].items[1].checked)
    }

    // -------------------------------------------------------------------------
    // Uppercase X
    // -------------------------------------------------------------------------

    @Test
    fun uppercaseX() {
        val doc = parseDoc("- [X] done\n")
        val list = assertIs<Block.BulletList>(doc.blocks[0])
        assertEquals(true, list.items[0].checked)
    }

    // -------------------------------------------------------------------------
    // Not a task list — no whitespace after marker
    // -------------------------------------------------------------------------

    @Test
    fun noSpaceAfterMarkerIsNotTaskList() {
        val doc = parseDoc("- [x]no space\n")
        val list = assertIs<Block.BulletList>(doc.blocks[0])
        assertNull(list.items[0].checked)
    }

    // -------------------------------------------------------------------------
    // Regular list item — no checkbox
    // -------------------------------------------------------------------------

    @Test
    fun regularListItemHasNullChecked() {
        val doc = parseDoc("- normal item\n")
        val list = assertIs<Block.BulletList>(doc.blocks[0])
        assertNull(list.items[0].checked)
    }

    // -------------------------------------------------------------------------
    // Ordered task list
    // -------------------------------------------------------------------------

    @Test
    fun orderedTaskList() {
        val doc = parseDoc("1. [ ] first\n2. [x] second\n")
        val list = assertIs<Block.OrderedList>(doc.blocks[0])
        assertEquals(false, list.items[0].checked)
        assertEquals(true, list.items[1].checked)
    }
}
