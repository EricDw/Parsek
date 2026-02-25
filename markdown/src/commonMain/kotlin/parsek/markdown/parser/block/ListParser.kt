package parsek.markdown.parser.block

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.markdown.ast.Block
import parsek.markdown.ast.Inline
import parsek.pLabel
import parsek.pMany

// ---------------------------------------------------------------------------
// Line-level helpers
// ---------------------------------------------------------------------------

private fun advancePastLineEndingLi(chars: List<Char>, idx: Int): Int = when {
    idx >= chars.size -> idx
    chars[idx] == '\r' && idx + 1 < chars.size && chars[idx + 1] == '\n' -> idx + 2
    chars[idx] == '\r' || chars[idx] == '\n' -> idx + 1
    else -> idx
}

private fun readRawLineLi(chars: List<Char>, startIdx: Int): Pair<String, Int> {
    var i = startIdx
    while (i < chars.size && chars[i] != '\n' && chars[i] != '\r') i++
    val content = chars.subList(startIdx, i).joinToString("")
    return Pair(content, advancePastLineEndingLi(chars, i))
}

private fun isBlankLi(s: String): Boolean = s.all { it == ' ' || it == '\t' }

/**
 * Expands leading tabs in a string to spaces, given that the string starts at
 * absolute column [startCol]. Non-whitespace characters and everything after them
 * are preserved as-is.
 */
private fun expandLeadingTabs(s: String, startCol: Int): String {
    var col = startCol
    var i = 0
    val sb = StringBuilder()
    while (i < s.length && (s[i] == ' ' || s[i] == '\t')) {
        if (s[i] == '\t') {
            val nextStop = (col / 4 + 1) * 4
            repeat(nextStop - col) { sb.append(' ') }
            col = nextStop
        } else {
            sb.append(' ')
            col++
        }
        i++
    }
    sb.append(s.substring(i))
    return sb.toString()
}

private fun countLeadingSpacesLi(s: String): Int = countVirtualIndentStr(s)

private fun stripLeadingSpacesLi(s: String, count: Int): String {
    // Expand all leading whitespace (spaces+tabs) to virtual spaces, strip count, return rest.
    var col = 0
    var i = 0
    while (i < s.length && (s[i] == ' ' || s[i] == '\t')) {
        col = if (s[i] == '\t') (col / 4 + 1) * 4 else col + 1
        i++
    }
    val remaining = col - count
    return if (remaining > 0) " ".repeat(remaining) + s.substring(i) else s.substring(i)
}

// ---------------------------------------------------------------------------
// Marker detection
// ---------------------------------------------------------------------------

private enum class MarkerKind { BULLET, ORDERED }

/**
 * All information extracted from a list-item marker.
 *
 * @property kind whether this is a bullet or ordered marker.
 * @property bulletChar the bullet character (`-`, `+`, or `*`); only valid for [MarkerKind.BULLET].
 * @property orderedStart the starting number; only valid for [MarkerKind.ORDERED].
 * @property orderedDelimiter the delimiter (`.` or `)`); only valid for [MarkerKind.ORDERED].
 * @property W the content column: continuation lines must be indented by at least this many spaces.
 * @property contentStartIdx the index in the original input where the first content character begins.
 */
private data class Marker(
    val kind: MarkerKind,
    val bulletChar: Char,
    val orderedStart: Int,
    val orderedDelimiter: Char,
    val W: Int,
    val contentStartIdx: Int,
    /** Virtual spaces remaining from a partially-consumed tab at the marker boundary. */
    val remainderSpaces: Int = 0,
)

/**
 * Attempts to parse a list marker at [idx] in [chars], allowing 0–3 leading spaces.
 *
 * Returns a [Marker] describing the marker and content position, or `null` if no
 * valid list marker is present.
 *
 * The content column `W` is computed as:
 * - `leading + markerLength + min(spacesAfter, 4)` when 1–4 spaces follow the marker.
 * - `leading + markerLength + 1` when the first line is empty or ≥ 5 spaces follow.
 *
 * When ≥ 5 spaces follow, only one space is consumed as part of the marker; the
 * remaining spaces become part of the first content line (enabling indented code
 * blocks within list items).
 */
