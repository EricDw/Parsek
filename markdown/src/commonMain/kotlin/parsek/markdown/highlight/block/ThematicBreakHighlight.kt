package parsek.markdown.highlight.block

import parsek.Parser
import parsek.markdown.ast.Block
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.pTag
import parsek.markdown.parser.block.pThematicBreak

/**
 * Highlight wrapper for a CommonMark thematic break.
 *
 * The entire consumed range is tagged as [TokenType.ThematicBreak].
 */
fun pThematicBreakHighlight(): Parser<Char, Block.ThematicBreak, SpanSink> =
    pTag(TokenType.ThematicBreak, pThematicBreak())
