package parsek.commonmark.highlight.block

import parsek.Parser
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import parsek.commonmark.highlight.emit
import parsek.commonmark.parser.block.pBlockQuote

/**
 * Highlight wrapper for a CommonMark block quote.
 *
 * On success, emits one [TokenType.BlockQuoteMarker] span per line that
 * carries a `>` marker. The marker span covers the 0–3 leading spaces,
 * the `>` character, and the optional single space after it.
 *
 * Lazy continuation lines (no `>` marker) do not produce a span.
 *
 * @param blockFactory factory for the recursive inner block parser, forwarded
 *   to [pBlockQuote].
 */
fun pBlockQuoteHighlight(
    blockFactory: () -> Parser<Char, Block, SpanSink>,
): Parser<Char, Block.BlockQuote, SpanSink> =
    Parser { input ->
        val start = input.index
        val result = pBlockQuote(blockFactory)(input)
        if (result !is Success) return@Parser result

        val chars = input.input
        val sink = input.userContext

        // Re-walk the consumed range line by line, emitting BlockQuoteMarker
        // for every line that starts with a `>` marker.
        var idx = start
        while (idx < result.nextIndex) {
            val markerEnd = tryConsumeBlockQuoteMarker(chars, idx)
            if (markerEnd != null) {
                sink.emit(TokenType.BlockQuoteMarker, idx, markerEnd)
            }
            // Advance to next line.
            while (idx < result.nextIndex && chars[idx] != '\n' && chars[idx] != '\r') idx++
            if (idx < result.nextIndex) {
                if (chars[idx] == '\r' && idx + 1 < result.nextIndex && chars[idx + 1] == '\n') idx += 2
                else idx++
            }
        }

        result
    }

/**
 * If the characters starting at [idx] begin with a block-quote marker
 * (0–3 spaces + `>`), returns the index immediately after the marker
 * and its optional trailing space. Returns `null` if no marker is present.
 */
private fun tryConsumeBlockQuoteMarker(chars: List<Char>, idx: Int): Int? {
    var i = idx
    var spaces = 0
    while (spaces < 3 && i < chars.size && chars[i] == ' ') { spaces++; i++ }
    if (i >= chars.size || chars[i] != '>') return null
    i++ // consume '>'
    // Consume optional single space (or tab) after '>'.
    if (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) i++
    return i
}
