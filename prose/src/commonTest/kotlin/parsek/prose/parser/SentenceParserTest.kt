package parsek.prose.parser

import parsek.prose.ast.Punctuation
import parsek.prose.ast.Word
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentenceParserTest {

    @Test
    fun singleSentence() {
        val tokens = tokenize("Hello world.", 0)
        val sentences = splitSentences(tokens)
        assertEquals(1, sentences.size)
        assertEquals(".", sentences[0].terminator?.text)
    }

    @Test
    fun twoSentences() {
        val tokens = tokenize("Hello. World.", 0)
        val sentences = splitSentences(tokens)
        assertEquals(2, sentences.size)
        assertEquals(".", sentences[0].terminator?.text)
        assertEquals(".", sentences[1].terminator?.text)
        // First sentence should contain "Hello" and "."
        val firstWords = sentences[0].tokens.filterIsInstance<Word>()
        assertEquals("Hello", firstWords.single().text)
        // Second sentence should contain "World" and "."
        val secondWords = sentences[1].tokens.filterIsInstance<Word>()
        assertEquals("World", secondWords.single().text)
    }

    @Test
    fun abbreviationDoesNotSplit() {
        val tokens = tokenize("Dr. Smith arrived.", 0)
        val sentences = splitSentences(tokens)
        assertEquals(1, sentences.size)
        val words = sentences[0].tokens.filterIsInstance<Word>()
        assertEquals(listOf("Dr", "Smith", "arrived"), words.map { it.text })
    }

    @Test
    fun exclamationAndQuestion() {
        val tokens = tokenize("Stop! What happened?", 0)
        val sentences = splitSentences(tokens)
        assertEquals(2, sentences.size)
        assertEquals("!", sentences[0].terminator?.text)
        assertEquals("?", sentences[1].terminator?.text)
    }

    @Test
    fun noTerminator() {
        val tokens = tokenize("Hello world", 0)
        val sentences = splitSentences(tokens)
        assertEquals(1, sentences.size)
        assertNull(sentences[0].terminator)
    }

    @Test
    fun ellipsisNotBoundary() {
        val tokens = tokenize("Wait... let me think.", 0)
        val sentences = splitSentences(tokens)
        // "Wait... let me think." — ellipsis followed by lowercase, not a boundary
        assertEquals(1, sentences.size)
    }

    @Test
    fun ellipsisFollowedByUppercase() {
        val tokens = tokenize("Wait... Then it happened.", 0)
        val sentences = splitSentences(tokens)
        assertEquals(2, sentences.size)
    }

    @Test
    fun emptyInput() {
        assertEquals(emptyList(), splitSentences(emptyList()))
    }

    @Test
    fun sentenceSourceRange() {
        val tokens = tokenize("Hi. Bye.", 0)
        val sentences = splitSentences(tokens)
        assertEquals(2, sentences.size)
        assertEquals(0..2, sentences[0].sourceRange)
        assertEquals(4..7, sentences[1].sourceRange)
    }
}
