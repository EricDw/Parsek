package parsek.commonmark.parser.block

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
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

private fun countLeadingSpacesLi(s: String): Int {
    var n = 0
    while (n < s.length && s[n] == ' ') n++
    return n
}

private fun stripLeadingSpacesLi(s: String, count: Int): String {
    var i = 0
    while (i < count && i < s.length && s[i] == ' ') i++
    return s.substring(i)
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
    var leading = 0
    while (leading < 3 && i < chars.size && chars[i] == ' ') { leading++; i++ }

    // --- Bullet marker: -, +, or * ---
    val bc = chars.getOrNull(i)
    if (bc == '-' || bc == '+' || bc == '*') {
        val afterBullet = i + 1
        if (afterBullet >= chars.size ||
            chars[afterBullet] == '\n' || chars[afterBullet] == '\r'
        ) {
            // Empty first line.
            return Marker(MarkerKind.BULLET, bc, 0, ' ',
                W = leading + 1 + 1, contentStartIdx = afterBullet)
        }
        if (chars[afterBullet] != ' ' && chars[afterBullet] != '\t') return null
        var spacesAfter = 0
        var j = afterBullet
        while (j < chars.size && (chars[j] == ' ' || chars[j] == '\t')) { spacesAfter++; j++ }
        val effectiveSpaces = if (spacesAfter > 4) 1 else spacesAfter
        val contentStart = if (spacesAfter > 4) afterBullet + 1 else j
        return Marker(MarkerKind.BULLET, bc, 0, ' ',
            W = leading + 1 + effectiveSpaces, contentStartIdx = contentStart)
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
    var spacesAfter = 0
    var j = afterDelim
    while (j < chars.size && (chars[j] == ' ' || chars[j] == '\t')) { spacesAfter++; j++ }
    val effectiveSpaces = if (spacesAfter > 4) 1 else spacesAfter
    val contentStart = if (spacesAfter > 4) afterDelim + 1 else j
    return Marker(MarkerKind.ORDERED, ' ', number, delim,
        W = leading + markerLen + effectiveSpaces, contentStartIdx = contentStart)
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
private fun collectItemLines(
    chars: List<Char>,
    firstContent: String,
    afterFirstLine: Int,
    W: Int,
): CollectedLines {
    val lines = mutableListOf<String>()
    if (firstContent.isNotEmpty()) lines.add(firstContent)

    var idx = afterFirstLine
    var commitIdx = afterFirstLine
    var pendingBlanks = 0
    var hadBlank = false

    while (idx < chars.size) {
        val (lineContent, nextIdx) = readRawLineLi(chars, idx)
        when {
            isBlankLi(lineContent) -> {
                pendingBlanks++
                idx = nextIdx
            }
            countLeadingSpacesLi(lineContent) >= W -> {
                if (pendingBlanks > 0) {
                    hadBlank = true
                    repeat(pendingBlanks) { lines.add("") }
                    pendingBlanks = 0
                }
                lines.add(stripLeadingSpacesLi(lineContent, W))
                idx = nextIdx
                commitIdx = nextIdx
            }
            else -> break
        }
    }

    return CollectedLines(lines, commitIdx, hadBlank)
}

// ---------------------------------------------------------------------------
// Internal parse-result type
// ---------------------------------------------------------------------------

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
    val (firstContent, afterFirstLine) = readRawLineLi(chars, marker.contentStartIdx)
    val collected = collectItemLines(chars, firstContent, afterFirstLine, marker.W)

    val innerText = collected.lines.joinToString("\n") + "\n"
    val innerChars = innerText.toList()
    val innerInput = ParserInput(innerChars, 0, input.userContext)
    val pBlock = blockFactory()
    val blocksResult = pMany(pBlock)(innerInput) as Success
    val blocks: List<Block> = blocksResult.value

    return ItemResult(marker, Block.ListItem(blocks), collected.hadInternalBlank, collected.nextIdx)
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