private fun detectMarker(chars: List<Char>, idx: Int): Marker? {
    var i = idx
    // Count leading whitespace using virtual columns (tabs expand to next tab stop).
    var leading = 0
    while (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) {
        val nextCol = if (chars[i] == '\t') (leading / 4 + 1) * 4 else leading + 1
        if (nextCol > 3) break  // 0–3 virtual columns only
        leading = nextCol
        i++
    }

    // --- Bullet marker: -, +, or * ---
    val bc = chars.getOrNull(i)
    if (bc == '-' || bc == '+' || bc == '*') {
        val afterBullet = i + 1
        if (afterBullet >= chars.size ||
            chars[afterBullet] == '\n' || chars[afterBullet] == '\r'
        ) {
            return Marker(MarkerKind.BULLET, bc, 0, ' ',
                W = leading + 1 + 1, contentStartIdx = afterBullet)
        }
        if (chars[afterBullet] != ' ' && chars[afterBullet] != '\t') return null
        // Count virtual spaces after the marker.
        val colAfterMarker = leading + 1  // absolute column right after bullet
        val (virtualSpacesAfter, j) = countVirtualIndent(chars, afterBullet, colAfterMarker)
        val firstLineBlank = j >= chars.size || chars[j] == '\n' || chars[j] == '\r'
        val effectiveSpaces = when {
            firstLineBlank -> 1
            virtualSpacesAfter > 4 -> 1
            else -> virtualSpacesAfter
        }
        // For contentStart, we need to consume exactly effectiveSpaces virtual columns.
        var bulletRemainder = 0
        val contentStart = when {
            firstLineBlank -> j
            virtualSpacesAfter > 4 -> {
                // Consume exactly 1 virtual space after marker.
                val (ci, rem) = consumeVirtualColumns(chars, afterBullet, 1, colAfterMarker)
                bulletRemainder = rem
                ci
            }
            else -> j
        }
        return Marker(MarkerKind.BULLET, bc, 0, ' ',
            W = leading + 1 + effectiveSpaces, contentStartIdx = contentStart,
            remainderSpaces = bulletRemainder)
    }

    // --- Ordered marker: 1–9 digits + '.' or ')' ---
    val digitStart = i
    while (i < chars.size && chars[i].isDigit()) i++
    val digitCount = i - digitStart
    if (digitCount == 0 || digitCount > 9) return null
    val delim = chars.getOrNull(i)
    if (delim != '.' && delim != ')') return null
    val number = chars.subList(digitStart, i).joinToString("").toInt()
    val markerLen = digitCount + 1   // digits + delimiter
    val afterDelim = i + 1
    if (afterDelim >= chars.size ||
        chars[afterDelim] == '\n' || chars[afterDelim] == '\r'
    ) {
        return Marker(MarkerKind.ORDERED, ' ', number, delim,
            W = leading + markerLen + 1, contentStartIdx = afterDelim)
    }
    if (chars[afterDelim] != ' ' && chars[afterDelim] != '\t') return null
    val colAfterDelim = leading + markerLen
    val (virtualSpacesAfter, j) = countVirtualIndent(chars, afterDelim, colAfterDelim)
    val firstLineBlankOrd = j >= chars.size || chars[j] == '\n' || chars[j] == '\r'
    val effectiveSpaces = when {
        firstLineBlankOrd -> 1
        virtualSpacesAfter > 4 -> 1
        else -> virtualSpacesAfter
    }
    var ordRemainder = 0
    val contentStart = when {
        firstLineBlankOrd -> j
        virtualSpacesAfter > 4 -> {
            val (ci, rem) = consumeVirtualColumns(chars, afterDelim, 1, colAfterDelim)
            ordRemainder = rem
            ci
        }
        else -> j
    }
    return Marker(MarkerKind.ORDERED, ' ', number, delim,
        W = leading + markerLen + effectiveSpaces, contentStartIdx = contentStart,
        remainderSpaces = ordRemainder)
}

// ---------------------------------------------------------------------------
// Thematic break detection (for list interruption)
// ---------------------------------------------------------------------------

/**
 * Returns `true` if the line starting at [startIdx] in [chars] is a thematic break.
 * A thematic break is 0–3 spaces, then 3+ of the same marker (`-`, `_`, `*`),
 * optionally interspersed with spaces/tabs, then only spaces/tabs until EOL/EOF.
 */
internal fun isThematicBreakLine(chars: List<Char>, startIdx: Int): Boolean {
    var i = startIdx
    var spaces = 0
    while (spaces < 3 && i < chars.size && chars[i] == ' ') { spaces++; i++ }
    val marker = chars.getOrNull(i)
    if (marker != '-' && marker != '_' && marker != '*') return false
    var count = 0
    while (i < chars.size && chars[i] != '\n' && chars[i] != '\r') {
        when (chars[i]) {
            marker -> count++
            ' ', '\t' -> {}
            else -> return false
        }
        i++
    }
    return count >= 3
}

