package parsek.commonmark.parser.block

/**
 * Tab expansion utilities for CommonMark §2.2.
 *
 * Tabs expand to the next tab stop (every 4 columns, 0-indexed).
 */

/**
 * Counts the virtual columns of leading whitespace (spaces and tabs) in [chars]
 * starting at [startIdx]. Tabs expand to the next multiple of 4 from [startCol].
 *
 * @return a pair of (virtualColumns, indexAfterWhitespace).
 *   virtualColumns is the total virtual column width (relative to column 0, not startCol).
 */
internal fun countVirtualIndent(chars: List<Char>, startIdx: Int, startCol: Int = 0): Pair<Int, Int> {
    var col = startCol
    var i = startIdx
    while (i < chars.size) {
        when (chars[i]) {
            ' ' -> { col++; i++ }
            '\t' -> { col = (col / 4 + 1) * 4; i++ }
            else -> break
        }
    }
    return Pair(col - startCol, i)
}

/**
 * Consumes exactly [n] virtual columns of whitespace from [chars] starting at [startIdx],
 * where the absolute column position is [startCol]. Tabs expand to the next multiple of 4
 * based on the absolute column.
 *
 * If a tab straddles the boundary (i.e. the tab expands past [n] columns), the remainder
 * is returned.
 *
 * @return a pair of (indexAfterConsumed, remainderSpaces). The remainder represents
 *   virtual spaces from a partially-consumed tab that should be prepended to content.
 */
internal fun consumeVirtualColumns(
    chars: List<Char>,
    startIdx: Int,
    n: Int,
    startCol: Int = 0,
): Pair<Int, Int> {
    var col = startCol
    val target = startCol + n
    var i = startIdx
    while (i < chars.size && col < target) {
        when (chars[i]) {
            ' ' -> { col++; i++ }
            '\t' -> {
                val nextStop = (col / 4 + 1) * 4
                if (nextStop <= target) {
                    col = nextStop
                    i++
                } else {
                    // Tab straddles the boundary: consume it, remainder goes to content.
                    val remainder = nextStop - target
                    return Pair(i + 1, remainder)
                }
            }
            else -> break
        }
    }
    return Pair(i, 0)
}

/**
 * Counts the virtual columns of leading whitespace in a string.
 */
internal fun countVirtualIndentStr(s: String): Int {
    var col = 0
    for (ch in s) {
        when (ch) {
            ' ' -> col++
            '\t' -> col = (col / 4 + 1) * 4
            else -> break
        }
    }
    return col
}

/**
 * Consumes [n] virtual columns of leading whitespace from a string.
 *
 * @return a pair of (charsConsumed, remainderSpaces).
 */
internal fun consumeVirtualColumnsStr(s: String, n: Int): Pair<Int, Int> {
    var col = 0
    var i = 0
    while (i < s.length && col < n) {
        when (s[i]) {
            ' ' -> { col++; i++ }
            '\t' -> {
                val nextStop = (col / 4 + 1) * 4
                if (nextStop <= n) {
                    col = nextStop
                    i++
                } else {
                    return Pair(i + 1, nextStop - n)
                }
            }
            else -> break
        }
    }
    return Pair(i, 0)
}
