package parsek.markdown.parser

import parsek.markdown.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineParserTest {

    // ── Plain text ─────────────────────────────────────────────────────────

    @Test
    fun plainText() {
        val result = parseInlines("hello world")
        assertEquals(1, result.size)
        assertEquals("hello world", (result[0] as Inline.Text).literal)
    }

    @Test
    fun emptyInput() {
        assertEquals(emptyList(), parseInlines(""))
    }

    // ── Backslash escapes ──────────────────────────────────────────────────

    @Test
    fun backslashEscapePunctuation() {
        val result = parseInlines("\\*not emphasis\\*")
        assertEquals(3, result.size)
        assertEquals("*", (result[0] as Inline.Text).literal)
        assertEquals("not emphasis", (result[1] as Inline.Text).literal)
        assertEquals("*", (result[2] as Inline.Text).literal)
    }

    @Test
    fun backslashEscapeBackslash() {
        val result = parseInlines("\\\\")
        assertEquals(1, result.size)
        assertEquals("\\", (result[0] as Inline.Text).literal)
    }

    @Test
    fun backslashBeforeNonPunctuation() {
        val result = parseInlines("\\a")
        // Backslash is literal, followed by text
        assertEquals(2, result.size)
        assertEquals("\\", (result[0] as Inline.Text).literal)
        assertEquals("a", (result[1] as Inline.Text).literal)
    }

    @Test
    fun backslashNewlineIsHardBreak() {
        val result = parseInlines("foo\\\nbar")
        assertEquals(3, result.size)
        assertEquals("foo", (result[0] as Inline.Text).literal)
        assertTrue(result[1] is Inline.HardBreak)
        assertEquals("bar", (result[2] as Inline.Text).literal)
    }

    // ── Code spans ─────────────────────────────────────────────────────────

    @Test
    fun simpleCodeSpan() {
        val result = parseInlines("`code`")
        assertEquals(1, result.size)
        assertEquals("code", (result[0] as Inline.CodeSpan).literal)
    }

    @Test
    fun doubleBacktickCodeSpan() {
        val result = parseInlines("`` code ``")
        assertEquals(1, result.size)
        assertEquals("code", (result[0] as Inline.CodeSpan).literal)
    }

    @Test
    fun codeSpanWithBacktick() {
        val result = parseInlines("`` ` ``")
        assertEquals(1, result.size)
        assertEquals("`", (result[0] as Inline.CodeSpan).literal)
    }

    @Test
    fun codeSpanLineEndingNormalization() {
        val result = parseInlines("`foo\nbar`")
        assertEquals(1, result.size)
        assertEquals("foo bar", (result[0] as Inline.CodeSpan).literal)
    }

    @Test
    fun unmatchedBackticks() {
        val result = parseInlines("`foo")
        assertEquals(2, result.size)
        assertEquals("`", (result[0] as Inline.Text).literal)
        assertEquals("foo", (result[1] as Inline.Text).literal)
    }

    // ── Emphasis ───────────────────────────────────────────────────────────

    @Test
    fun simpleEmphasis() {
        val result = parseInlines("*foo*")
        assertEquals(1, result.size)
        val em = result[0] as Inline.Emphasis
        assertEquals(1, em.children.size)
        assertEquals("foo", (em.children[0] as Inline.Text).literal)
    }

    @Test
    fun underscoreEmphasis() {
        val result = parseInlines("_foo_")
        assertEquals(1, result.size)
        assertTrue(result[0] is Inline.Emphasis)
    }

    @Test
    fun strongEmphasis() {
        val result = parseInlines("**foo**")
        assertEquals(1, result.size)
        val strong = result[0] as Inline.StrongEmphasis
        assertEquals(1, strong.children.size)
        assertEquals("foo", (strong.children[0] as Inline.Text).literal)
    }

    @Test
    fun tripleEmphasis() {
        val result = parseInlines("***foo***")
        assertEquals(1, result.size)
        // Could be Em(Strong(foo)) or Strong(Em(foo)) depending on algorithm
        val outer = result[0]
        if (outer is Inline.Emphasis) {
            val inner = outer.children[0] as Inline.StrongEmphasis
            assertEquals("foo", (inner.children[0] as Inline.Text).literal)
        } else {
            val strong = outer as Inline.StrongEmphasis
            val inner = strong.children[0] as Inline.Emphasis
            assertEquals("foo", (inner.children[0] as Inline.Text).literal)
        }
    }

    @Test
    fun nestedEmphasis() {
        val result = parseInlines("*foo **bar** baz*")
        assertEquals(1, result.size)
        val em = result[0] as Inline.Emphasis
        assertEquals(3, em.children.size)
        assertEquals("foo ", (em.children[0] as Inline.Text).literal)
        assertTrue(em.children[1] is Inline.StrongEmphasis)
        assertEquals(" baz", (em.children[2] as Inline.Text).literal)
    }

    @Test
    fun unmatchedDelimiter() {
        val result = parseInlines("*foo")
        assertEquals(2, result.size)
        assertEquals("*", (result[0] as Inline.Text).literal)
        assertEquals("foo", (result[1] as Inline.Text).literal)
    }

    // ── Breaks ─────────────────────────────────────────────────────────────

    @Test
    fun softBreak() {
        val result = parseInlines("foo\nbar")
        assertEquals(3, result.size)
        assertTrue(result[1] is Inline.SoftBreak)
    }

    @Test
    fun hardBreakSpaces() {
        val result = parseInlines("foo  \nbar")
        assertEquals(3, result.size)
        assertTrue(result[1] is Inline.HardBreak)
    }

    @Test
    fun trailingSpacesStripped() {
        val result = parseInlines("foo   ")
        assertEquals(1, result.size)
        assertEquals("foo", (result[0] as Inline.Text).literal)
    }

    // ── Mixed content ──────────────────────────────────────────────────────

    @Test
    fun emphasisWithCodeSpan() {
        val result = parseInlines("*foo `code` bar*")
        assertEquals(1, result.size)
        val em = result[0] as Inline.Emphasis
        assertEquals(3, em.children.size)
        assertEquals("foo ", (em.children[0] as Inline.Text).literal)
        assertTrue(em.children[1] is Inline.CodeSpan)
        assertEquals(" bar", (em.children[2] as Inline.Text).literal)
    }
}
