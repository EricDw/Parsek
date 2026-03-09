package parsek.prose.parser

import parsek.prose.ast.Punctuation
import parsek.prose.ast.Sentence
import parsek.prose.ast.Token
import parsek.prose.ast.Whitespace
import parsek.prose.ast.Word

/**
 * Splits a flat list of [Token]s into [Sentence]s by detecting sentence boundaries.
 *
 * A sentence boundary occurs when a terminal punctuation token (`.`, `!`, `?`)
 * is followed by whitespace and then a word starting with an uppercase letter,
 * or by the end of the token list.
 *
 * Exceptions:
 * - Known abbreviations (`Mr.`, `Dr.`, `e.g.`, etc.)
 * - Initialisms (`U.S.A.`, single uppercase letters followed by `.`)
 * - Ellipsis (`...` or `…`)
 */
internal fun splitSentences(tokens: List<Token>): List<Sentence> {
    if (tokens.isEmpty()) return emptyList()

    val sentences = mutableListOf<Sentence>()
    var sentenceStart = 0

    var i = 0
    while (i < tokens.size) {
        val token = tokens[i]
        if (token is Punctuation && isTerminalPunctuation(token)) {
            // Check if this is actually a sentence boundary
            if (isSentenceBoundary(tokens, i)) {
                // Include any closing quotes/parens immediately after the terminator
                var end = i
                if (end + 1 < tokens.size && isClosingPunctuation(tokens[end + 1])) {
                    end++
                }
                val sentenceTokens = tokens.subList(sentenceStart, end + 1)
                val trimmed = trimWhitespace(sentenceTokens)
                if (trimmed.isNotEmpty()) {
                    sentences.add(buildSentence(trimmed, token))
                }
                // Skip whitespace between sentences
                sentenceStart = end + 1
                if (sentenceStart < tokens.size && tokens[sentenceStart] is Whitespace) {
                    sentenceStart++
                }
                i = sentenceStart
                continue
            }
        }
        i++
    }

    // Remaining tokens form the last sentence (possibly without terminator)
    if (sentenceStart < tokens.size) {
        val sentenceTokens = tokens.subList(sentenceStart, tokens.size)
        val trimmed = trimWhitespace(sentenceTokens)
        if (trimmed.isNotEmpty()) {
            val terminator = findLastTerminator(trimmed)
            sentences.add(buildSentence(trimmed, terminator))
        }
    }

    return sentences
}

private fun isTerminalPunctuation(token: Punctuation): Boolean {
    val t = token.text
    return t == "." || t == "!" || t == "?" ||
        t == "..." || t == "\u2026" // ellipsis
}

private fun isSentenceBoundary(tokens: List<Token>, punctIndex: Int): Boolean {
    val punct = tokens[punctIndex] as Punctuation

    // Ellipsis alone is not a boundary unless followed by uppercase
    if (punct.text == "..." || punct.text == "\u2026") {
        return isFollowedByUppercase(tokens, punctIndex)
    }

    // Period: check abbreviation exceptions
    if (punct.text == ".") {
        // Look back for the preceding word
        val prevWord = findPrecedingWord(tokens, punctIndex)
        if (prevWord != null) {
            if (isAbbreviation(prevWord.text)) return false
            if (isInitialism(prevWord.text)) return false
        }
    }

    // At end of tokens — it's a boundary
    val afterPunct = skipClosingPunctuation(tokens, punctIndex + 1)
    if (afterPunct >= tokens.size) return true

    // Must be followed by whitespace + uppercase
    return isFollowedByUppercase(tokens, punctIndex)
}

private fun isFollowedByUppercase(tokens: List<Token>, fromIndex: Int): Boolean {
    var i = fromIndex + 1
    // Skip closing punctuation
    while (i < tokens.size && isClosingPunctuation(tokens[i])) i++
    // Expect whitespace
    if (i >= tokens.size) return true
    if (tokens[i] !is Whitespace) return false
    i++
    // Skip further whitespace
    while (i < tokens.size && tokens[i] is Whitespace) i++
    // Expect word starting with uppercase (or end of input)
    if (i >= tokens.size) return true
    val next = tokens[i]
    return next is Word && next.text.isNotEmpty() && next.text[0].isUpperCase()
}

private fun isClosingPunctuation(token: Token): Boolean {
    if (token !is Punctuation) return false
    return token.text.all { it in "\")]\u2019\u201D" }
}

private fun skipClosingPunctuation(tokens: List<Token>, from: Int): Int {
    var i = from
    while (i < tokens.size && isClosingPunctuation(tokens[i])) i++
    return i
}

private fun findPrecedingWord(tokens: List<Token>, beforeIndex: Int): Word? {
    var i = beforeIndex - 1
    while (i >= 0) {
        when (tokens[i]) {
            is Word -> return tokens[i] as Word
            is Punctuation -> {
                // Could be part of an abbreviation chain like "e.g."
                // Look further back
                i--
            }
            else -> return null
        }
    }
    return null
}

private fun findLastTerminator(tokens: List<Token>): Punctuation? {
    for (i in tokens.indices.reversed()) {
        val t = tokens[i]
        if (t is Punctuation && (t.text == "." || t.text == "!" || t.text == "?")) {
            return t
        }
    }
    return null
}

private fun trimWhitespace(tokens: List<Token>): List<Token> {
    var start = 0
    while (start < tokens.size && tokens[start] is Whitespace) start++
    var end = tokens.size
    while (end > start && tokens[end - 1] is Whitespace) end--
    return if (start == 0 && end == tokens.size) tokens else tokens.subList(start, end)
}

private fun buildSentence(tokens: List<Token>, terminator: Punctuation?): Sentence {
    val first = tokens.first().sourceRange.first
    val last = tokens.last().sourceRange.last
    return Sentence(tokens, terminator, first..last)
}
