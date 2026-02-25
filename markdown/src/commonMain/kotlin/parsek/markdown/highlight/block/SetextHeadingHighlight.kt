package parsek.markdown.highlight.block

import parsek.Parser
import parsek.Success
import parsek.markdown.ast.Block
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.emit
import parsek.markdown.parser.block.pSetextHeading
import parsek.markdown.parser.block.setextUnderlineLevel

/**
 * Highlight wrapper for a CommonMark setext heading.
 *
 * On success, emits:
 * - [TokenType.HeadingText] for the content lines (excluding the underline)
 * - [TokenType.HeadingMarker] for the `=` or `-` underline
 */
fun pSetextHeadingHighlight(): Parser<Char, Block.Heading, SpanSink> =
    Parser { input ->
        val start = input.index
        val result = pSetextHeading<SpanSink>()(input)
        if (result !is Success) return@Parser result

        val chars = input.input
        val sink = input.userContext

        // Re-walk the consumed input to find the underline line.
        // The underline is the last line before result.nextIndex.
        // Walk lines from start, checking each for setext underline status.
        var idx = start
        var underlineStart = -1

        while (idx < result.nextIndex) {
            // Check if this line is a setext underline
            if (underlineStart == -1 && setextUnderlineLevel(chars, idx) != null) {
                // We need to ensure there's content before this line.
                // But the parser already validated this, so just check if idx > start.
                if (idx > start) {
                    underlineStart = idx
                    break
                }
            }
            // Advance to next line
            while (idx < result.nextIndex && chars[idx] != '\n' && chars[idx] != '\r') idx++
            if (idx < result.nextIndex) {
                if (chars[idx] == '\r' && idx + 1 < result.nextIndex && chars[idx + 1] == '\n') idx += 2
                else idx++
            }
        }

        if (underlineStart == -1) {
            // Fallback: shouldn't happen for valid setext headings, but be safe.
            // Walk backwards from the end to find the last line.
            var end = result.nextIndex
            // Skip trailing line ending
            if (end > start && chars[end - 1] == '\n') end--
            if (end > start && chars[end - 1] == '\r') end--
            underlineStart = end
            while (underlineStart > start && chars[underlineStart - 1] != '\n' && chars[underlineStart - 1] != '\r') {
                underlineStart--
            }
        }

        // Content = everything from start up to (but not including) the underline line.
        // We need to find the end of the last content line (before the line ending that
        // precedes the underline).
        var contentEnd = underlineStart
        // Back up past the line ending before the underline
        if (contentEnd > start && chars[contentEnd - 1] == '\n') contentEnd--
        if (contentEnd > start && chars[contentEnd - 1] == '\r') contentEnd--

        sink.emit(TokenType.HeadingText, start, contentEnd)

        // Underline: from underlineStart to end of that line (excluding trailing line ending).
        var underlineEnd = underlineStart
        while (underlineEnd < result.nextIndex && chars[underlineEnd] != '\n' && chars[underlineEnd] != '\r') {
            underlineEnd++
        }
        sink.emit(TokenType.HeadingMarker, underlineStart, underlineEnd)

        result
    }
