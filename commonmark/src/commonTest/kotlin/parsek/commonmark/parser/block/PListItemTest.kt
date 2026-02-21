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

class PListItemTest {

    /** Basic inner parser — paragraphs only. */
    private fun parse(input: String) =
        pListItem<Unit> { pParagraph() }(ParserInput.of(input.toList(), Unit))

    /** Inner parser that handles blank lines (needed for internal-blank tests). */
    private fun parseBlanks(input: String): parsek.ParseResult<Char, Block.ListItem, Unit> {
        fun blockParser(): Parser<Char, Block, Unit> =
            pOr(pMap(pBlankLine<Unit>()) { Block.BlankLine }, pParagraph())
        return pListItem(::blockParser)(ParserInput.of(input.toList(), Unit))
    }

    // -------------------------------------------------------------------------
    // Bullet markers: -, +, *
    // -------------------------------------------------------------------------

    @Test
    fun bulletDash() {
        val result = parse("- foo\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        assertEquals(1, result.value.blocks.size)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    @Test
    fun bulletStar() {
        val result = parse("* foo\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    @Test
    fun bulletPlus() {
        val result = parse("+ foo\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    // -------------------------------------------------------------------------
    // Ordered markers
    // -------------------------------------------------------------------------

    @Test
    fun orderedDot() {
        val result = parse("1. foo\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    @Test
    fun orderedParen() {
        val result = parse("1) foo\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    @Test
    fun orderedMultiDigit() {
        val result = parse("42. foo\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        assertIs<Block.Paragraph>(result.value.blocks.first())
    }

    // -------------------------------------------------------------------------
    // Multi-line items (continuation indentation)
    // -------------------------------------------------------------------------

    @Test
    fun bulletContinuation() {
        // W = 0 + 1 + 1 = 2; "  bar" has 2 leading spaces → continuation.
        val result = parse("- foo\n  bar\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo\nbar")), para.inlines)
    }

    @Test
    fun orderedContinuation() {
        // W = 0 + 2 + 1 = 3; "   bar" has 3 leading spaces → continuation.
        val result = parse("1. foo\n   bar\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo\nbar")), para.inlines)
    }

    @Test
    fun unindentedLineEndsItem() {
        // "next" has 0 spaces < W=2 → not a continuation; item ends after first line.
        val result = parse("- foo\nnext\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
        // nextIndex points just past "- foo\n".
        assertEquals("- foo\n".length, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Internal blank lines (absorbed when followed by a continuation)
    // -------------------------------------------------------------------------

    @Test
    fun internalBlankAbsorbed() {
        // Blank line between "foo" and "  bar" is absorbed → two blocks inside.
        val result = parseBlanks("- foo\n\n  bar\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        val nonBlank = result.value.blocks.filterIsInstance<Block.Paragraph>()
        assertEquals(2, nonBlank.size)
        assertEquals(listOf(Inline.Text("foo")), nonBlank[0].inlines)
        assertEquals(listOf(Inline.Text("bar")), nonBlank[1].inlines)
    }

    @Test
    fun trailingBlankNotConsumed() {
        // Blank line after content is NOT a continuation → not consumed by the item.
        val result = parse("- foo\n\nnext\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        // nextIndex is right after "- foo\n", the blank line is not consumed.
        assertEquals("- foo\n".length, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Leading indentation (0–3 spaces before marker)
    // -------------------------------------------------------------------------

    @Test
    fun oneLeadingSpaceBeforeMarker() {
        val result = parse(" - foo\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    @Test
    fun threeLeadingSpacesBeforeMarker() {
        val result = parse("   - foo\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    // -------------------------------------------------------------------------
    // Empty first line
    // -------------------------------------------------------------------------

    @Test
    fun emptyFirstLineWithContinuation() {
        // "-\n  foo\n" — empty first line, W = 2, "  foo" is a continuation.
        val result = parse("-\n  foo\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo")), para.inlines)
    }

    // -------------------------------------------------------------------------
    // CRLF line endings
    // -------------------------------------------------------------------------

    @Test
    fun crlfLineEndings() {
        val result = parse("- foo\r\n  bar\r\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        val para = result.value.blocks.first() as Block.Paragraph
        assertEquals(listOf(Inline.Text("foo\nbar")), para.inlines)
    }

    // -------------------------------------------------------------------------
    // nextIndex correctness
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexSingleLine() {
        // "- foo\n" = 6 characters.
        val result = parse("- foo\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        assertEquals(6, result.nextIndex)
    }

    @Test
    fun nextIndexMultiLine() {
        // "- foo\n  bar\n" = 12 characters.
        val result = parse("- foo\n  bar\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        assertEquals(12, result.nextIndex)
    }

    @Test
    fun nextIndexDoesNotConsumeFollowingLine() {
        val result = parse("- foo\n- bar\n")
        assertIs<Success<Char, Block.ListItem, Unit>>(result)
        // Only the first item is consumed; the second is left for pList.
        assertEquals("- foo\n".length, result.nextIndex)
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
    fun failureOnFourLeadingSpaces() {
        assertIs<Failure<Char, Unit>>(parse("    - foo\n"))
    }

    @Test
    fun failureOnOrderedTooManyDigits() {
        // 10+ digits are not a valid ordered marker.
        assertIs<Failure<Char, Unit>>(parse("1234567890. foo\n"))
    }

    @Test
    fun failureOnDashWithoutSpace() {
        // '-' immediately followed by a letter is not a valid bullet marker.
        assertIs<Failure<Char, Unit>>(parse("-foo\n"))
    }

    @Test
    fun failureOnBlankLine() {
        assertIs<Failure<Char, Unit>>(parse("\n"))
    }
}
