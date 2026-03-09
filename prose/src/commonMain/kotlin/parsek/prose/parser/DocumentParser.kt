package parsek.prose.parser

import parsek.prose.ast.TextDocument
import parsek.prose.ast.Paragraph

/**
 * Parses a plain-text string into a [TextDocument] AST.
 *
 * Paragraphs are separated by one or more blank lines. Line endings are
 * normalised from `\r\n` and `\r` to `\n` before processing.
 *
 * @param text the raw source text.
 * @return a [TextDocument] containing the parsed paragraph hierarchy.
 */
fun parseDocument(text: String): TextDocument {
    if (text.isEmpty()) return TextDocument(emptyList())

    val normalised = normaliseLineEndings(text)
    val paragraphs = splitParagraphs(normalised)
    return TextDocument(paragraphs)
}

/**
 * Normalises `\r\n` and standalone `\r` to `\n`.
 */
private fun normaliseLineEndings(text: String): String {
    return text.replace("\r\n", "\n").replace("\r", "\n")
}

/**
 * Splits the normalised text on blank lines (two or more consecutive newlines)
 * and parses each non-empty segment as a paragraph.
 */
private fun splitParagraphs(text: String): List<Paragraph> {
    val paragraphs = mutableListOf<Paragraph>()
    var i = 0
    while (i < text.length) {
        // Skip leading blank lines
        while (i < text.length && text[i] == '\n') i++
        if (i >= text.length) break

        // Find the end of this paragraph (next blank line = two consecutive \n)
        val start = i
        var end = i
        while (end < text.length) {
            if (text[end] == '\n') {
                // Check for blank line (another \n or only whitespace then \n)
                var peek = end + 1
                while (peek < text.length && text[peek] != '\n' && text[peek].isWhitespace()) peek++
                if (peek < text.length && text[peek] == '\n') {
                    // Found blank line — paragraph ends here
                    break
                }
                if (peek >= text.length) {
                    // End of text after trailing whitespace on last line
                    end = peek
                    break
                }
            }
            end++
        }

        val paraText = text.substring(start, end).trimEnd()
        if (paraText.isNotEmpty()) {
            paragraphs.add(parseParagraph(paraText, start))
        }

        i = end
    }
    return paragraphs
}
