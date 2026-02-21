package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PInlinesTest {

    private fun parse(
        input: String,
        resolveRef: LinkRefResolver = { null },
    ): List<Inline> {
        val result = pInlines<Unit>(resolveRef)(ParserInput.of(input.toList(), Unit))
        assertIs<Success<Char, List<Inline>, Unit>>(result)
        return result.value
    }

    // -------------------------------------------------------------------------
    // Plain text
    // -------------------------------------------------------------------------

    @Test
    fun plainText() {
        val result = parse("hello world")
        assertEquals(listOf(Inline.Text("hello world")), result)
    }

    // -------------------------------------------------------------------------
    // Backslash escapes
    // -------------------------------------------------------------------------

    @Test
    fun backslashEscape() {
        val result = parse("foo\\*bar")
        assertEquals(
            listOf(Inline.Text("foo"), Inline.Text("*"), Inline.Text("bar")),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // HTML entities
    // -------------------------------------------------------------------------

    @Test
    fun htmlEntity() {
        val result = parse("foo&amp;bar")
        assertEquals(
            listOf(Inline.Text("foo"), Inline.HtmlEntity("&amp;"), Inline.Text("bar")),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Code spans
    // -------------------------------------------------------------------------

    @Test
    fun codeSpan() {
        val result = parse("foo `code` bar")
        assertEquals(
            listOf(Inline.Text("foo "), Inline.CodeSpan("code"), Inline.Text(" bar")),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Emphasis
    // -------------------------------------------------------------------------

    @Test
    fun emphasis() {
        val result = parse("*foo*")
        assertEquals(
            listOf(Inline.Emphasis(listOf(Inline.Text("foo")))),
            result,
        )
    }

    @Test
    fun strongEmphasis() {
        val result = parse("**foo**")
        assertEquals(
            listOf(Inline.StrongEmphasis(listOf(Inline.Text("foo")))),
            result,
        )
    }

    @Test
    fun combinedEmphasisAndStrong() {
        val result = parse("***foo***")
        assertEquals(
            listOf(
                Inline.Emphasis(
                    listOf(Inline.StrongEmphasis(listOf(Inline.Text("foo")))),
                ),
            ),
            result,
        )
    }

    @Test
    fun emphasisWithUnderscore() {
        val result = parse("_foo_")
        assertEquals(
            listOf(Inline.Emphasis(listOf(Inline.Text("foo")))),
            result,
        )
    }

    @Test
    fun emphasisMixedContent() {
        val result = parse("*foo `code` bar*")
        assertEquals(
            listOf(
                Inline.Emphasis(
                    listOf(
                        Inline.Text("foo "),
                        Inline.CodeSpan("code"),
                        Inline.Text(" bar"),
                    ),
                ),
            ),
            result,
        )
    }

    @Test
    fun unmatchedStar() {
        val result = parse("foo * bar")
        // '*' surrounded by spaces: not left-flanking → not opener, kept as text
        assertEquals(
            listOf(Inline.Text("foo "), Inline.Text("*"), Inline.Text(" bar")),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Line breaks
    // -------------------------------------------------------------------------

    @Test
    fun softBreak() {
        val result = parse("foo\nbar")
        assertEquals(
            listOf(Inline.Text("foo"), Inline.SoftBreak, Inline.Text("bar")),
            result,
        )
    }

    @Test
    fun hardBreakWithSpaces() {
        val result = parse("foo  \nbar")
        assertEquals(
            listOf(Inline.Text("foo"), Inline.HardBreak, Inline.Text("bar")),
            result,
        )
    }

    @Test
    fun hardBreakWithBackslash() {
        val result = parse("foo\\\nbar")
        assertEquals(
            listOf(Inline.Text("foo"), Inline.HardBreak, Inline.Text("bar")),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Autolinks
    // -------------------------------------------------------------------------

    @Test
    fun autolink() {
        val result = parse("see <http://example.com> here")
        assertEquals(
            listOf(
                Inline.Text("see "),
                Inline.Autolink("http://example.com"),
                Inline.Text(" here"),
            ),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Inline links
    // -------------------------------------------------------------------------

    @Test
    fun inlineLink() {
        val result = parse("[link](/url)")
        assertEquals(
            listOf(Inline.Link("/url", null, listOf(Inline.Text("link")))),
            result,
        )
    }

    @Test
    fun inlineLinkWithTitle() {
        val result = parse("[link](/url \"title\")")
        assertEquals(
            listOf(Inline.Link("/url", "title", listOf(Inline.Text("link")))),
            result,
        )
    }

    @Test
    fun inlineLinkWithEmphasis() {
        val result = parse("[*foo*](/url)")
        assertEquals(
            listOf(
                Inline.Link(
                    "/url", null,
                    listOf(Inline.Emphasis(listOf(Inline.Text("foo")))),
                ),
            ),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Images
    // -------------------------------------------------------------------------

    @Test
    fun inlineImage() {
        val result = parse("![alt](/img.png)")
        assertEquals(
            listOf(Inline.Image("/img.png", null, "alt")),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Reference links
    // -------------------------------------------------------------------------

    @Test
    fun shortcutReferenceLink() {
        val refMap: LinkRefResolver = { label ->
            if (label == "foo") Pair("/url", "title") else null
        }
        val result = parse("[foo]", resolveRef = refMap)
        assertEquals(
            listOf(Inline.Link("/url", "title", listOf(Inline.Text("foo")))),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Raw HTML
    // -------------------------------------------------------------------------

    @Test
    fun rawHtml() {
        val result = parse("foo <em>bar</em> baz")
        assertEquals(
            listOf(
                Inline.Text("foo "),
                Inline.RawHtml("<em>"),
                Inline.Text("bar"),
                Inline.RawHtml("</em>"),
                Inline.Text(" baz"),
            ),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Combined constructs
    // -------------------------------------------------------------------------

    @Test
    fun multipleMixedInlines() {
        val result = parse("A `code`, a *word*, and \\*.")
        assertEquals(
            listOf(
                Inline.Text("A "),
                Inline.CodeSpan("code"),
                Inline.Text(", a "),
                Inline.Emphasis(listOf(Inline.Text("word"))),
                Inline.Text(", and "),
                Inline.Text("*"),
                Inline.Text("."),
            ),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    fun emptyInput() {
        val result = pInlines<Unit>()(ParserInput.of(emptyList(), Unit))
        assertIs<Success<Char, List<Inline>, Unit>>(result)
        assertEquals(emptyList(), result.value)
    }
}
