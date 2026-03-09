package parsek.prose.parser

import parsek.prose.ast.Word
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentParserTest {

    @Test
    fun emptyDocument() {
        val doc = parseDocument("")
        assertEquals(0, doc.paragraphs.size)
    }

    @Test
    fun singleParagraph() {
        val doc = parseDocument("Hello world.")
        assertEquals(1, doc.paragraphs.size)
        assertEquals(1, doc.paragraphs[0].sentences.size)
    }

    @Test
    fun twoParagraphsSeparatedByBlankLine() {
        val doc = parseDocument("First paragraph.\n\nSecond paragraph.")
        assertEquals(2, doc.paragraphs.size)
        val first = doc.paragraphs[0].sentences[0].tokens.filterIsInstance<Word>()
        assertEquals("First", first[0].text)
        val second = doc.paragraphs[1].sentences[0].tokens.filterIsInstance<Word>()
        assertEquals("Second", second[0].text)
    }

    @Test
    fun multipleParagraphs() {
        val text = """
            |First paragraph here.
            |
            |Second paragraph here.
            |
            |Third paragraph here.
        """.trimMargin()
        val doc = parseDocument(text)
        assertEquals(3, doc.paragraphs.size)
    }

    @Test
    fun multipleBlankLinesBetweenParagraphs() {
        val doc = parseDocument("One.\n\n\n\nTwo.")
        assertEquals(2, doc.paragraphs.size)
    }

    @Test
    fun lineBreakWithinParagraph() {
        val doc = parseDocument("Hello\nworld.")
        assertEquals(1, doc.paragraphs.size)
        assertEquals(1, doc.paragraphs[0].sentences.size)
    }

    @Test
    fun windowsLineEndings() {
        val doc = parseDocument("One.\r\n\r\nTwo.")
        assertEquals(2, doc.paragraphs.size)
    }

    @Test
    fun leadingAndTrailingBlankLines() {
        val doc = parseDocument("\n\nHello.\n\n")
        assertEquals(1, doc.paragraphs.size)
    }

    @Test
    fun paragraphSourceRanges() {
        val doc = parseDocument("Hi.\n\nBye.")
        assertEquals(2, doc.paragraphs.size)
        assertEquals(0..2, doc.paragraphs[0].sourceRange)
        assertEquals(5..8, doc.paragraphs[1].sourceRange)
    }

    @Test
    fun multipleSentencesInParagraph() {
        val doc = parseDocument("First sentence. Second sentence. Third sentence.")
        assertEquals(1, doc.paragraphs.size)
        assertEquals(3, doc.paragraphs[0].sentences.size)
    }
}
