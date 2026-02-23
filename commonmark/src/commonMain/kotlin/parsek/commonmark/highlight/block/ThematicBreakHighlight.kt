package parsek.commonmark.highlight.block

import parsek.Parser
import parsek.commonmark.ast.Block
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import parsek.commonmark.highlight.pTag
import parsek.commonmark.parser.block.pThematicBreak

/**
 * Highlight wrapper for a CommonMark thematic break.
 *
 * The entire consumed range is tagged as [TokenType.ThematicBreak].
 */
fun pThematicBreakHighlight(): Parser<Char, Block.ThematicBreak, SpanSink> =
    pTag(TokenType.ThematicBreak, pThematicBreak())
