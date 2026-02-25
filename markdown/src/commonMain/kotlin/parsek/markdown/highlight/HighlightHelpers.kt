package parsek.markdown.highlight

/**
 * Emits a [Span] into this [SpanSink] if the range is non-empty.
 *
 * A span is only recorded when `end > start`, preventing zero-width spans
 * from cluttering the output when optional constructs (like an info string
 * or link title) are absent.
 *
 * @param type the [TokenType] to attach to the span.
 * @param start the index of the first character (inclusive).
 * @param end the index one past the last character (exclusive).
 */
fun SpanSink.emit(type: TokenType, start: Int, end: Int) {
    if (end > start) {
        spans += Span(type, start, end)
    }
}
