package parsek.markdown.highlight.inline

import parsek.Parser
import parsek.Success
import parsek.markdown.ast.Inline
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.emit
import parsek.markdown.parser.inline.EmphasisToken
import parsek.markdown.parser.inline.classifyDelimiterRun
import parsek.markdown.parser.inline.processEmphasis

/**
 * Tracks the source position of a delimiter run during inline tokenization.
 *
 * @property start index of the first delimiter character (inclusive).
 * @property end index one past the last delimiter character (exclusive).
 * @property char the delimiter character (`*` or `_`).
 * @property originalLength the number of delimiter characters in the run.
 */
data class DelimiterRecord(
    val start: Int,
    val end: Int,
    val char: Char,
    val originalLength: Int,
)

/**
 * Parses a delimiter run (`*` or `_`) and records its source position in
 * [records] for later use by [emitEmphasisSpans].
 *
 * Returns an [EmphasisToken.DelimiterRun] identical to the standard parser.
 */
fun pDelimiterRunHighlight(
    records: MutableList<DelimiterRecord>,
): Parser<Char, EmphasisToken, SpanSink> =
    Parser { input ->
        val chars = input.input
        val start = input.index

        if (start >= chars.size)
            return@Parser parsek.Failure("delimiter run", start, input)

        val delimChar = chars[start]
        if (delimChar != '*' && delimChar != '_' && delimChar != '~')
            return@Parser parsek.Failure("delimiter run", start, input)

        var i = start
        while (i < chars.size && chars[i] == delimChar) i++
        val length = i - start

        // GFM strikethrough requires exactly 2 tildes.
        if (delimChar == '~' && length != 2)
            return@Parser parsek.Failure("delimiter run", start, input)

        val charBefore = if (start > 0) chars[start - 1] else null
        val charAfter = if (i < chars.size) chars[i] else null
        val (canOpen, canClose) = classifyDelimiterRun(charBefore, charAfter, delimChar)

        val token = EmphasisToken.DelimiterRun(delimChar, length, canOpen, canClose)
        records.add(DelimiterRecord(start, i, delimChar, length))

        Success(token, i, input)
    }

/**
 * Emits [TokenType.EmphasisMarker] and [TokenType.StrongMarker] spans by
 * comparing the original delimiter records with the processed inline result.
 *
 * The algorithm:
 * 1. Counts how many delimiter characters appear as literal text in the
 *    processed output (unmatched delimiters become `Inline.Text`).
 * 2. For each recorded delimiter run, if fewer characters appear as literal
 *    text than the original length, the difference was consumed as markers.
 *    Spans are emitted from the edges of the run (openers from the right,
 *    closers from the left).
 *
 * @param tokens the original flat token list before emphasis processing.
 * @param inlines the result of [processEmphasis].
 * @param records the delimiter records collected by [pDelimiterRunHighlight].
 * @param sink the span sink to emit into.
 */
fun emitEmphasisSpans(
    tokens: List<EmphasisToken>,
    inlines: List<Inline>,
    records: List<DelimiterRecord>,
    sink: SpanSink,
) {
    // Collect the remaining literal delimiter characters from the output.
    // Each unmatched delimiter run in the output is an Inline.Text of '*' or '_'.
    val literalLengths = mutableListOf<Pair<Char, Int>>()
    collectLiteralDelimiters(inlines, literalLengths)

    // Match each record to remaining literal characters.
    // Delimiter runs in the token list and the literal outputs appear in the
    // same order. Walk both lists, consuming from the literal pool.
    var litIdx = 0
    for (record in records) {
        var remaining = 0
        if (litIdx < literalLengths.size && literalLengths[litIdx].first == record.char) {
            remaining = literalLengths[litIdx].second
            litIdx++
        }

        val consumed = record.originalLength - remaining
        if (consumed <= 0) continue

        // Determine if consumed chars are strong (2) or emphasis (1) markers.
        // An opener gives up chars from its right end; a closer from its left.
        // We emit spans starting from the opener's right and closer's left.
        // For simplicity, emit based on consumed count:
        // - 2 consumed → StrongMarker
        // - 1 consumed → EmphasisMarker
        // - 3+ consumed → StrongMarker (2) + EmphasisMarker (1), etc.
        var c = consumed
        // Openers consume from the right end of the run.
        // Check if this was an opener or closer by looking at the token.
        val tokenIdx = records.indexOf(record)
        val isOpener = tokenIdx < records.size &&
            tokens.getOrNull(findTokenIndex(tokens, record))
                ?.let { it is EmphasisToken.DelimiterRun && it.canOpen } == true

        var emitStart: Int
        var emitEnd: Int

        if (isOpener) {
            // Opener: chars consumed from the right end.
            emitStart = record.end - consumed
            emitEnd = record.end
        } else {
            // Closer: chars consumed from the left end.
            emitStart = record.start
            emitEnd = record.start + consumed
        }

        while (c > 0) {
            if (c >= 2) {
                if (isOpener) {
                    sink.emit(TokenType.StrongMarker, emitEnd - 2, emitEnd)
                    emitEnd -= 2
                } else {
                    sink.emit(TokenType.StrongMarker, emitStart, emitStart + 2)
                    emitStart += 2
                }
                c -= 2
            } else {
                if (isOpener) {
                    sink.emit(TokenType.EmphasisMarker, emitEnd - 1, emitEnd)
                    emitEnd -= 1
                } else {
                    sink.emit(TokenType.EmphasisMarker, emitStart, emitStart + 1)
                    emitStart += 1
                }
                c -= 1
            }
        }
    }
}

/**
 * Collects literal delimiter characters from the inline tree.
 * Unmatched delimiters appear as `Inline.Text` consisting only of `*` or `_`.
 */
private fun collectLiteralDelimiters(
    inlines: List<Inline>,
    out: MutableList<Pair<Char, Int>>,
) {
    for (inline in inlines) {
        when (inline) {
            is Inline.Text -> {
                if (inline.literal.isNotEmpty() && inline.literal.all { it == '*' || it == '_' }) {
                    val c = inline.literal[0]
                    if (inline.literal.all { it == c }) {
                        out.add(c to inline.literal.length)
                    }
                }
            }
            is Inline.Emphasis -> collectLiteralDelimiters(inline.children, out)
            is Inline.StrongEmphasis -> collectLiteralDelimiters(inline.children, out)
            is Inline.Link -> collectLiteralDelimiters(inline.children, out)
            is Inline.Image -> collectLiteralDelimiters(inline.children, out)
            else -> {}
        }
    }
}

/**
 * Finds the index of the token corresponding to a delimiter record.
 */
private fun findTokenIndex(tokens: List<EmphasisToken>, record: DelimiterRecord): Int {
    var delimIdx = 0
    for ((i, token) in tokens.withIndex()) {
        if (token is EmphasisToken.DelimiterRun) {
            if (delimIdx == 0 && token.char == record.char && token.length == record.originalLength) {
                return i
            }
            delimIdx++
        }
    }
    return -1
}
