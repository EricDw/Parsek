package parsek.markdown.parser.inline

import parsek.markdown.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PStrikethroughTest {

    private fun parse(input: String): List<Inline> =
        parseInlineContent(input.toList(), Unit) { null }

    // -------------------------------------------------------------------------
    // GFM spec example 491 — basic strikethrough
    // -------------------------------------------------------------------------

    @Test
    fun basicStrikethrough() {
        val inlines = parse("~~Hi~~ Hello, world!")
        val del = assertIs<Inline.Strikethrough>(inlines[0])
        assertEquals(1, del.children.size)
        assertIs<Inline.Text>(del.children[0])
        assertEquals("Hi", (del.children[0] as Inline.Text).literal)
    }

    // -------------------------------------------------------------------------
    // GFM spec example 492 — paragraph break interrupts strikethrough
    // -------------------------------------------------------------------------

    @Test
    fun singleTildeIsLiteralText() {
        // A single ~ is not a strikethrough delimiter
        val inlines = parse("~Hi~")
        // Should be literal text, no Strikethrough
        for (inline in inlines) {
            assert(inline !is Inline.Strikethrough) { "Single tilde should not produce Strikethrough" }
        }
    }

    @Test
    fun threeTildesProducesPartialStrikethrough() {
        // ~~~ = ~ (literal) + ~~ (delimiter), so ~~~Hi~~~ has a strikethrough
        // of "Hi~" with a leading literal "~"
        val inlines = parse("~~~Hi~~~")
        // First element is the leftover ~ as text
        assertIs<Inline.Text>(inlines[0])
        assertEquals("~", (inlines[0] as Inline.Text).literal)
        // Second element is the strikethrough
        val del = assertIs<Inline.Strikethrough>(inlines[1])
        assert(del.children.isNotEmpty())
    }

    // -------------------------------------------------------------------------
    // Strikethrough with emphasis nesting
    // -------------------------------------------------------------------------

    @Test
    fun strikethroughWithEmphasis() {
        val inlines = parse("~~**bold** and *italic*~~")
        assertEquals(1, inlines.size)
        val del = assertIs<Inline.Strikethrough>(inlines[0])
        // Should contain strong emphasis, text, and emphasis
        assert(del.children.any { it is Inline.StrongEmphasis })
        assert(del.children.any { it is Inline.Emphasis })
    }

    @Test
    fun emphasisInsideStrikethrough() {
        val inlines = parse("~~*foo*~~")
        assertEquals(1, inlines.size)
        val del = assertIs<Inline.Strikethrough>(inlines[0])
        assertEquals(1, del.children.size)
        assertIs<Inline.Emphasis>(del.children[0])
    }
}
