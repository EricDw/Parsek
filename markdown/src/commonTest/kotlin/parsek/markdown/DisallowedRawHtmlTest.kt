package parsek.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class DisallowedRawHtmlTest {

    @Test
    fun filterScript() {
        assertEquals(
            "&lt;script>alert('hi')&lt;/script>",
            filterDisallowedRawHtml("<script>alert('hi')</script>"),
        )
    }

    @Test
    fun filterClosingScript() {
        assertEquals("&lt;/script>", filterDisallowedRawHtml("</script>"))
    }

    @Test
    fun filterStyle() {
        assertEquals("&lt;style>body{}&lt;/style>", filterDisallowedRawHtml("<style>body{}</style>"))
    }

    @Test
    fun filterCaseInsensitive() {
        assertEquals("&lt;SCRIPT>", filterDisallowedRawHtml("<SCRIPT>"))
        assertEquals("&lt;Script>", filterDisallowedRawHtml("<Script>"))
    }

    @Test
    fun filterAllDisallowedTags() {
        val tags = listOf("title", "textarea", "style", "xmp", "iframe", "noembed", "noframes", "script", "plaintext")
        for (tag in tags) {
            val input = "<$tag>"
            val expected = "&lt;$tag>"
            assertEquals(expected, filterDisallowedRawHtml(input), "Failed for tag: $tag")
        }
    }

    @Test
    fun allowedTagsUnchanged() {
        assertEquals("<div>", filterDisallowedRawHtml("<div>"))
        assertEquals("<p>", filterDisallowedRawHtml("<p>"))
        assertEquals("<a href=\"x\">", filterDisallowedRawHtml("<a href=\"x\">"))
    }

    @Test
    fun selfClosingDisallowed() {
        assertEquals("&lt;script/>", filterDisallowedRawHtml("<script/>"))
    }

    @Test
    fun tagWithAttributes() {
        assertEquals(
            "&lt;script type=\"text/javascript\">",
            filterDisallowedRawHtml("<script type=\"text/javascript\">"),
        )
    }
}
