package parsek.markdown.highlight.block

import parsek.Parser
import parsek.Success
import parsek.markdown.ast.Block
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.emit
import parsek.markdown.parser.block.pList
import parsek.markdown.parser.block.pListItem

/**
 * Highlight wrapper for a single CommonMark list item.
 *
 * On success, emits one [TokenType.ListMarker] span covering the marker
 * (bullet character or digits + delimiter) on the item's first line.
 * The span does NOT include leading indentation or the trailing space.
 *
 * @param blockFactory factory for the recursive inner block parser, forwarded
 *   to [pListItem].
 */
fun pListItemHighlight(
    blockFactory: () -> Parser<Char, Block, SpanSink>,
): Parser<Char, Block.ListItem, SpanSink> =
    Parser { input ->
        val start = input.index
        val result = pListItem(blockFactory)(input)
        if (result !is Success) return@Parser result

        val chars = input.input
        val sink = input.userContext

        emitListMarker(chars, start, sink)

        result
    }

/**
 * Highlight wrapper for a CommonMark list (one or more items).
 *
 * On success, emits one [TokenType.ListMarker] span per list item, each
 * covering the item's marker (bullet or digits + delimiter).
 *
 * @param blockFactory factory for the recursive inner block parser, forwarded
 *   to [pList].
 */
fun pListHighlight(
    blockFactory: () -> Parser<Char, Block, SpanSink>,
): Parser<Char, Block, SpanSink> =
    Parser { input ->
        val start = input.index
        val result = pList(blockFactory)(input)
        if (result !is Success) return@Parser result

        val chars = input.input
        val sink = input.userContext

        // Walk the consumed range line by line, emitting a ListMarker for
        // each line that starts a new list item.
        var idx = start
        while (idx < result.nextIndex) {
            emitListMarker(chars, idx, sink)
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
 * If the characters starting at [idx] begin with a list marker (0–3 spaces +
 * bullet or digits+delimiter), emits a [TokenType.ListMarker] span covering
 * just the marker characters (not leading spaces or trailing space).
 */
private fun emitListMarker(chars: List<Char>, idx: Int, sink: SpanSink) {
    var i = idx
    // Skip 0–3 leading spaces/tabs (virtual column counting not needed for
    // marker detection — tabs before the marker are rare in practice).
    var spaces = 0
    while (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) {
        val nextCol = if (chars[i] == '\t') (spaces / 4 + 1) * 4 else spaces + 1
        if (nextCol > 3) break
        spaces = nextCol
        i++
    }

    if (i >= chars.size) return

    // Bullet marker: -, +, *
    val c = chars[i]
    if (c == '-' || c == '+' || c == '*') {
        val afterBullet = i + 1
        // Must be followed by space, tab, or end of line to be a bullet marker.
        val next = chars.getOrNull(afterBullet)
        if (next == null || next == ' ' || next == '\t' || next == '\n' || next == '\r') {
            sink.emit(TokenType.ListMarker, i, afterBullet)
        }
        return
    }

    // Ordered marker: digits + '.' or ')'
    if (c.isDigit()) {
        val digitStart = i
        while (i < chars.size && chars[i].isDigit()) i++
        val digitCount = i - digitStart
        if (digitCount in 1..9) {
            val delim = chars.getOrNull(i)
            if (delim == '.' || delim == ')') {
                val afterDelim = i + 1
                val next = chars.getOrNull(afterDelim)
                if (next == null || next == ' ' || next == '\t' || next == '\n' || next == '\r') {
                    sink.emit(TokenType.ListMarker, digitStart, afterDelim)
                }
            }
        }
    }
}
