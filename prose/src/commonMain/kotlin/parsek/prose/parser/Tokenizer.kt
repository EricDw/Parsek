package parsek.prose.parser

import parsek.prose.ast.Punctuation
import parsek.prose.ast.Token
import parsek.prose.ast.Whitespace
import parsek.prose.ast.Word

/**
 * Tokenizes a paragraph's raw text into a flat list of [Token]s.
 *
 * @param text the raw paragraph text (no blank lines).
 * @param offset the character offset of this paragraph in the source document,
 *   used to compute [Token.sourceRange] values.
 * @return an ordered list of [Word], [Punctuation], and [Whitespace] tokens.
 */
internal fun tokenize(text: String, offset: Int): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            ch.isWhitespace() -> {
                val start = i
                while (i < text.length && text[i].isWhitespace()) i++
                tokens.add(Whitespace(text.substring(start, i), (offset + start)..(offset + i - 1)))
            }
            isPunctuation(ch) -> {
                val start = i
                // Group runs of the same punctuation character (e.g. "...", "---")
                val base = ch
                i++
                while (i < text.length && text[i] == base) i++
                // Also group mixed closing punctuation (e.g. '!"', '?"')
                // but only if the base was a sentence terminator
                tokens.add(Punctuation(text.substring(start, i), (offset + start)..(offset + i - 1)))
            }
            else -> {
                val start = i
                while (i < text.length && !text[i].isWhitespace() && !isPunctuation(text[i])) i++
                tokens.add(Word(text.substring(start, i), (offset + start)..(offset + i - 1)))
            }
        }
    }
    return tokens
}

private fun isPunctuation(ch: Char): Boolean {
    return ch in "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~" ||
        ch == '\u2014' || ch == '\u2013' || // em-dash, en-dash
        ch == '\u2018' || ch == '\u2019' || // single curly quotes
        ch == '\u201C' || ch == '\u201D' || // double curly quotes
        ch == '\u2026'                      // ellipsis character
}
