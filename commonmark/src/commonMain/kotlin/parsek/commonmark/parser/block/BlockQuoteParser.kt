package parsek.commonmark.parser.block

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.pLabel
import parsek.pMany

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

private fun advancePastLineEndingBq(chars: List<Char>, idx: Int): Int = when {
    idx >= chars.size -> idx
    chars[idx] == '\r' && idx + 1 < chars.size && chars[idx + 1] == '\n' -> idx + 2
    chars[idx] == '\r' || chars[idx] == '\n' -> idx + 1
    else -> idx
}

private fun readRawLineBq(chars: List<Char>, startIdx: Int): Pair<String, Int> {
    var i = startIdx
    while (i < chars.size && chars[i] != '\n' && chars[i] != '\r') i++
    val content = chars.subList(startIdx, i).joinToString("")
    return Pair(content, advancePastLineEndingBq(chars, i))
}

private fun isBlankLineBq(content: String): Boolean =
    content.all { it == ' ' || it == '\t' }

/**
 * If the characters starting at [idx] begin with a block-quote marker
 * (`>` preceded by 0–3 spaces), returns the index immediately after the
 * marker and the optional single space that follows it. Returns `null`
 * if no block-quote marker is present.
 */
private fun consumeBlockQuoteMarker(chars: List<Char>, idx: Int): Int? {
    var i = idx
    var spaces = 0
    while (spaces < 3 && i < chars.size && chars[i] == ' ') { spaces++; i++ }
    if (i >= chars.size || chars[i] != '>') return null
    i++ // consume '>'
    // Consume exactly one optional space after '>'.
    if (i < chars.size && chars[i] == ' ') i++
    return i
}

// ---------------------------------------------------------------------------
// pBlockQuote
// ---------------------------------------------------------------------------

/**
 * Parses a CommonMark block quote (§5.1).
 *
 * A block quote consists of one or more lines each starting with a block-quote
 * marker: 0–3 optional leading spaces followed by `>`, with an optional single
 * space after the `>` (which is stripped as part of the marker). Lines without
 * the `>` prefix that are non-blank and immediately follow a marked or lazy
 * continuation line are accepted as **lazy continuation** lines.
 *
 * The block quote ends at:
 * - A blank line (not consumed)
 * - End of input
 * - A non-blank line without a `>` marker that has no preceding marked line
 *
 * After collecting, the marker-stripped lines are joined with `\n` and
 * recursively parsed using [blockFactory] to produce the inner block list.
 *
 * @param blockFactory a factory that creates the inner block parser when called.
 *   Receiving a factory (rather than the parser directly) enables mutual
 *   recursion between `pBlockQuote` and the top-level block parser, which
 *   itself includes `pBlockQuote` as one of its alternatives.
 *
 * @return a [Parser] that succeeds with [Block.BlockQuote] or fails.
 */
fun <U : Any> pBlockQuote(
    blockFactory: () -> Parser<Char, Block, U>,
): Parser<Char, Block.BlockQuote, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            var idx = input.index
            val blockLines = mutableListOf<String>()
            var seenMark = false

            while (idx < chars.size) {
                val afterMark = consumeBlockQuoteMarker(chars, idx)
                if (afterMark != null) {
                    // Block-quote-marked line: strip the marker and collect the content.
                    seenMark = true
                    val (content, nextIdx) = readRawLineBq(chars, afterMark)
                    blockLines.add(content)
                    idx = nextIdx
                } else {
                    // No marker on this line.
                    val (content, nextIdx) = readRawLineBq(chars, idx)
                    if (!seenMark || isBlankLineBq(content)) break
                    // Lazy continuation: a non-blank line following a marked line.
                    blockLines.add(content)
                    idx = nextIdx
                }
            }

            if (blockLines.isEmpty())
                return@Parser Failure("block quote", input.index, input)

            // Recursively parse the stripped content as a sequence of blocks.
            val innerText = blockLines.joinToString("\n") + "\n"
            val innerChars = innerText.toList()
            val innerInput = ParserInput(innerChars, 0, input.userContext)
            val pBlock = blockFactory()
            val blocksResult = pMany(pBlock)(innerInput) as Success
            val blocks = blocksResult.value

            Success(Block.BlockQuote(blocks), idx, input)
        },
        "block quote",
    )
