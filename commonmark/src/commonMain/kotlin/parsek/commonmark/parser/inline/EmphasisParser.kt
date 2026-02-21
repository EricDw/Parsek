package parsek.commonmark.parser.inline

import parsek.commonmark.ast.Inline

// ---------------------------------------------------------------------------
// Character classification helpers
// ---------------------------------------------------------------------------

/**
 * Returns `true` if [ch] is a Unicode whitespace character as defined by the
 * CommonMark spec: Unicode category Zs (space separators), plus tab, line
 * feed, form feed, and carriage return.
 */
internal fun isUnicodeWhitespace(ch: Char): Boolean =
    ch == '\t' || ch == '\n' || ch == '\u000C' || ch == '\r' ||
        ch.category == CharCategory.SPACE_SEPARATOR

/**
 * Returns `true` if [ch] is a Unicode punctuation character as defined by the
 * CommonMark spec: ASCII punctuation characters, plus Unicode general
 * categories P (punctuation) and S (symbol).
 */
internal fun isUnicodePunctuation(ch: Char): Boolean =
    ch in "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~" ||
        ch.category.let { cat ->
            cat == CharCategory.CONNECTOR_PUNCTUATION ||
                cat == CharCategory.DASH_PUNCTUATION ||
                cat == CharCategory.START_PUNCTUATION ||
                cat == CharCategory.END_PUNCTUATION ||
                cat == CharCategory.INITIAL_QUOTE_PUNCTUATION ||
                cat == CharCategory.FINAL_QUOTE_PUNCTUATION ||
                cat == CharCategory.OTHER_PUNCTUATION ||
                cat == CharCategory.MATH_SYMBOL ||
                cat == CharCategory.CURRENCY_SYMBOL ||
                cat == CharCategory.MODIFIER_SYMBOL ||
                cat == CharCategory.OTHER_SYMBOL
        }

// ---------------------------------------------------------------------------
// EmphasisToken — public input type for processEmphasis
// ---------------------------------------------------------------------------

/**
 * A token in the emphasis processing pipeline.
 *
 * The inline parser (PR 5.8) produces a flat list of these tokens. Runs of
 * `*` or `_` characters become [DelimiterRun] entries; all other inline
 * elements become [Content] entries.
 *
 * After tokenisation, [processEmphasis] matches delimiter runs and produces
 * a properly nested `List<Inline>` with [Inline.Emphasis] and
 * [Inline.StrongEmphasis] wrappers.
 */
sealed interface EmphasisToken {

    /** A non-delimiter inline element. */
    data class Content(val inline: Inline) : EmphasisToken

    /**
     * A run of emphasis delimiter characters (`*` or `_`).
     *
     * @property char the delimiter character.
     * @property length the number of delimiter characters in the run.
     * @property canOpen `true` if this run can open emphasis.
     * @property canClose `true` if this run can close emphasis.
     */
    data class DelimiterRun(
        val char: Char,
        val length: Int,
        val canOpen: Boolean,
        val canClose: Boolean,
    ) : EmphasisToken
}

// ---------------------------------------------------------------------------
// classifyDelimiterRun — flanking detection
// ---------------------------------------------------------------------------

/**
 * Determines whether a delimiter run is left-flanking, right-flanking, or
 * both, and returns the `(canOpen, canClose)` pair for the given
 * [delimChar] (`*` or `_`).
 *
 * Flanking rules (CommonMark §6.2):
 * - **Left-flanking**: not followed by Unicode whitespace, AND (not followed
 *   by Unicode punctuation OR preceded by Unicode whitespace or punctuation).
 * - **Right-flanking**: not preceded by Unicode whitespace, AND (not preceded
 *   by Unicode punctuation OR followed by Unicode whitespace or punctuation).
 *
 * For `*`: canOpen = left-flanking; canClose = right-flanking.
 *
 * For `_`: canOpen = left-flanking AND (NOT right-flanking OR preceded by
 * punctuation); canClose = right-flanking AND (NOT left-flanking OR followed
 * by punctuation).
 *
 * @param charBefore the character immediately before the delimiter run, or
 *   `null` if at the start of input (treated as whitespace).
 * @param charAfter the character immediately after the delimiter run, or
 *   `null` if at the end of input (treated as whitespace).
 * @param delimChar the delimiter character (`*` or `_`).
 * @return `(canOpen, canClose)`.
 */
