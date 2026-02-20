package parsek.commonmark.highlight

/**
 * A mutable accumulator for [Span]s, used as the `U` (user context) type
 * parameter when running a parser in highlighting mode.
 *
 * Pass a [SpanSink] instance as the user context of a [parsek.ParserInput].
 * Parsers wrapped with [pTag] will push [Span]s into [spans] as they succeed.
 * After parsing completes, [spans] contains a flat, ordered list of all
 * highlighted ranges.
 *
 * ### Example
 * ```kotlin
 * val sink  = SpanSink()
 * val input = ParserInput.of(source.toList(), sink)
 * pDocument()(input)
 * val highlights: List<Span> = sink.spans
 * ```
 */
class SpanSink {
    val spans: MutableList<Span> = mutableListOf()
}
