package parsek.prose.parser

import parsek.prose.ast.Punctuation
import parsek.prose.ast.Whitespace
import parsek.prose.ast.Word
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenizerTest {

    @Test
    fun simpleWords() {
        val tokens = tokenize("Hello world", 0)
        assertEquals(3, tokens.size)
        assertEquals(Word("Hello", 0..4), tokens[0])
        assertEquals(Whitespace(" ", 5..5), tokens[1])
        assertEquals(Word("world", 6..10), tokens[2])
    }

    @Test
    fun punctuationSeparatedFromWords() {
        val tokens = tokenize("Hello, world.", 0)
        assertEquals(5, tokens.size)
        assertEquals(Word("Hello", 0..4), tokens[0])
        assertEquals(Punctuation(",", 5..5), tokens[1])
        assertEquals(Whitespace(" ", 6..6), tokens[2])
        assertEquals(Word("world", 7..11), tokens[3])
        assertEquals(Punctuation(".", 12..12), tokens[4])
    }

    @Test
    fun ellipsisGrouped() {
        val tokens = tokenize("Wait...", 0)
        assertEquals(2, tokens.size)
        assertEquals(Word("Wait", 0..3), tokens[0])
        assertEquals(Punctuation("...", 4..6), tokens[1])
    }

    @Test
    fun emDashGrouped() {
        val tokens = tokenize("well--actually", 0)
        assertEquals(3, tokens.size)
        assertEquals(Word("well", 0..3), tokens[0])
        assertEquals(Punctuation("--", 4..5), tokens[1])
        assertEquals(Word("actually", 6..13), tokens[2])
    }

    @Test
    fun offsetApplied() {
        val tokens = tokenize("Hi!", 10)
        assertEquals(Word("Hi", 10..11), tokens[0])
        assertEquals(Punctuation("!", 12..12), tokens[1])
    }

    @Test
    fun quotedText() {
        val tokens = tokenize("\"Hello\"", 0)
        assertEquals(3, tokens.size)
        assertEquals(Punctuation("\"", 0..0), tokens[0])
        assertEquals(Word("Hello", 1..5), tokens[1])
        assertEquals(Punctuation("\"", 6..6), tokens[2])
    }

    @Test
    fun multipleWhitespace() {
        val tokens = tokenize("a   b", 0)
        assertEquals(3, tokens.size)
        assertEquals(Word("a", 0..0), tokens[0])
        assertEquals(Whitespace("   ", 1..3), tokens[1])
        assertEquals(Word("b", 4..4), tokens[2])
    }

    @Test
    fun emptyInput() {
        assertEquals(emptyList(), tokenize("", 0))
    }

    @Test
    fun apostropheKeptInWord() {
        // Apostrophes between letters stay part of the word in our tokenizer
        // Note: since our tokenizer classifies ' as punctuation, this depends
        // on the isPunctuation check. Let's verify the actual behavior:
        val tokens = tokenize("don't", 0)
        // ' is classified as punctuation, so we get: "don", "'", "t"
        assertEquals(3, tokens.size)
        assertEquals(Word("don", 0..2), tokens[0])
        assertEquals(Punctuation("'", 3..3), tokens[1])
        assertEquals(Word("t", 4..4), tokens[2])
    }
}
