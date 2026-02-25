package parsek.markdown.parser.block

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.markdown.ast.Block
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PTableTest {

    private fun parse(input: String) =
        pTable<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // GFM spec example 198 — basic table
    // -------------------------------------------------------------------------

    @Test
    fun basicTable() {
        val result = parse("| foo | bar |\n| --- | --- |\n| baz | bim |\n")
        assertIs<Success<Char, Block.Table, Unit>>(result)
        val table = result.value
        assertEquals(2, table.alignments.size)
        assertEquals(Block.Alignment.NONE, table.alignments[0])
        assertEquals(Block.Alignment.NONE, table.alignments[1])
        assertEquals(2, table.header.cells.size)
        assertEquals(1, table.body.size)
        assertEquals(2, table.body[0].cells.size)
    }

    // -------------------------------------------------------------------------
    // GFM spec example 199 — alignment and optional pipes
    // -------------------------------------------------------------------------

    @Test
    fun alignmentWithOptionalPipes() {
        val result = parse("| abc | defghi |\n:-: | -----------:\nbar | baz\n")
        assertIs<Success<Char, Block.Table, Unit>>(result)
        val table = result.value
        assertEquals(Block.Alignment.CENTER, table.alignments[0])
        assertEquals(Block.Alignment.RIGHT, table.alignments[1])
        assertEquals(1, table.body.size)
    }

    // -------------------------------------------------------------------------
    // GFM spec example 200 — escaped pipes
    // -------------------------------------------------------------------------

    @Test
    fun escapedPipesInCells() {
        val cells = splitTableCells("| f\\|oo  |")
        assertEquals(1, cells.size)
        assertEquals("f|oo", cells[0])
    }

    // -------------------------------------------------------------------------
    // GFM spec example 201 — table terminated by blockquote
    // -------------------------------------------------------------------------

    @Test
    fun terminatedByBlockquote() {
        val result = parse("| abc | def |\n| --- | --- |\n| bar | baz |\n> bar\n")
        assertIs<Success<Char, Block.Table, Unit>>(result)
        val table = result.value
        assertEquals(1, table.body.size)
    }

    // -------------------------------------------------------------------------
    // GFM spec example 202 — table terminated by blank line
    // -------------------------------------------------------------------------

    @Test
    fun terminatedByBlankLine() {
        val result = parse("| abc | def |\n| --- | --- |\n| bar | baz |\n\nbar\n")
        assertIs<Success<Char, Block.Table, Unit>>(result)
        val table = result.value
        assertEquals(1, table.body.size)
    }

    // -------------------------------------------------------------------------
    // GFM spec example 203 — header/delimiter cell count mismatch → not a table
    // -------------------------------------------------------------------------

    @Test
    fun mismatchedCellCountFails() {
        val result = parse("| abc | def |\n| --- |\n| bar |\n")
        assertIs<Failure<Char, Unit>>(result)
    }

    // -------------------------------------------------------------------------
    // GFM spec example 204 — body rows with variable cell counts
    // -------------------------------------------------------------------------

    @Test
    fun variableBodyRowCellCounts() {
        val result = parse("| abc | def |\n| --- | --- |\n| bar |\n| bar | baz | boo |\n")
        assertIs<Success<Char, Block.Table, Unit>>(result)
        val table = result.value
        assertEquals(2, table.body.size)
        // First row: missing second cell should be padded
        assertEquals(2, table.body[0].cells.size)
        // Second row: excess cell should be truncated
        assertEquals(2, table.body[1].cells.size)
    }

    // -------------------------------------------------------------------------
    // GFM spec example 205 — header-only table (no body)
    // -------------------------------------------------------------------------

    @Test
    fun headerOnlyTable() {
        val result = parse("| abc | def |\n| --- | --- |\n")
        assertIs<Success<Char, Block.Table, Unit>>(result)
        val table = result.value
        assertEquals(0, table.body.size)
    }

    // -------------------------------------------------------------------------
    // Delimiter row validation
    // -------------------------------------------------------------------------

    @Test
    fun leftAlignment() {
        assertEquals(Block.Alignment.LEFT, parseAlignmentCell(":---"))
    }

    @Test
    fun rightAlignment() {
        assertEquals(Block.Alignment.RIGHT, parseAlignmentCell("---:"))
    }

    @Test
    fun centerAlignment() {
        assertEquals(Block.Alignment.CENTER, parseAlignmentCell(":---:"))
    }

    @Test
    fun noAlignment() {
        assertEquals(Block.Alignment.NONE, parseAlignmentCell("---"))
    }

    @Test
    fun singleHyphen() {
        assertEquals(Block.Alignment.NONE, parseAlignmentCell("-"))
    }

    @Test
    fun invalidDelimiterCell() {
        assertEquals(null, parseAlignmentCell("abc"))
        assertEquals(null, parseAlignmentCell(""))
        assertEquals(null, parseAlignmentCell("::"))
    }

    // -------------------------------------------------------------------------
    // Cell splitting
    // -------------------------------------------------------------------------

    @Test
    fun splitCellsBasic() {
        val cells = splitTableCells("| a | b | c |")
        assertEquals(listOf("a", "b", "c"), cells)
    }

    @Test
    fun splitCellsNoPipes() {
        val cells = splitTableCells("a | b | c")
        assertEquals(listOf("a", "b", "c"), cells)
    }

    @Test
    fun splitCellsBacktickSpan() {
        // Pipe inside backtick span should not split
        val cells = splitTableCells("| `a|b` | c |")
        assertEquals(2, cells.size)
        assertEquals("`a|b`", cells[0])
    }

    // -------------------------------------------------------------------------
    // Not a table — missing delimiter row
    // -------------------------------------------------------------------------

    @Test
    fun noDelimiterRow() {
        val result = parse("| foo | bar |\n| baz | bim |\n")
        assertIs<Failure<Char, Unit>>(result)
    }
}
