package parsek.markdown.highlight.block

import parsek.Parser
import parsek.markdown.ast.Block
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.pTag
import parsek.markdown.parser.block.pTable

/**
 * Highlight wrapper for a GFM table.
 *
 * The entire table range is tagged as [TokenType.TableDelimiter]. Individual
 * header and body cell spans are emitted during inline resolution.
 */
fun pTableHighlight(): Parser<Char, Block.Table, SpanSink> =
    pTag(TokenType.TableDelimiter, pTable())
