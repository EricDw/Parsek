package parsek.markdown.highlight.block

import parsek.Parser
import parsek.Success
import parsek.markdown.ast.Block
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.emit
import parsek.markdown.parser.block.pFencedCodeBlock

/**
 * Highlight wrapper for a CommonMark fenced code block.
 *
 * On success, emits:
 * - [TokenType.CodeFence] for the opening fence (including leading indent)
 * - [TokenType.CodeInfo] for the info string (if present)
 * - [TokenType.CodeContent] for each content line (including line endings)
 * - [TokenType.CodeFence] for the closing fence (if present)
 */
fun pFencedCodeBlockHighlight(): Parser<Char, Block.FencedCodeBlock, SpanSink> =
    Parser { input ->
        val start = input.index
        val result = pFencedCodeBlock<SpanSink>()(input)
        if (result !is Success) return@Parser result

        val chars = input.input
        val sink = input.userContext
        var idx = start

        // 1. Opening fence line: skip 0–3 leading spaces, then count fence chars.
        var spaces = 0
        while (spaces < 3 && idx < chars.size && chars[idx] == ' ') { spaces++; idx++ }

        val fenceChar = chars[idx]
        val fenceStart = idx
        var fenceLen = 0
        while (idx < chars.size && chars[idx] == fenceChar) { fenceLen++; idx++ }

        // The fence span covers leading indent + fence chars.
        sink.emit(TokenType.CodeFence, start, idx)

        // Info string: rest of the opening line (trimmed).
        val infoStart = idx
        while (idx < chars.size && chars[idx] != '\n' && chars[idx] != '\r') idx++
        val infoEnd = idx

        // Trim whitespace to determine if there's an info string.
        val infoRaw = chars.subList(infoStart, infoEnd)
        val trimStart = infoRaw.indexOfFirst { it != ' ' && it != '\t' }
        val trimEnd = infoRaw.indexOfLast { it != ' ' && it != '\t' }
        if (trimStart >= 0 && trimEnd >= trimStart) {
            sink.emit(TokenType.CodeInfo, infoStart + trimStart, infoStart + trimEnd + 1)
        }

        // Consume the line ending after the opening fence.
        if (idx < chars.size) {
            if (chars[idx] == '\r' && idx + 1 < chars.size && chars[idx + 1] == '\n') idx += 2
            else idx++
        }

        // 2. Content lines and optional closing fence.
        while (idx < result.nextIndex) {
            val lineStart = idx

            // Check if this is the closing fence.
            var ci = idx
            var cSpaces = 0
            while (cSpaces < 3 && ci < chars.size && chars[ci] == ' ') { cSpaces++; ci++ }

            var isFence = false
            if (ci < chars.size && chars[ci] == fenceChar) {
                var cFenceLen = 0
                val cfStart = ci
                while (ci < chars.size && chars[ci] == fenceChar) { cFenceLen++; ci++ }
                if (cFenceLen >= fenceLen) {
                    // Check only spaces/tabs then line ending or EOF.
                    var afterFence = ci
                    while (afterFence < chars.size && (chars[afterFence] == ' ' || chars[afterFence] == '\t')) afterFence++
                    if (afterFence >= chars.size || chars[afterFence] == '\n' || chars[afterFence] == '\r') {
                        // This is a closing fence — advance idx to end of this line.
                        idx = afterFence
                        if (idx < chars.size) {
                            if (chars[idx] == '\r' && idx + 1 < chars.size && chars[idx + 1] == '\n') idx += 2
                            else idx++
                        }
                        sink.emit(TokenType.CodeFence, lineStart, idx)
                        isFence = true
                    }
                }
            }

            if (!isFence) {
                // Content line.
                while (idx < result.nextIndex && chars[idx] != '\n' && chars[idx] != '\r') idx++
                if (idx < result.nextIndex) {
                    if (chars[idx] == '\r' && idx + 1 < result.nextIndex && chars[idx + 1] == '\n') idx += 2
                    else idx++
                }
                sink.emit(TokenType.CodeContent, lineStart, idx)
            }
        }

        result
    }
