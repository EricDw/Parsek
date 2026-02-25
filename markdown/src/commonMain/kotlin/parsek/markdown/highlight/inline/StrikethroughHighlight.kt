package parsek.markdown.highlight.inline

import parsek.markdown.ast.Inline
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.emit

/**
 * Emits [TokenType.StrikethroughMarker] spans for `~~` delimiters consumed
 * by strikethrough processing.
 *
 * This follows the same approach as [emitEmphasisSpans]: after emphasis/
 * strikethrough processing, we walk the delimiter records and emit spans for
 * tilde runs that were consumed as markers (i.e. not left as literal text).
 */
fun emitStrikethroughSpans(
    records: List<DelimiterRecord>,
    inlines: List<Inline>,
    sink: SpanSink,
) {
    // Collect remaining literal tilde characters from the output.
    val literalTildes = mutableListOf<Int>()
    collectLiteralTildes(inlines, literalTildes)

    var litIdx = 0
    for (record in records) {
        if (record.char != '~') continue

        var remaining = 0
        if (litIdx < literalTildes.size) {
            remaining = literalTildes[litIdx]
            litIdx++
        }

        val consumed = record.originalLength - remaining
        if (consumed >= 2) {
            // Opener: consumed from right end.
            sink.emit(TokenType.StrikethroughMarker, record.end - consumed, record.end)
        }
    }
}

private fun collectLiteralTildes(inlines: List<Inline>, out: MutableList<Int>) {
    for (inline in inlines) {
        when (inline) {
            is Inline.Text -> {
                if (inline.literal.isNotEmpty() && inline.literal.all { it == '~' }) {
                    out.add(inline.literal.length)
                }
            }
            is Inline.Strikethrough -> collectLiteralTildes(inline.children, out)
            is Inline.Emphasis -> collectLiteralTildes(inline.children, out)
            is Inline.StrongEmphasis -> collectLiteralTildes(inline.children, out)
            is Inline.Link -> collectLiteralTildes(inline.children, out)
            else -> {}
        }
    }
}
