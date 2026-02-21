package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PImageTest {

    private val emptyRefResolver: LinkRefResolver = { null }

    private val sampleRefMap: LinkRefResolver = { label ->
        when (label) {
            "foo" -> Pair("/image.png", "Foo Image")
            "bar" -> Pair("/bar.jpg", null)
            else -> null
        }
    }

    private fun parse(
        input: String,
        resolveRef: LinkRefResolver = emptyRefResolver,
    ) = pImage<Unit>(
        resolveRef = resolveRef,
    )(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Inline images
    // -------------------------------------------------------------------------

    @Test
    fun inlineImageSimple() {
        val result = parse("![alt text](/url)")
        assertIs<Success<Char, Inline, Unit>>(result)
        val image = assertIs<Inline.Image>(result.value)
        assertEquals("/url", image.destination)
        assertEquals(null, image.title)
        assertEquals("alt text", image.alt)
    }

    @Test
    fun inlineImageWithTitle() {
        val result = parse("![alt](/url \"title\")")
        assertIs<Success<Char, Inline, Unit>>(result)
        val image = assertIs<Inline.Image>(result.value)
        assertEquals("/url", image.destination)
        assertEquals("title", image.title)
        assertEquals("alt", image.alt)
    }

    @Test
    fun inlineImageEmptyAlt() {
        val result = parse("![](/url)")
        assertIs<Success<Char, Inline, Unit>>(result)
        val image = assertIs<Inline.Image>(result.value)
        assertEquals("/url", image.destination)
        assertEquals("", image.alt)
    }

    @Test
    fun inlineImageEmptyDestination() {
        val result = parse("![alt]()")
        assertIs<Success<Char, Inline, Unit>>(result)
        val image = assertIs<Inline.Image>(result.value)
        assertEquals("", image.destination)
        assertEquals("alt", image.alt)
    }

    @Test
    fun inlineImageAngleBracketDest() {
        val result = parse("![alt](<http://example.com/img.png>)")
        assertIs<Success<Char, Inline, Unit>>(result)
        val image = assertIs<Inline.Image>(result.value)
        assertEquals("http://example.com/img.png", image.destination)
    }

    @Test
    fun inlineImageConsumesCorrectly() {
        val result = parse("![alt](/url) trailing")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(12, result.nextIndex) // "![alt](/url)" = 12 chars
    }

    // -------------------------------------------------------------------------
    // Full reference images
    // -------------------------------------------------------------------------

    @Test
    fun fullReferenceImage() {
        val result = parse("![alt text][foo]", resolveRef = sampleRefMap)
        assertIs<Success<Char, Inline, Unit>>(result)
        val image = assertIs<Inline.Image>(result.value)
        assertEquals("/image.png", image.destination)
        assertEquals("Foo Image", image.title)
        assertEquals("alt text", image.alt)
    }

    @Test
    fun fullReferenceImageNoTitle() {
        val result = parse("![alt text][bar]", resolveRef = sampleRefMap)
        assertIs<Success<Char, Inline, Unit>>(result)
        val image = assertIs<Inline.Image>(result.value)
        assertEquals("/bar.jpg", image.destination)
        assertEquals(null, image.title)
    }

    @Test
    fun fullReferenceImageUndefined() {
        val result = parse("![alt][undefined]", resolveRef = sampleRefMap)
        assertIs<Failure<Char, Unit>>(result)
    }

    @Test
    fun fullReferenceImageCaseInsensitive() {
        val result = parse("![alt][FOO]", resolveRef = sampleRefMap)
        assertIs<Success<Char, Inline, Unit>>(result)
        val image = assertIs<Inline.Image>(result.value)
        assertEquals("/image.png", image.destination)
    }

    // -------------------------------------------------------------------------
    // Collapsed reference images
    // -------------------------------------------------------------------------

    @Test
    fun collapsedReferenceImage() {
        val result = parse("![foo][]", resolveRef = sampleRefMap)
        assertIs<Success<Char, Inline, Unit>>(result)
        val image = assertIs<Inline.Image>(result.value)
        assertEquals("/image.png", image.destination)
        assertEquals("Foo Image", image.title)
        assertEquals("foo", image.alt)
    }

    @Test
    fun collapsedReferenceImageUndefined() {
        val result = parse("![undefined][]", resolveRef = sampleRefMap)
        assertIs<Failure<Char, Unit>>(result)
    }

    // -------------------------------------------------------------------------
    // Shortcut reference images
    // -------------------------------------------------------------------------

    @Test
    fun shortcutReferenceImage() {
        val result = parse("![foo]", resolveRef = sampleRefMap)
        assertIs<Success<Char, Inline, Unit>>(result)
        val image = assertIs<Inline.Image>(result.value)
        assertEquals("/image.png", image.destination)
        assertEquals("Foo Image", image.title)
        assertEquals("foo", image.alt)
        assertEquals(6, result.nextIndex) // "![foo]" = 6 chars
    }

    @Test
    fun shortcutReferenceImageUndefined() {
        val result = parse("![undefined]", resolveRef = sampleRefMap)
        assertIs<Failure<Char, Unit>>(result)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureNoExclamation() {
        assertIs<Failure<Char, Unit>>(parse("[not an image](/url)"))
    }

    @Test
    fun failureNoOpenBracket() {
        assertIs<Failure<Char, Unit>>(parse("no image"))
    }

    @Test
    fun failureNoCloseBracket() {
        assertIs<Failure<Char, Unit>>(parse("![unclosed"))
    }

    @Test
    fun failureNoSuffixNoRef() {
        assertIs<Failure<Char, Unit>>(parse("![alt]"))
    }

    @Test
    fun failureEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    // -------------------------------------------------------------------------
    // Backslash escapes in alt text
    // -------------------------------------------------------------------------

    @Test
    fun imageAltWithEscapedBracket() {
        val result = parse("![foo\\]bar](/url)")
        assertIs<Success<Char, Inline, Unit>>(result)
        val image = assertIs<Inline.Image>(result.value)
        assertEquals("/url", image.destination)
        assertEquals("foo\\]bar", image.alt)
    }
}
