package parsek.markdown.highlight.block

import parsek.Parser
import parsek.Success
import parsek.markdown.ast.Block
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.emit
import parsek.markdown.parser.block.pIndentedCodeBlock

/**
 * Highlight wrapper for a CommonMark indented code block.
 *
 * On success, emits one [TokenType.CodeContent] span per consumed line
 * (including blank interstitial lines). Each span covers the full line
 * including its line ending.
 */
fun pIndentedCodeBlockHighlight(): Parser<Char, Block.IndentedCodeBlock, SpanSink> =
    Parser { input ->
        val start = input.index
        val result = pIndentedCodeBlock<SpanSink>()(input)
        if (result !is Success) return@Parser result

        val chars = input.input
        val sink = input.userContext

        // Emit one CodeContent span per line in the consumed range.
        var idx = start
        while (idx < result.nextIndex) {
            val lineStart = idx
            // Advance to end of line
            while (idx < result.nextIndex && chars[idx] != '\n' && chars[idx] != '\r') idx++
            // Consume line ending
            if (idx < result.nextIndex) {
                if (chars[idx] == '\r' && idx + 1 < result.nextIndex && chars[idx + 1] == '\n') idx += 2
                else idx++
            }
            sink.emit(TokenType.CodeContent, lineStart, idx)
        }

        result
    }
