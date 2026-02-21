package parsek.commonmark.parser.block

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PLinkReferenceDefinitionTest {

    private fun parse(input: String) =
        pLinkReferenceDefinition<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Basic definitions — destination only
    // -------------------------------------------------------------------------

    @Test
    fun simpleDefinition() {
        val result = parse("[foo]: /url\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("foo", result.value.label)
        assertEquals("/url", result.value.destination)
        assertNull(result.value.title)
    }

    @Test
    fun definitionWithoutTrailingNewline() {
        val result = parse("[foo]: /url")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("/url", result.value.destination)
        assertNull(result.value.title)
    }

    // -------------------------------------------------------------------------
    // Title forms
    // -------------------------------------------------------------------------

    @Test
    fun doubleQuotedTitle() {
        val result = parse("[foo]: /url \"title\"\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("/url", result.value.destination)
        assertEquals("title", result.value.title)
    }

    @Test
    fun singleQuotedTitle() {
        val result = parse("[foo]: /url 'title'\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("title", result.value.title)
    }

    @Test
    fun parenTitle() {
        val result = parse("[foo]: /url (title)\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("title", result.value.title)
    }

    @Test
    fun emptyTitle() {
        val result = parse("[foo]: /url \"\"\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("", result.value.title)
    }

    @Test
    fun titleWithSpaces() {
        val result = parse("[foo]: /url \"the title\"\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("the title", result.value.title)
    }

    // -------------------------------------------------------------------------
    // Angle-bracket destination
    // -------------------------------------------------------------------------

    @Test
    fun angleBracketDestination() {
        val result = parse("[foo]: <http://example.com>\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("http://example.com", result.value.destination)
    }

    @Test
    fun angleBracketDestinationWithTitle() {
        val result = parse("[foo]: <http://example.com> \"title\"\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("http://example.com", result.value.destination)
        assertEquals("title", result.value.title)
    }

    @Test
    fun angleBracketEmptyDestination() {
        val result = parse("[foo]: <>\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("", result.value.destination)
    }

    // -------------------------------------------------------------------------
    // Destination on next line
    // -------------------------------------------------------------------------

    @Test
    fun destinationOnNextLine() {
        val result = parse("[foo]:\n/url\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("foo", result.value.label)
        assertEquals("/url", result.value.destination)
    }

    @Test
    fun destinationOnNextLineWithIndent() {
        val result = parse("[foo]:\n  /url\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("/url", result.value.destination)
    }

    // -------------------------------------------------------------------------
    // Title on next line
    // -------------------------------------------------------------------------

    @Test
    fun titleOnNextLine() {
        val result = parse("[foo]: /url\n\"title\"\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("title", result.value.title)
    }

    @Test
    fun destinationAndTitleOnSeparateLines() {
        val result = parse("[foo]:\n/url\n\"title\"\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("/url", result.value.destination)
        assertEquals("title", result.value.title)
    }

    // -------------------------------------------------------------------------
    // Label normalisation
    // -------------------------------------------------------------------------

    @Test
    fun labelIsCaseFolded() {
        val result = parse("[FOO]: /url\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("foo", result.value.label)
    }

    @Test
    fun labelWhitespaceCollapsed() {
        val result = parse("[foo   bar]: /url\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("foo bar", result.value.label)
    }

    @Test
    fun labelMixedCaseAndWhitespace() {
        val result = parse("[Foo  BAR]: /url\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("foo bar", result.value.label)
    }

    @Test
    fun labelWithLineEndingInsideIsCollapsed() {
        // A line ending inside the label counts as whitespace.
        val result = parse("[foo\nbar]: /url\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("foo bar", result.value.label)
    }

    // -------------------------------------------------------------------------
    // Leading indentation (0–3 spaces allowed)
    // -------------------------------------------------------------------------

    @Test
    fun oneLeadingSpace() {
        val result = parse(" [foo]: /url\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("foo", result.value.label)
    }

    @Test
    fun threeLeadingSpaces() {
        val result = parse("   [foo]: /url\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("foo", result.value.label)
    }

    // -------------------------------------------------------------------------
    // Backslash escapes in destination and title
    // -------------------------------------------------------------------------

    @Test
    fun backslashEscapeInBareDestination() {
        // '\(' in a bare destination is an escaped '('.
        val result = parse("[foo]: /url\\(bar\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("/url(bar", result.value.destination)
    }

    @Test
    fun backslashEscapeInTitle() {
        // '\"' inside a double-quoted title is an escaped '"'.
        val result = parse("[foo]: /url \"ti\\\"tle\"\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("ti\"tle", result.value.title)
    }

    @Test
    fun backslashEscapeInParenTitle() {
        val result = parse("[foo]: /url (escaped \\(paren)\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("escaped (paren", result.value.title)
    }

    // -------------------------------------------------------------------------
    // Separation between destination and title is required
    // -------------------------------------------------------------------------

    @Test
    fun noSpaceBeforeTitleFails() {
        // Angle-bracket destination immediately followed by title with no space.
        assertIs<Failure<Char, Unit>>(parse("[foo]: <url>\"title\"\n"))
    }

    // -------------------------------------------------------------------------
    // CRLF line endings
    // -------------------------------------------------------------------------

    @Test
    fun crlfLineEnding() {
        val result = parse("[foo]: /url\r\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("/url", result.value.destination)
    }

    // -------------------------------------------------------------------------
    // nextIndex correctness
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexAfterDefinition() {
        // "[foo]: /url\n" = 12 characters.
        val result = parse("[foo]: /url\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals(12, result.nextIndex)
    }

    @Test
    fun nextIndexDoesNotConsumeNextLine() {
        val result = parse("[foo]: /url\nnext line\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("[foo]: /url\n".length, result.nextIndex)
    }

    @Test
    fun nextIndexWithTitle() {
        // "[foo]: /url \"title\"\n" = 20 characters.
        val result = parse("[foo]: /url \"title\"\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("[foo]: /url \"title\"\n".length, result.nextIndex)
    }

    @Test
    fun nextIndexTitleOnNextLine() {
        // Definition spans two lines; nextIndex points past the title line.
        val result = parse("[foo]: /url\n\"title\"\n")
        assertIs<Success<Char, Block.LinkReferenceDefinition, Unit>>(result)
        assertEquals("[foo]: /url\n\"title\"\n".length, result.nextIndex)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureOnEmptyLabel() {
        assertIs<Failure<Char, Unit>>(parse("[]: /url\n"))
    }

    @Test
    fun failureOnWhitespaceOnlyLabel() {
        assertIs<Failure<Char, Unit>>(parse("[   ]: /url\n"))
    }

    @Test
    fun failureOnNoColon() {
        assertIs<Failure<Char, Unit>>(parse("[foo] /url\n"))
    }

    @Test
    fun failureOnNoDestination() {
        assertIs<Failure<Char, Unit>>(parse("[foo]:\n"))
    }

    @Test
    fun failureOnFourLeadingSpaces() {
        assertIs<Failure<Char, Unit>>(parse("    [foo]: /url\n"))
    }

    @Test
    fun failureOnContentAfterTitle() {
        assertIs<Failure<Char, Unit>>(parse("[foo]: /url \"title\" extra\n"))
    }

    @Test
    fun failureOnNonTitleContentAfterDestination() {
        // "bar" is not a valid title; fails with non-whitespace after destination.
        assertIs<Failure<Char, Unit>>(parse("[foo]: /url bar\n"))
    }

    @Test
    fun failureOnMalformedTitle() {
        // Unclosed double-quoted title.
        assertIs<Failure<Char, Unit>>(parse("[foo]: /url \"unclosed\n"))
    }

    @Test
    fun failureOnUnbalancedParenInParenTitle() {
        assertIs<Failure<Char, Unit>>(parse("[foo]: /url (bad ( paren)\n"))
    }

    @Test
    fun failureOnPlainText() {
        assertIs<Failure<Char, Unit>>(parse("not a link reference\n"))
    }

    @Test
    fun failureOnEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }
}
