package parsek.markdown.parser.inline

import parsek.markdown.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PExtendedAutolinkTest {

    private fun parse(input: String): List<Inline> =
        splitExtendedAutolinks(parseInlineContent(input.toList(), Unit) { null })

    // -------------------------------------------------------------------------
    // www autolinks
    // -------------------------------------------------------------------------

    @Test
    fun wwwAutolink() {
        val inlines = parse("Visit www.commonmark.org for info.")
        val autolink = inlines.filterIsInstance<Inline.ExtendedAutolink>()
        assertEquals(1, autolink.size)
        assertEquals("http://www.commonmark.org", autolink[0].url)
    }

    @Test
    fun wwwAutolinkWithPath() {
        val inlines = parse("Visit www.commonmark.org/help for info.")
        val autolink = inlines.filterIsInstance<Inline.ExtendedAutolink>()
        assertEquals(1, autolink.size)
        assertEquals("http://www.commonmark.org/help", autolink[0].url)
    }

    @Test
    fun wwwAutolinkTrailingPunctuation() {
        val inlines = parse("Visit www.commonmark.org.")
        val autolink = inlines.filterIsInstance<Inline.ExtendedAutolink>()
        assertEquals(1, autolink.size)
        // Trailing . should be excluded
        assertEquals("http://www.commonmark.org", autolink[0].url)
    }

    // -------------------------------------------------------------------------
    // URL autolinks (http/https)
    // -------------------------------------------------------------------------

    @Test
    fun httpAutolink() {
        val inlines = parse("Visit http://commonmark.org for info.")
        val autolink = inlines.filterIsInstance<Inline.ExtendedAutolink>()
        assertEquals(1, autolink.size)
        assertEquals("http://commonmark.org", autolink[0].url)
    }

    @Test
    fun httpsAutolink() {
        val inlines = parse("Visit https://example.com/path?q=1 for info.")
        val autolink = inlines.filterIsInstance<Inline.ExtendedAutolink>()
        assertEquals(1, autolink.size)
        assertEquals("https://example.com/path?q=1", autolink[0].url)
    }

    @Test
    fun urlAutolinkWithParens() {
        val inlines = parse("https://encrypted.google.com/search?q=Markup+(business)")
        val autolink = inlines.filterIsInstance<Inline.ExtendedAutolink>()
        assertEquals(1, autolink.size)
        // Balanced parens — closing ) should be included
        assertEquals("https://encrypted.google.com/search?q=Markup+(business)", autolink[0].url)
    }

    // -------------------------------------------------------------------------
    // Email autolinks
    // -------------------------------------------------------------------------

    @Test
    fun emailAutolink() {
        val inlines = parse("Contact foo@bar.baz for info.")
        val autolink = inlines.filterIsInstance<Inline.ExtendedAutolink>()
        assertEquals(1, autolink.size)
        assertEquals("mailto:foo@bar.baz", autolink[0].url)
    }

    @Test
    fun emailWithPlus() {
        val inlines = parse("hello+xyz@mail.example")
        val autolink = inlines.filterIsInstance<Inline.ExtendedAutolink>()
        assertEquals(1, autolink.size)
        assertEquals("mailto:hello+xyz@mail.example", autolink[0].url)
    }

    // -------------------------------------------------------------------------
    // Context — must be after whitespace or delimiter
    // -------------------------------------------------------------------------

    @Test
    fun notAfterAlphanumeric() {
        val inlines = parse("textwww.example.com")
        val autolink = inlines.filterIsInstance<Inline.ExtendedAutolink>()
        assertEquals(0, autolink.size)
    }

    @Test
    fun afterAsterisk() {
        val inlines = parse("*www.example.com")
        val autolink = inlines.filterIsInstance<Inline.ExtendedAutolink>()
        assertEquals(1, autolink.size)
    }

    // -------------------------------------------------------------------------
    // No domain dot — should not match
    // -------------------------------------------------------------------------

    @Test
    fun noDomainDot() {
        val inlines = parse("foo@bar")
        val autolink = inlines.filterIsInstance<Inline.ExtendedAutolink>()
        assertEquals(0, autolink.size)
    }
}