fun classifyDelimiterRun(
    charBefore: Char?,
    charAfter: Char?,
    delimChar: Char,
): Pair<Boolean, Boolean> {
    // Start/end of input are treated as if preceded/followed by a newline.
    val before = charBefore ?: '\n'
    val after = charAfter ?: '\n'

    val afterIsWs = isUnicodeWhitespace(after)
    val afterIsPunct = isUnicodePunctuation(after)
    val beforeIsWs = isUnicodeWhitespace(before)
    val beforeIsPunct = isUnicodePunctuation(before)

    val leftFlanking = !afterIsWs && (!afterIsPunct || beforeIsWs || beforeIsPunct)
    val rightFlanking = !beforeIsWs && (!beforeIsPunct || afterIsWs || afterIsPunct)

    return when (delimChar) {
        '*' -> Pair(leftFlanking, rightFlanking)
        '_' -> {
            val canOpen = leftFlanking && (!rightFlanking || beforeIsPunct)
            val canClose = rightFlanking && (!leftFlanking || afterIsPunct)
            Pair(canOpen, canClose)
        }
        else -> Pair(leftFlanking, rightFlanking)
    }
}

// ---------------------------------------------------------------------------
// Private delimiter entry used during processing
// ---------------------------------------------------------------------------

/**
 * A mutable delimiter entry used internally by [processEmphasis].
 *
 * Uses reference identity for `equals`/`indexOf` lookups (regular class,
 * not a data class).
 */
private class DelimInfo(
    val char: Char,
    var remaining: Int,
    val originalLength: Int,
    val canOpen: Boolean,
    val canClose: Boolean,
)

/**
 * Checks the "sum of 3" rule (CommonMark §6.2, appendix):
 *
 * If one of the delimiters can both open and close emphasis, then the sum
 * of the original lengths of the delimiter runs must not be a multiple of 3
 * unless both lengths are multiples of 3.
 */
private fun canMatch(opener: DelimInfo, closer: DelimInfo): Boolean {
    if ((opener.canOpen && opener.canClose) || (closer.canOpen && closer.canClose)) {
        if ((opener.originalLength + closer.originalLength) % 3 == 0) {
            if (opener.originalLength % 3 != 0 || closer.originalLength % 3 != 0) {
                return false
            }
        }
    }
    return true
}

// ---------------------------------------------------------------------------
// processEmphasis — the main matching algorithm
// ---------------------------------------------------------------------------

/**
 * Processes a flat list of emphasis tokens, matching delimiter runs according
 * to the CommonMark emphasis algorithm (§6.2, rules 1–17).
 *
 * The algorithm walks closer candidates left-to-right. For each closer, it
 * searches backward for a matching opener (same delimiter character,
 * `canOpen = true`, satisfies the "sum of 3" rule). When a match is found:
 *
 * 1. The content between opener and closer is wrapped in [Inline.Emphasis]
 *    (1 delimiter used) or [Inline.StrongEmphasis] (2 delimiters used).
 * 2. Unmatched delimiter runs between the matched pair become literal text.
 * 3. The opener and closer counts are reduced; fully consumed delimiters are
 *    removed.
 *
 * Unmatched delimiter runs remaining after the algorithm completes are
 * converted to literal [Inline.Text] nodes.
 *
 * @param tokens the flat token list produced by the inline parser.
 * @return the properly nested list of [Inline] nodes.
 */
