package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PLinkTest {

    /** A minimal content parser: each character becomes a separate [Inline.Text]. */
    private val charByCharParser: (List<Char>, Unit) -> List<Inline> = { chars, _ ->
        chars.map { Inline.Text(it.toString()) }
    }

    private val emptyRefResolver: LinkRefResolver = { null }

    private val sampleRefMap: LinkRefResolver = { label ->
        when (label) {
            "foo" -> Pair("/url", "title")
            "bar" -> Pair("/bar-url", null)
            "long title" -> Pair("/long", "A Long Title")
            else -> null
        }
    }

    private fun parse(
        input: String,
        resolveRef: LinkRefResolver = emptyRefResolver,
    ) = pLink(
        contentParser = charByCharParser,
        resolveRef = resolveRef,
    )(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Inline links
    // -------------------------------------------------------------------------

    @Test
    fun inlineLinkSimple() {
        val result = parse("[link](/url)")
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("/url", link.destination)
        assertEquals(null, link.title)
        assertEquals(listOf(Inline.Text("l"), Inline.Text("i"), Inline.Text("n"), Inline.Text("k")), link.children)
    }

    @Test
    fun inlineLinkWithTitle() {
        val result = parse("[link](/url \"title\")")
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("/url", link.destination)
        assertEquals("title", link.title)
    }

    @Test
    fun inlineLinkWithSingleQuoteTitle() {
        val result = parse("[link](/url 'title')")
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("/url", link.destination)
        assertEquals("title", link.title)
    }

    @Test
    fun inlineLinkWithParenTitle() {
        val result = parse("[link](/url (title))")
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("/url", link.destination)
        assertEquals("title", link.title)
    }

    @Test
    fun inlineLinkEmptyDestination() {
        val result = parse("[link]()")
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("", link.destination)
        assertEquals(null, link.title)
    }

    @Test
    fun inlineLinkAngleBracketDestination() {
        val result = parse("[link](<http://example.com>)")
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("http://example.com", link.destination)
    }

    @Test
    fun inlineLinkEmptyText() {
        val result = parse("[](/url)")
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("/url", link.destination)
        assertEquals(emptyList(), link.children)
    }

    @Test
    fun inlineLinkEmptyAngleBracketDest() {
        val result = parse("[link](<>)")
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("", link.destination)
    }

    @Test
    fun inlineLinkConsumesCorrectly() {
        val result = parse("[link](/url) trailing")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(12, result.nextIndex) // "[link](/url)" = 12 chars
    }

    @Test
    fun inlineLinkDestinationWithParens() {
        val result = parse("[link](foo(bar)baz)")
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("foo(bar)baz", link.destination)
    }

    // -------------------------------------------------------------------------
    // Full reference links
    // -------------------------------------------------------------------------

    @Test
    fun fullReferenceLink() {
        val result = parse("[link text][foo]", resolveRef = sampleRefMap)
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("/url", link.destination)
        assertEquals("title", link.title)
    }

    @Test
    fun fullReferenceLinkNoTitle() {
        val result = parse("[link text][bar]", resolveRef = sampleRefMap)
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("/bar-url", link.destination)
        assertEquals(null, link.title)
    }

    @Test
    fun fullReferenceLinkUndefined() {
        val result = parse("[link][undefined]", resolveRef = sampleRefMap)
        assertIs<Failure<Char, Unit>>(result)
    }

    @Test
    fun fullReferenceLinkCaseInsensitive() {
        val result = parse("[link][FOO]", resolveRef = sampleRefMap)
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("/url", link.destination)
    }

    // -------------------------------------------------------------------------
    // Collapsed reference links
    // -------------------------------------------------------------------------

    @Test
    fun collapsedReferenceLink() {
        val result = parse("[foo][]", resolveRef = sampleRefMap)
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("/url", link.destination)
        assertEquals("title", link.title)
    }

    @Test
    fun collapsedReferenceLinkUndefined() {
        val result = parse("[undefined][]", resolveRef = sampleRefMap)
        assertIs<Failure<Char, Unit>>(result)
    }

    // -------------------------------------------------------------------------
    // Shortcut reference links
    // -------------------------------------------------------------------------

    @Test
    fun shortcutReferenceLink() {
        val result = parse("[foo]", resolveRef = sampleRefMap)
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("/url", link.destination)
        assertEquals("title", link.title)
        assertEquals(5, result.nextIndex) // "[foo]" = 5 chars
    }

    @Test
    fun shortcutReferenceLinkUndefined() {
        val result = parse("[undefined]", resolveRef = sampleRefMap)
        assertIs<Failure<Char, Unit>>(result)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureNoOpenBracket() {
        assertIs<Failure<Char, Unit>>(parse("no bracket"))
    }

    @Test
    fun failureNoCloseBracket() {
        assertIs<Failure<Char, Unit>>(parse("[unclosed"))
    }

    @Test
    fun failureNoSuffixNoRef() {
        // No inline suffix, no reference resolver → fails
        assertIs<Failure<Char, Unit>>(parse("[text]"))
    }

    @Test
    fun failureEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureMismatchedParens() {
        // Unbalanced parens in destination
        assertIs<Failure<Char, Unit>>(parse("[link](foo(bar)"))
    }

    // -------------------------------------------------------------------------
    // Backslash escapes in link text
    // -------------------------------------------------------------------------

    @Test
    fun linkTextWithEscapedBracket() {
        // "\]" inside the text should not close the bracket early
        val result = parse("[foo\\]bar](/url)")
        assertIs<Success<Char, Inline, Unit>>(result)
        val link = assertIs<Inline.Link>(result.value)
        assertEquals("/url", link.destination)
    }
}