// ---------------------------------------------------------------------------
// Setext underline detection (for lazy continuation fixup)
// ---------------------------------------------------------------------------

/**
 * Returns `true` if [content] is a setext heading underline.
 */
private fun isSetextUnderlineLi(content: String): Boolean {
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

// ---------------------------------------------------------------------------
// Item line collection
// ---------------------------------------------------------------------------

private data class CollectedLines(
    /** Inner content lines, each with [W] leading spaces stripped. */
    val lines: List<String>,
    /** Index after the last committed (non-blank) continuation line. Trailing blank lines are NOT consumed. */
    val nextIdx: Int,
    /** `true` if at least one blank line was absorbed (appeared between continuation lines). */
    val hadInternalBlank: Boolean,
    /** For each line, whether it was a lazy continuation (< W indent). */
    val isLazy: List<Boolean> = emptyList(),
)

/**
 * Collects the content lines of a list item starting from [afterFirstLine].
 *
 * [firstContent] is the first-line content, already stripped of the marker prefix.
 * [W] is the content column: subsequent lines must be indented by at least [W] spaces
 * to be considered part of this item.
 *
 * Blank lines are absorbed (included in the item) only when followed by a sufficiently
 * indented continuation line; otherwise they are left unconsumed.
 */
private data class CollectedLineInfo(
    val content: String,
    val isLazy: Boolean,
)

private fun collectItemLines(
    chars: List<Char>,
    firstContent: String,
    afterFirstLine: Int,
    W: Int,
): CollectedLines {
    val lines = mutableListOf<CollectedLineInfo>()
    val firstIsBlank = firstContent.isEmpty() || isBlankLi(firstContent)
    if (firstContent.isNotEmpty()) lines.add(CollectedLineInfo(firstContent, false))

    var idx = afterFirstLine
    var commitIdx = afterFirstLine
    var pendingBlanks = 0
    var hadBlank = false
    // Track whether the inner content's last block is a paragraph,
    // which allows lazy continuation of subsequent under-indented lines.
    var inParagraph = !firstIsBlank

    while (idx < chars.size) {
        val (lineContent, nextIdx) = readRawLineLi(chars, idx)
        when {
            isBlankLi(lineContent) -> {
                // If the first line was blank (empty item), a blank line
                // terminates the item immediately (no continuation allowed).
                if (firstIsBlank) break
                pendingBlanks++
                inParagraph = false  // blank line ends paragraph context
                idx = nextIdx
            }
            countLeadingSpacesLi(lineContent) >= W -> {
                if (pendingBlanks > 0) {
                    hadBlank = true
                    repeat(pendingBlanks) { lines.add(CollectedLineInfo("", false)) }
                    pendingBlanks = 0
                }
                val stripped = stripLeadingSpacesLi(lineContent, W)
                lines.add(CollectedLineInfo(stripped, false))
                // After blank + normal continuation, the new line might start a
                // new block or continue a paragraph — conservatively assume paragraph.
                inParagraph = !isBlankLi(stripped)
                idx = nextIdx
                commitIdx = nextIdx
            }
            else -> {
                // Line has fewer than W leading spaces.
                // Lazy continuation: per §5.2, if the inner content has an open
                // paragraph, a non-blank continuation line that doesn't interrupt
                // a paragraph at the outer level can be lazily included.
                if (pendingBlanks > 0) break  // blank + lazy = paragraph ended
                if (!inParagraph) break
                if (canInterruptParagraph(chars, idx)) break
                // If this line could start a new list item at the outer level,
                // it's not a lazy continuation — it's a new item.
                if (detectMarker(chars, idx) != null) break
                // Include as lazy continuation — strip up to W spaces.
                val stripped = stripLeadingSpacesLi(lineContent, W)
                lines.add(CollectedLineInfo(stripped, true))
                idx = nextIdx
                commitIdx = nextIdx
            }
        }
    }

    return CollectedLines(
        lines.map { it.content },
        commitIdx,
        hadBlank,
        lines.map { it.isLazy },
    )
}

// ---------------------------------------------------------------------------
// Internal parse-result type
// ---------------------------------------------------------------------------

/**
 * Recursively finds the deepest open paragraph in a block structure and extends it
 * with [lazyLines]. Traverses into the last block of block quotes, list items, and
 * lists to find the innermost paragraph.
 */
private fun extendDeepestParagraph(blocks: MutableList<Block>, lazyLines: List<String>) {
    // Try to find the deepest paragraph by traversing into nested containers.
    val lastIdx = blocks.lastIndex
    if (lastIdx < 0) return

    when (val last = blocks[lastIdx]) {
        is Block.Paragraph -> {
            val existingText = last.inlines.filterIsInstance<Inline.Text>()
                .joinToString("") { it.literal }
            val extended = (existingText + "\n" + lazyLines.joinToString("\n")).trimEnd()
            blocks[lastIdx] = Block.Paragraph(listOf(Inline.Text(extended)))
        }
        is Block.BlockQuote -> {
            val innerBlocks = last.blocks.toMutableList()
            extendDeepestParagraph(innerBlocks, lazyLines)
            blocks[lastIdx] = Block.BlockQuote(innerBlocks)
        }
        is Block.BulletList -> {
            val items = last.items.toMutableList()
            if (items.isNotEmpty()) {
                val lastItem = items.last()
                val itemBlocks = lastItem.blocks.toMutableList()
                extendDeepestParagraph(itemBlocks, lazyLines)
                items[items.lastIndex] = Block.ListItem(itemBlocks)
                blocks[lastIdx] = Block.BulletList(last.tight, last.marker, items)
            }
        }
        is Block.OrderedList -> {
            val items = last.items.toMutableList()
            if (items.isNotEmpty()) {
                val lastItem = items.last()
                val itemBlocks = lastItem.blocks.toMutableList()
                extendDeepestParagraph(itemBlocks, lazyLines)
                items[items.lastIndex] = Block.ListItem(itemBlocks)
                blocks[lastIdx] = Block.OrderedList(last.tight, last.start, last.delimiter, items)
            }
        }
        else -> {} // Cannot extend non-container blocks
    }
}

private data class ItemResult<U : Any>(
    val marker: Marker,
    val item: Block.ListItem,
    val hadInternalBlank: Boolean,
    val nextIdx: Int,
)

private fun <U : Any> tryParseItem(
    chars: List<Char>,
    startIdx: Int,
    input: ParserInput<Char, U>,
    blockFactory: () -> Parser<Char, Block, U>,
): ItemResult<U>? {
    val marker = detectMarker(chars, startIdx) ?: return null
    val (rawFirstContent, afterFirstLine) = readRawLineLi(chars, marker.contentStartIdx)
    // Expand any remaining tabs in the first content line prefix to spaces,
    // accounting for the absolute column position and remainder from marker detection.
    val firstContent = if (marker.remainderSpaces > 0) {
        " ".repeat(marker.remainderSpaces) + expandLeadingTabs(rawFirstContent, marker.W + marker.remainderSpaces)
    } else {
        expandLeadingTabs(rawFirstContent, marker.W)
    }
    val collected = collectItemLines(chars, firstContent, afterFirstLine, marker.W)

    val hasLazyLines = collected.isLazy.any { it }

    val pBlock = blockFactory()
    val blocks: List<Block>

    if (hasLazyLines) {
        // Split lines into groups of normal (non-lazy) segments, separated by
        // lazy continuation lines. Normal segments are parsed through the block
        // parser; lazy lines extend the preceding paragraph's text.
        val parsedBlocks = mutableListOf<Block>()
        var i = 0
        while (i < collected.lines.size) {
            // Collect a run of normal lines.
            val normalStart = i
            while (i < collected.lines.size && !collected.isLazy[i]) i++
            if (i > normalStart) {
                val segmentText = collected.lines.subList(normalStart, i)
                    .joinToString("\n") + "\n"
                val segChars = segmentText.toList()
                val segInput = ParserInput(segChars, 0, input.userContext)
                val segResult = pMany(pBlock)(segInput) as Success
                parsedBlocks.addAll(segResult.value)
            }
            // Collect a run of lazy lines and extend the deepest open paragraph.
            if (i < collected.lines.size && collected.isLazy[i]) {
                val lazyLines = mutableListOf<String>()
                while (i < collected.lines.size && collected.isLazy[i]) {
                    lazyLines.add(collected.lines[i])
                    i++
                }
                extendDeepestParagraph(parsedBlocks, lazyLines)
            }
        }
        blocks = parsedBlocks
    } else {
        val innerText = collected.lines.joinToString("\n") + "\n"
        val innerChars = innerText.toList()
        val innerInput = ParserInput(innerChars, 0, input.userContext)
        val blocksResult = pMany(pBlock)(innerInput) as Success
        blocks = blocksResult.value
    }

    // Determine looseness from parsed blocks: a blank line between non-blank
    // blocks at the top level means the item has internal blanks. Blank lines
    // inside fenced code blocks or nested sublists are absorbed by those parsers
    // and do NOT appear as top-level BlankLine nodes.
    val hasInternalBlank = run {
        var seenContent = false
        var seenBlankAfterContent = false
        for (b in blocks) {
            if (b is Block.BlankLine) {
                if (seenContent) seenBlankAfterContent = true
            } else {
                if (seenBlankAfterContent) return@run true
                seenContent = true
            }
        }
        false
    }

    return ItemResult(marker, Block.ListItem(blocks), hasInternalBlank, collected.nextIdx)
}

// ---------------------------------------------------------------------------
// pListItem
// ---------------------------------------------------------------------------

/**
 * Parses a single CommonMark list item (§5.2).
 *
 * The first line must begin with a list marker preceded by 0–3 optional spaces:
 * - **Bullet**: `-`, `+`, or `*` followed by 1–4 spaces (or end of line for an empty item).
 * - **Ordered**: 1–9 digits followed by `.` or `)`, then 1–4 spaces (or end of line).
 *
 * Continuation lines must be indented by at least `W` spaces, where `W` is the
 * content column determined by the first line. Blank lines within the item are
 * absorbed when followed by an indented continuation line; otherwise they are
 * left unconsumed and the item ends.
 *
 * The item content is recursively parsed using [blockFactory].
 *
 * @return a [Parser] that succeeds with [Block.ListItem] or fails.
 */
fun <U : Any> pListItem(
    blockFactory: () -> Parser<Char, Block, U>,
): Parser<Char, Block.ListItem, U> =
    pLabel(
        Parser { input ->
            val r = tryParseItem(input.input, input.index, input, blockFactory)
                ?: return@Parser Failure("list item", input.index, input)
            Success(r.item, r.nextIdx, input)
        },
        "list item",
    )

// ---------------------------------------------------------------------------
// pList
// ---------------------------------------------------------------------------

/**
 * Parses a CommonMark list (§5.3) — one or more consecutive compatible list items.
 *
 * List items are **compatible** when they share the same marker kind (bullet or
 * ordered) and the same marker character or delimiter. Blank lines between items
 * are consumed as part of the list and cause it to be **loose**; blank lines
 * inside any item also make the list loose.
 *
 * Trailing blank lines after the final item are **not** consumed.
 *
 * Returns a [Block.BulletList] or [Block.OrderedList] depending on the marker type.
 * The `start` number of an ordered list is taken from the first item's marker.
 *
 * @return a [Parser] that succeeds with [Block.BulletList] or [Block.OrderedList], or fails.
 */
fun <U : Any> pList(
    blockFactory: () -> Parser<Char, Block, U>,
): Parser<Char, Block, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            val first = tryParseItem(chars, input.index, input, blockFactory)
                ?: return@Parser Failure("list", input.index, input)

            val items = mutableListOf(first.item)
            var loose = first.hadInternalBlank
            var idx = first.nextIdx

            while (idx < chars.size) {
                // Peek past blank lines without committing.
                var tempIdx = idx
                var blankCount = 0
                while (tempIdx < chars.size) {
                    val (lc, nIdx) = readRawLineLi(chars, tempIdx)
                    if (!isBlankLi(lc)) break
                    blankCount++
                    tempIdx = nIdx
                }

                // A thematic break terminates the list.
                if (isThematicBreakLine(chars, tempIdx)) break

                // Try to parse the next item (possibly after blank lines).
                val next = tryParseItem(chars, tempIdx, input, blockFactory) ?: break

                // Must be the same marker kind and same character/delimiter.
                if (next.marker.kind != first.marker.kind) break
                if (next.marker.kind == MarkerKind.BULLET &&
                    next.marker.bulletChar != first.marker.bulletChar) break
                if (next.marker.kind == MarkerKind.ORDERED &&
                    next.marker.orderedDelimiter != first.marker.orderedDelimiter) break

                // Commit: consume the blank lines and the next item.
                if (blankCount > 0) loose = true
                if (next.hadInternalBlank) loose = true
                items.add(next.item)
                idx = next.nextIdx
            }

            val block: Block = when (first.marker.kind) {
                MarkerKind.BULLET ->
                    Block.BulletList(!loose, first.marker.bulletChar, items)
                MarkerKind.ORDERED ->
                    Block.OrderedList(!loose, first.marker.orderedStart, first.marker.orderedDelimiter, items)
            }
            Success(block, idx, input)
        },
        "list",
    )
