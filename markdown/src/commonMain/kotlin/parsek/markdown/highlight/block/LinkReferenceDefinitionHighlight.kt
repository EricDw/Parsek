package parsek.markdown.highlight.block

import parsek.Parser
import parsek.Success
import parsek.markdown.ast.Block
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.emit
import parsek.markdown.parser.block.parseLinkDestination
import parsek.markdown.parser.block.parseLinkLabel
import parsek.markdown.parser.block.parseLinkTitle
import parsek.markdown.parser.block.pLinkReferenceDefinition

/**
 * Highlight wrapper for a CommonMark link reference definition.
 *
 * On success, emits:
 * - [TokenType.LinkLabel] for the `[label]` (including brackets)
 * - [TokenType.LinkDestination] for the destination URL
 * - [TokenType.LinkTitle] for the title (if present, including delimiters)
 */
fun pLinkReferenceDefinitionHighlight(): Parser<Char, Block.LinkReferenceDefinition, SpanSink> =
    Parser { input ->
        val start = input.index
        val result = pLinkReferenceDefinition<SpanSink>()(input)
        if (result !is Success) return@Parser result

        val chars = input.input
        val sink = input.userContext
        var idx = start

        // Skip 0–3 leading spaces.
        var spaces = 0
        while (spaces < 3 && idx < chars.size && chars[idx] == ' ') { spaces++; idx++ }

        // Link label [content]
        val labelStart = idx
        val (_, afterLabel) = parseLinkLabel(chars, idx)!!
        sink.emit(TokenType.LinkLabel, labelStart, afterLabel)
        idx = afterLabel

        // Skip ':' after the label.
        idx++ // consume ':'

        // Skip optional spaces/tabs, then at most one line ending.
        while (idx < chars.size && (chars[idx] == ' ' || chars[idx] == '\t')) idx++
        if (idx < chars.size && (chars[idx] == '\n' || chars[idx] == '\r')) {
            if (chars[idx] == '\r' && idx + 1 < chars.size && chars[idx + 1] == '\n') idx += 2
            else idx++
            while (idx < chars.size && (chars[idx] == ' ' || chars[idx] == '\t')) idx++
        }

        // Link destination.
        val destStart = idx
        val (_, afterDest) = parseLinkDestination(chars, idx)!!
        sink.emit(TokenType.LinkDestination, destStart, afterDest)
        idx = afterDest

        // Optional title.
        val posAfterDest = idx

        // Skip optional spaces/tabs.
        while (idx < chars.size && (chars[idx] == ' ' || chars[idx] == '\t')) idx++
        val hadWs = idx > posAfterDest

        // Skip optional one line ending.
        var hadLineEnding = false
        if (idx < chars.size && (chars[idx] == '\n' || chars[idx] == '\r')) {
            if (chars[idx] == '\r' && idx + 1 < chars.size && chars[idx + 1] == '\n') idx += 2
            else idx++
            hadLineEnding = true
            while (idx < chars.size && (chars[idx] == ' ' || chars[idx] == '\t')) idx++
        }

        val c = chars.getOrNull(idx)
        if ((hadWs || hadLineEnding) && (c == '"' || c == '\'' || c == '(')) {
            val titleResult = parseLinkTitle(chars, idx)
            if (titleResult != null) {
                val (_, afterTitle) = titleResult
                // Verify the rest of the line is clean (as the original parser does).
                var checkIdx = afterTitle
                while (checkIdx < chars.size && (chars[checkIdx] == ' ' || chars[checkIdx] == '\t')) checkIdx++
                if (checkIdx >= chars.size || chars[checkIdx] == '\n' || chars[checkIdx] == '\r') {
                    sink.emit(TokenType.LinkTitle, idx, afterTitle)
                }
            }
        }

        result
    }