fun processEmphasis(tokens: List<EmphasisToken>): List<Inline> {
    if (tokens.isEmpty()) return emptyList()

    // Convert public tokens to internal mutable representation.
    // `nodes` holds a mix of `Inline` and `DelimInfo` objects.
    val nodes = mutableListOf<Any>()
    for (token in tokens) {
        when (token) {
            is EmphasisToken.Content -> nodes.add(token.inline)
            is EmphasisToken.DelimiterRun -> nodes.add(
                DelimInfo(token.char, token.length, token.length, token.canOpen, token.canClose),
            )
        }
    }

    // Build the delimiter stack: references to all DelimInfo objects,
    // preserving their order in `nodes`.
    val delimStack = nodes.filterIsInstance<DelimInfo>().toMutableList()

    // Walk closer candidates left-to-right.
    var ci = 0
    while (ci < delimStack.size) {
        val closer = delimStack[ci]
        if (!closer.canClose || closer.remaining <= 0) {
            ci++
            continue
        }

        // Search backward in the delimiter stack for a matching opener.
        var oi = ci - 1
        var matched = false
        while (oi >= 0) {
            val opener = delimStack[oi]
            if (opener.char == closer.char &&
                opener.canOpen &&
                opener.remaining > 0 &&
                canMatch(opener, closer)
            ) {
                matched = true
                break
            }
            oi--
        }

        if (!matched) {
            // No opener found. If this closer can also open, keep it in the
            // stack (it might act as an opener for a later closer). Otherwise
            // remove it — it will never match.
            if (!closer.canOpen) {
                delimStack.removeAt(ci)
                // Don't increment ci; the list shifted.
            } else {
                ci++
            }
            continue
        }

        // --- Match found ---

        val opener = delimStack[oi]
        val useCount = if (opener.remaining >= 2 && closer.remaining >= 2) 2 else 1

        opener.remaining -= useCount
        closer.remaining -= useCount

        // Locate opener and closer in the nodes list (by reference identity).
        val openerIdx = nodes.indexOf(opener as Any)
        val closerIdx = nodes.indexOf(closer as Any)

        // Extract inner content: everything between opener and closer.
        val innerInlines = mutableListOf<Inline>()
        for (i in openerIdx + 1 until closerIdx) {
            when (val node = nodes[i]) {
                is Inline -> innerInlines.add(node)
                is DelimInfo -> {
                    if (node.remaining > 0) {
                        innerInlines.add(
                            Inline.Text(node.char.toString().repeat(node.remaining)),
                        )
                        node.remaining = 0
                    }
                }
            }
        }

        // Wrap in Emphasis or StrongEmphasis.
        val emphInline: Inline = if (useCount == 2) {
            Inline.StrongEmphasis(innerInlines)
        } else {
            Inline.Emphasis(innerInlines)
        }

        // Replace nodes between opener and closer with the emphasis node.
        val removeStart = openerIdx + 1
        val removeEnd = closerIdx
        if (removeEnd > removeStart) {
            nodes.subList(removeStart, removeEnd).clear()
        }
        nodes.add(removeStart, emphInline)

        // Remove inner delimiters from the stack (between oi and ci).
        for (si in (oi + 1 until ci).reversed()) {
            delimStack.removeAt(si)
        }
        // After removing (ci - oi - 1) inner entries, closer is now at oi + 1.
        ci = oi + 1

        // If opener is exhausted, remove from stack and nodes.
        if (opener.remaining <= 0) {
            delimStack.remove(opener)
            nodes.remove(opener as Any)
            ci-- // opener was before ci; adjust.
        }

        // If closer is exhausted, remove from stack and nodes.
        if (closer.remaining <= 0) {
            delimStack.remove(closer)
            nodes.remove(closer as Any)
            // ci now points to the next element — correct without adjustment.
        }
        // If closer still has remaining chars, don't advance ci — it might
        // match again with another opener (e.g. `***foo***`).
    }

    // Convert remaining nodes to Inline values.
    return nodes.mapNotNull { node ->
        when (node) {
            is Inline -> node
            is DelimInfo -> {
                if (node.remaining > 0)
                    Inline.Text(node.char.toString().repeat(node.remaining))
                else
                    null
            }
            else -> null
        }
    }
}
