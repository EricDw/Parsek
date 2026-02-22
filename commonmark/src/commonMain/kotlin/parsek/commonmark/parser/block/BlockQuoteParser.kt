package parsek.commonmark.parser.block

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.commonmark.ast.Inline
import parsek.pLabel

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
// Inner state tracking for lazy continuation
// ---------------------------------------------------------------------------

/**
 * Returns `true` if [content] is a setext heading underline: one or more
 * `=` or `-` characters, optionally preceded by up to 3 spaces, with optional
 * trailing spaces.
 */
private fun isSetextUnderline(content: String): Boolean {
    var i = 0
    var spaces = 0
    while (spaces < 3 && i < content.length && content[i] == ' ') { spaces++; i++ }
    if (i >= content.length) return false
    val ch = content[i]
    if (ch != '=' && ch != '-') return false
    while (i < content.length && content[i] == ch) i++
    while (i < content.length && (content[i] == ' ' || content[i] == '\t')) i++
    return i == content.length
}

/**
 * Returns `true` if [content] starts an indented code block (4+ leading spaces).
 */
private fun startsIndentedCode(content: String): Boolean {
    var spaces = 0
    for (ch in content) {
        if (ch == ' ') spaces++ else break
    }
    return spaces >= 4
}

/**
 * Returns `true` if [content] opens a fenced code block (3+ backticks or tildes).
 * Also sets [outChar] and [outLen] via the callback.
 */
private fun detectFenceOpening(content: String): Triple<Boolean, Char, Int>? {
    var i = 0
    var spaces = 0
    while (spaces < 3 && i < content.length && content[i] == ' ') { spaces++; i++ }
    if (i >= content.length) return null
    val ch = content[i]
    if (ch != '`' && ch != '~') return null
    var count = 0
    while (i < content.length && content[i] == ch) { count++; i++ }
    if (count < 3) return null
    // Backtick fences: info string must not contain backticks
    if (ch == '`') {
        for (j in i until content.length) {
            if (content[j] == '`') return null
        }
    }
    return Triple(true, ch, count)
}

/**
 * Returns `true` if [content] closes a fenced code block opened with [fenceChar]
 * of length [fenceLen].
 */
private fun isFenceClosing(content: String, fenceChar: Char, fenceLen: Int): Boolean {
    var i = 0
    var spaces = 0
    while (spaces < 3 && i < content.length && content[i] == ' ') { spaces++; i++ }
    if (i >= content.length || content[i] != fenceChar) return false
    var count = 0
    while (i < content.length && content[i] == fenceChar) { count++; i++ }
    if (count < fenceLen) return false
    while (i < content.length) {
        if (content[i] != ' ' && content[i] != '\t') return false
        i++
    }
    return true
}

/**
 * Updates the inner block state of the block quote after processing a marked line.
 * Returns whether lazy continuation is allowed after this line.
 */
private inline fun updateBlockQuoteInnerState(
    content: String,
    inFencedCode: Boolean,
    fenceChar: Char,
    fenceLen: Int,
    updateState: (inFenced: Boolean, fenceChar: Char, fenceLen: Int) -> Unit,
): Boolean {
    if (inFencedCode) {
        // Check for closing fence
        if (isFenceClosing(content, fenceChar, fenceLen)) {
            updateState(false, ' ', 0)
            return false  // just closed a fenced block — not in a paragraph
        }
        return false  // inside fenced code — no lazy continuation
    }

    if (isBlankLineBq(content)) {
        return false  // blank line breaks paragraph context
    }

    // Check if this line opens a fenced code block
    val fence = detectFenceOpening(content)
    if (fence != null) {
        updateState(true, fence.second, fence.third)
        return false  // just opened a fenced block
    }

    // Check if this line starts an indented code block
    if (startsIndentedCode(content)) {
        return false  // indented code block — no lazy continuation
    }

    // Otherwise, this line is part of a paragraph (or starts one)
    return true
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
            val isLazyLine = mutableListOf<Boolean>()
            var seenMark = false
            // Lazy continuation is only valid when the inner content's last open
            // block is a paragraph. We track this with a simple state machine.
            var canLazyContinue = false
            var inFencedCode = false   // inside a fenced code block (opened but not closed)
            var fenceChar: Char = ' '
            var fenceLen = 0

            while (idx < chars.size) {
                val afterMark = consumeBlockQuoteMarker(chars, idx)
                if (afterMark != null) {
                    // Block-quote-marked line: strip the marker and collect the content.
                    seenMark = true
                    val (content, nextIdx) = readRawLineBq(chars, afterMark)
                    blockLines.add(content)
                    isLazyLine.add(false)
                    idx = nextIdx
                    // Update inner state to determine if lazy continuation is allowed.
                    canLazyContinue = updateBlockQuoteInnerState(
                        content, inFencedCode, fenceChar, fenceLen
                    ) { inFenced, fc, fl ->
                        inFencedCode = inFenced
                        fenceChar = fc
                        fenceLen = fl
                    }
                } else {
                    // No marker on this line.
                    val (content, nextIdx) = readRawLineBq(chars, idx)
                    if (!canLazyContinue || isBlankLineBq(content)) break
                    // Lazy continuation is only valid for paragraph continuation lines.
                    // Lines that would start a new block type cannot be lazy-continued.
                    if (canInterruptParagraph(chars, idx)) break
                    blockLines.add(content)
                    isLazyLine.add(true)
                    idx = nextIdx
                }
            }

            if (blockLines.isEmpty())
                return@Parser Failure("block quote", input.index, input)

            // Recursively parse the stripped content as a sequence of blocks.
            val innerText = blockLines.joinToString("\n") + "\n"
            val innerChars = innerText.toList()
            val pBlock = blockFactory()

            // Parse blocks one by one to track positions for lazy-setext fixup.
            val blocks = mutableListOf<Block>()
            // Precompute line start offsets in innerText.
            val lineStartOffsets = mutableListOf<Int>()
            var off = 0
            for (line in blockLines) {
                lineStartOffsets.add(off)
                off += line.length + 1  // +1 for '\n'
            }

            var pos = 0
            while (pos < innerChars.size) {
                val currentInput = ParserInput(innerChars, pos, input.userContext)
                val result = pBlock(currentInput)
                if (result is Success) {
                    var block = result.value
                    val blockEndPos = result.nextIndex

                    // Fix setext headings formed via lazy continuation underlines.
                    // Per spec §4.3: "a setext heading underline cannot be a lazy
                    // continuation line in a block quote or list item."
                    if (block is Block.Heading && (block.level == 1 || block.level == 2)) {
                        // Find the line index of the character just before blockEndPos.
                        val underlineLineIdx = lineStartOffsets.indexOfLast { it < blockEndPos }
                        if (underlineLineIdx >= 0 && underlineLineIdx < isLazyLine.size &&
                            isLazyLine[underlineLineIdx] && isSetextUnderline(blockLines[underlineLineIdx])
                        ) {
                            // The underline was a lazy continuation line — convert to paragraph.
                            // Re-parse from `pos` using only the paragraph parser.
                            val paraText = blockLines.subList(
                                lineStartOffsets.indexOfFirst { it >= pos },
                                underlineLineIdx + 1,
                            ).joinToString("\n").trimEnd()
                            block = Block.Paragraph(
                                if (paraText.isEmpty()) emptyList()
                                else listOf(Inline.Text(paraText))
                            )
                        }
                    }

                    blocks.add(block)
                    pos = blockEndPos
                } else {
                    break
                }
            }

            Success(Block.BlockQuote(blocks), idx, input)
        },
        "block quote",
    )
