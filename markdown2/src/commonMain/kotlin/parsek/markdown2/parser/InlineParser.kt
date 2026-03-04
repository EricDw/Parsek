package parsek.markdown2.parser

import parsek.markdown.ast.Inline

/**
 * Resolves a link reference label to (destination, title?) or null.
 */
typealias LinkRefResolver = (label: String) -> Pair<String, String?>?

/**
 * Parses inline markdown content from raw text into a list of [Inline] nodes.
 *
 * Handles:
 * - Backslash escapes
 * - Code spans
 * - Emphasis and strong emphasis (`*` and `_`)
 * - Hard breaks (two spaces + newline, or `\` + newline)
 * - Soft breaks (newline)
 * - Links and images (inline, reference, collapsed, shortcut)
 * - Autolinks and raw HTML
 * - HTML entities
 * - Plain text
 *
 * The algorithm works in two phases:
 * 1. Tokenize the input into a flat list of inline tokens (content + delimiter runs)
 * 2. Apply the emphasis matching algorithm (CommonMark §6.2) to produce nested inlines
 */
fun parseInlines(text: String, resolveRef: LinkRefResolver? = null): List<Inline> {
    if (text.isEmpty()) return emptyList()
    val tokens = tokenizeInlines(text, resolveRef).toMutableList()
    // Strip trailing whitespace from last text token
    trimTrailingSpaces(tokens)
    return processEmphasis(tokens)
}

// ── ASCII punctuation for backslash escapes ────────────────────────────

private val ASCII_PUNCTUATION = setOf(
    '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.',
    '/', ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '^', '_', '`',
    '{', '|', '}', '~',
)

// ── Tokenization (Phase 1) ─────────────────────────────────────────────

/**
 * A token in the inline processing pipeline.
 */
private sealed interface InlineToken {
    data class Content(val inline: Inline) : InlineToken
    data class DelimiterRun(
        val char: Char,
        val length: Int,
        val canOpen: Boolean,
        val canClose: Boolean,
    ) : InlineToken
}

/**
 * Tokenizes inline text into a flat list of [InlineToken]s.
 */
private fun tokenizeInlines(text: String, resolveRef: LinkRefResolver? = null): List<InlineToken> {
    val tokens = mutableListOf<InlineToken>()
    var i = 0

    while (i < text.length) {
        val ch = text[i]

        when {
            // Backslash escape
            ch == '\\' && i + 1 < text.length -> {
                val next = text[i + 1]
                if (next == '\n') {
                    // Hard break: backslash + newline
                    tokens.add(InlineToken.Content(Inline.HardBreak))
                    i += 2
                } else if (next in ASCII_PUNCTUATION) {
                    tokens.add(InlineToken.Content(Inline.Text(next.toString())))
                    i += 2
                } else {
                    tokens.add(InlineToken.Content(Inline.Text("\\")))
                    i++
                }
            }

            // Code span
            ch == '`' -> {
                val result = parseCodeSpan(text, i)
                tokens.add(InlineToken.Content(result.first))
                i = result.second
            }

            // HTML entity / numeric character reference
            ch == '&' -> {
                val entity = tryParseEntity(text, i)
                if (entity != null) {
                    tokens.add(InlineToken.Content(entity.first))
                    i = entity.second
                } else {
                    tokens.add(InlineToken.Content(Inline.Text("&")))
                    i++
                }
            }

            // Autolink / Raw HTML
            ch == '<' -> {
                val autolink = tryParseAutolink(text, i)
                if (autolink != null) {
                    tokens.add(InlineToken.Content(autolink.first))
                    i = autolink.second
                } else {
                    val rawHtml = tryParseRawHtml(text, i)
                    if (rawHtml != null) {
                        tokens.add(InlineToken.Content(Inline.RawHtml(rawHtml.first)))
                        i = rawHtml.second
                    } else {
                        tokens.add(InlineToken.Content(Inline.Text("<")))
                        i++
                    }
                }
            }

            // Emphasis delimiter runs: * and _
            ch == '*' || ch == '_' -> {
                val runStart = i
                while (i < text.length && text[i] == ch) i++
                val length = i - runStart

                val charBefore = if (runStart > 0) text[runStart - 1] else null
                val charAfter = if (i < text.length) text[i] else null
                val (canOpen, canClose) = classifyDelimiterRun(charBefore, charAfter, ch)

                tokens.add(InlineToken.DelimiterRun(ch, length, canOpen, canClose))
            }

            // Line ending → hard or soft break
            ch == '\n' -> {
                // Check for hard break: 2+ trailing spaces before newline
                val hasHardBreak = hasTrailingSpaces(tokens)
                if (hasHardBreak) {
                    // Remove trailing spaces from last text token
                    trimTrailingSpaces(tokens)
                    tokens.add(InlineToken.Content(Inline.HardBreak))
                } else {
                    // Remove single trailing space if present
                    trimTrailingSpaces(tokens)
                    tokens.add(InlineToken.Content(Inline.SoftBreak))
                }
                i++
                // Skip leading spaces on next line
                while (i < text.length && text[i] == ' ') i++
            }

            // Image: ![
            ch == '!' && i + 1 < text.length && text[i + 1] == '[' -> {
                val result = tryParseImage(text, i, resolveRef)
                if (result != null) {
                    tokens.add(InlineToken.Content(result.first))
                    i = result.second
                } else {
                    tokens.add(InlineToken.Content(Inline.Text("!")))
                    i++
                }
            }

            // Link: [
            ch == '[' -> {
                val result = tryParseLink(text, i, resolveRef)
                if (result != null) {
                    tokens.add(InlineToken.Content(result.first))
                    i = result.second
                } else {
                    tokens.add(InlineToken.Content(Inline.Text("[")))
                    i++
                }
            }

            // Plain text — batch safe characters
            else -> {
                val start = i
                while (i < text.length) {
                    val c = text[i]
                    if (c == '\\' || c == '`' || c == '*' || c == '_' || c == '\n' ||
                        c == '&' || c == '<' || c == '[' || c == '!') break
                    i++
                }
                if (i > start) {
                    tokens.add(InlineToken.Content(Inline.Text(text.substring(start, i))))
                } else {
                    // Single character fallback
                    tokens.add(InlineToken.Content(Inline.Text(text[i].toString())))
                    i++
                }
            }
        }
    }

    return tokens
}

/**
 * Checks if the last content token ends with 2+ spaces (for hard break detection).
 */
private fun hasTrailingSpaces(tokens: List<InlineToken>): Boolean {
    val last = tokens.lastOrNull() ?: return false
    if (last is InlineToken.Content && last.inline is Inline.Text) {
        val text = last.inline.literal
        return text.length >= 2 && text.endsWith("  ")
                || text.length >= 1 && text.endsWith(" ") && trailingSpaceCount(tokens) >= 2
    }
    return false
}

/**
 * Counts trailing spaces across the last text token.
 */
private fun trailingSpaceCount(tokens: List<InlineToken>): Int {
    val last = tokens.lastOrNull() ?: return 0
    if (last is InlineToken.Content && last.inline is Inline.Text) {
        val text = last.inline.literal
        var count = 0
        for (j in text.length - 1 downTo 0) {
            if (text[j] == ' ') count++ else break
        }
        return count
    }
    return 0
}

/**
 * Trims trailing spaces and tabs from the last text token.
 */
private fun trimTrailingSpaces(tokens: MutableList<InlineToken>) {
    if (tokens.isEmpty()) return
    val last = tokens.last()
    if (last is InlineToken.Content && last.inline is Inline.Text) {
        val text = last.inline.literal
        val trimmed = text.trimEnd(' ', '\t')
        if (trimmed.isEmpty()) {
            tokens.removeAt(tokens.size - 1)
        } else if (trimmed != text) {
            tokens[tokens.size - 1] = InlineToken.Content(Inline.Text(trimmed))
        }
    }
}

// ── Code span parsing ──────────────────────────────────────────────────

/**
 * Parses a code span starting at position [start].
 * Returns (Inline, nextIndex).
 */
private fun parseCodeSpan(text: String, start: Int): Pair<Inline, Int> {
    var i = start
    // Count opening backtick run
    while (i < text.length && text[i] == '`') i++
    val n = i - start

    // Scan for a closing run of exactly n backticks
    val sb = StringBuilder()
    while (i < text.length) {
        if (text[i] == '`') {
            val tickStart = i
            while (i < text.length && text[i] == '`') i++
            val tickCount = i - tickStart
            if (tickCount == n) {
                val content = normaliseCodeSpan(sb.toString())
                return Inline.CodeSpan(content) to i
            } else {
                repeat(tickCount) { sb.append('`') }
            }
        } else {
            sb.append(text[i])
            i++
        }
    }

    // No matching closing run — emit opening backticks as literal text
    return Inline.Text("`".repeat(n)) to (start + n)
}

/**
 * Normalizes code span content per CommonMark §6.1:
 * 1. Line endings → single space
 * 2. Strip one leading/trailing space if both ends are spaces and content isn't all spaces
 */
private fun normaliseCodeSpan(raw: String): String {
    val spaced = buildString {
        var j = 0
        while (j < raw.length) {
            when {
                raw[j] == '\r' && j + 1 < raw.length && raw[j + 1] == '\n' -> { append(' '); j += 2 }
                raw[j] == '\r' || raw[j] == '\n' -> { append(' '); j++ }
                else -> { append(raw[j]); j++ }
            }
        }
    }
    return if (spaced.length >= 2 &&
        spaced.first() == ' ' &&
        spaced.last() == ' ' &&
        spaced.any { it != ' ' }
    ) spaced.substring(1, spaced.length - 1) else spaced
}

// ── Flanking detection ─────────────────────────────────────────────────

private fun isUnicodeWhitespace(ch: Char): Boolean =
    ch == '\t' || ch == '\n' || ch == '\u000C' || ch == '\r' ||
        ch.category == CharCategory.SPACE_SEPARATOR

private fun isUnicodePunctuation(ch: Char): Boolean =
    ch in ASCII_PUNCTUATION ||
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

private fun classifyDelimiterRun(
    charBefore: Char?,
    charAfter: Char?,
    delimChar: Char,
): Pair<Boolean, Boolean> {
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

// ── Emphasis processing (Phase 2) ──────────────────────────────────────

/**
 * Mutable delimiter entry used during emphasis processing.
 * Uses reference identity (not data class) for correct indexOf lookups.
 */
private class DelimInfo(
    val char: Char,
    var remaining: Int,
    val originalLength: Int,
    val canOpen: Boolean,
    val canClose: Boolean,
)

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

/**
 * Processes emphasis tokens using the CommonMark §6.2 algorithm.
 */
private fun processEmphasis(tokens: List<InlineToken>): List<Inline> {
    if (tokens.isEmpty()) return emptyList()

    val nodes = mutableListOf<Any>()
    for (token in tokens) {
        when (token) {
            is InlineToken.Content -> nodes.add(token.inline)
            is InlineToken.DelimiterRun -> nodes.add(
                DelimInfo(token.char, token.length, token.length, token.canOpen, token.canClose),
            )
        }
    }

    val delimStack = nodes.filterIsInstance<DelimInfo>().toMutableList()

    var ci = 0
    while (ci < delimStack.size) {
        val closer = delimStack[ci]
        if (!closer.canClose || closer.remaining <= 0) {
            ci++
            continue
        }

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
            if (!closer.canOpen) {
                delimStack.removeAt(ci)
            } else {
                ci++
            }
            continue
        }

        val opener = delimStack[oi]
        val useCount = if (opener.remaining >= 2 && closer.remaining >= 2) 2 else 1

        opener.remaining -= useCount
        closer.remaining -= useCount

        val openerIdx = nodes.indexOf(opener as Any)
        val closerIdx = nodes.indexOf(closer as Any)

        val innerInlines = mutableListOf<Inline>()
        for (i in openerIdx + 1 until closerIdx) {
            when (val node = nodes[i]) {
                is Inline -> innerInlines.add(node)
                is DelimInfo -> {
                    if (node.remaining > 0) {
                        innerInlines.add(Inline.Text(node.char.toString().repeat(node.remaining)))
                        node.remaining = 0
                    }
                }
            }
        }

        val emphInline: Inline = when {
            useCount == 2 -> Inline.StrongEmphasis(innerInlines)
            else -> Inline.Emphasis(innerInlines)
        }

        val removeStart = openerIdx + 1
        val removeEnd = closerIdx
        if (removeEnd > removeStart) {
            nodes.subList(removeStart, removeEnd).clear()
        }
        nodes.add(removeStart, emphInline)

        for (si in (oi + 1 until ci).reversed()) {
            delimStack.removeAt(si)
        }
        ci = oi + 1

        if (opener.remaining <= 0) {
            delimStack.remove(opener)
            nodes.remove(opener as Any)
            ci--
        }

        if (closer.remaining <= 0) {
            delimStack.remove(closer)
            nodes.remove(closer as Any)
        }
    }

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

// ── Link and image parsing ──────────────────────────────────────────────

/**
 * Tries to parse a link starting at `[` at position [start].
 * Returns (Inline.Link, nextIndex) or null.
 */
private fun tryParseLink(text: String, start: Int, resolveRef: LinkRefResolver?): Pair<Inline, Int>? {
    if (start >= text.length || text[start] != '[') return null

    val closeIdx = findClosingBracket(text, start + 1)
    if (closeIdx == null) return null

    val linkText = text.substring(start + 1, closeIdx)
    val afterClose = closeIdx + 1

    // Try inline link: [text](dest "title")
    val inlineLink = tryInlineLinkSuffix(text, afterClose)
    if (inlineLink != null) {
        val children = parseInlines(linkText, resolveRef)
        // Links cannot contain links
        if (containsLink(children)) return null
        return Inline.Link(inlineLink.first, inlineLink.second, children) to inlineLink.third
    }

    // Try full reference: [text][label]
    if (afterClose < text.length && text[afterClose] == '[') {
        val refLabel = parseLinkLabel(text, afterClose)
        if (refLabel != null) {
            val normalized = normalizeLinkLabel(refLabel.first)
            if (normalized.isNotBlank() && resolveRef != null) {
                val ref = resolveRef(normalized)
                if (ref != null) {
                    val children = parseInlines(linkText, resolveRef)
                    if (containsLink(children)) return null
                    return Inline.Link(ref.first, ref.second, children) to refLabel.second
                }
            }
        }

        // Try collapsed reference: [text][]
        if (afterClose + 1 < text.length && text[afterClose] == '[' && text[afterClose + 1] == ']') {
            val normalized = normalizeLinkLabel(linkText)
            if (normalized.isNotBlank() && resolveRef != null) {
                val ref = resolveRef(normalized)
                if (ref != null) {
                    val children = parseInlines(linkText, resolveRef)
                    if (containsLink(children)) return null
                    return Inline.Link(ref.first, ref.second, children) to (afterClose + 2)
                }
            }
        }

        // No shortcut allowed when [ follows ]
        return null
    }

    // Try shortcut reference: [text]
    val normalized = normalizeLinkLabel(linkText)
    if (normalized.isNotBlank() && resolveRef != null) {
        val ref = resolveRef(normalized)
        if (ref != null) {
            val children = parseInlines(linkText, resolveRef)
            if (containsLink(children)) return null
            return Inline.Link(ref.first, ref.second, children) to afterClose
        }
    }

    return null
}

/**
 * Tries to parse an image starting at `![` at position [start].
 * Returns (Inline.Image, nextIndex) or null.
 */
private fun tryParseImage(text: String, start: Int, resolveRef: LinkRefResolver?): Pair<Inline, Int>? {
    if (start + 1 >= text.length || text[start] != '!' || text[start + 1] != '[') return null

    val closeIdx = findClosingBracket(text, start + 2)
    if (closeIdx == null) return null

    val altText = text.substring(start + 2, closeIdx)
    val afterClose = closeIdx + 1

    // Try inline image: ![alt](dest "title")
    val inlineLink = tryInlineLinkSuffix(text, afterClose)
    if (inlineLink != null) {
        val children = parseInlines(altText, resolveRef)
        return Inline.Image(inlineLink.first, inlineLink.second, altText, children) to inlineLink.third
    }

    // Try full reference: ![alt][label]
    if (afterClose < text.length && text[afterClose] == '[') {
        val refLabel = parseLinkLabel(text, afterClose)
        if (refLabel != null) {
            val normalized = normalizeLinkLabel(refLabel.first)
            if (normalized.isNotBlank() && resolveRef != null) {
                val ref = resolveRef(normalized)
                if (ref != null) {
                    val children = parseInlines(altText, resolveRef)
                    return Inline.Image(ref.first, ref.second, altText, children) to refLabel.second
                }
            }
        }

        // Try collapsed: ![alt][]
        if (afterClose + 1 < text.length && text[afterClose] == '[' && text[afterClose + 1] == ']') {
            val normalized = normalizeLinkLabel(altText)
            if (normalized.isNotBlank() && resolveRef != null) {
                val ref = resolveRef(normalized)
                if (ref != null) {
                    val children = parseInlines(altText, resolveRef)
                    return Inline.Image(ref.first, ref.second, altText, children) to (afterClose + 2)
                }
            }
        }

        return null
    }

    // Shortcut: ![alt]
    val normalized = normalizeLinkLabel(altText)
    if (normalized.isNotBlank() && resolveRef != null) {
        val ref = resolveRef(normalized)
        if (ref != null) {
            val children = parseInlines(altText, resolveRef)
            return Inline.Image(ref.first, ref.second, altText, children) to afterClose
        }
    }

    return null
}

/**
 * Finds the closing `]` for a bracket pair, handling nesting, escapes,
 * code spans, and angle brackets.
 * [start] is the position after the opening `[`.
 * Returns the index of the closing `]` or null.
 */
private fun findClosingBracket(text: String, start: Int): Int? {
    var i = start
    var depth = 1

    while (i < text.length) {
        when (text[i]) {
            '[' -> { depth++; i++ }
            ']' -> {
                depth--
                if (depth == 0) return i
                i++
            }
            '\\' -> {
                i += 2 // skip escaped char
            }
            '`' -> {
                // Skip code span
                val tickStart = i
                while (i < text.length && text[i] == '`') i++
                val tickLen = i - tickStart
                // Find matching closing backtick run
                var found = false
                while (i < text.length) {
                    if (text[i] == '`') {
                        val closeStart = i
                        while (i < text.length && text[i] == '`') i++
                        if (i - closeStart == tickLen) { found = true; break }
                    } else {
                        i++
                    }
                }
                if (!found) {
                    // No matching close — backtrack past opening backticks as literal
                    i = tickStart + tickLen
                }
            }
            '<' -> {
                // Skip angle-bracket content
                val end = skipAngleBracket(text, i)
                i = end
            }
            else -> i++
        }
    }
    return null
}

/**
 * Skips past an angle-bracket construct (autolink or raw HTML).
 * Returns the index after the closing `>`, or start+1 if no match.
 */
private fun skipAngleBracket(text: String, start: Int): Int {
    var i = start + 1
    while (i < text.length) {
        when (text[i]) {
            '>' -> return i + 1
            '<' -> return start + 1 // nested < not allowed
            '\n' -> return start + 1 // line break not allowed in autolink
            '\\' -> i += 2
            else -> i++
        }
    }
    return start + 1
}

/**
 * Tries to parse the inline link suffix `(dest "title")` starting at [start].
 * Returns (destination, title, nextIndex) or null.
 */
private fun tryInlineLinkSuffix(text: String, start: Int): Triple<String, String?, Int>? {
    var i = start
    if (i >= text.length || text[i] != '(') return null
    i++

    // Skip whitespace
    i = skipLinkWhitespace(text, i)

    // Empty parens: ()
    if (i < text.length && text[i] == ')') {
        return Triple("", null, i + 1)
    }

    // Parse destination
    val dest = parseLinkDestination(text, i) ?: return null
    i = dest.second

    // Check for title
    val wsStart = i
    i = skipLinkWhitespace(text, i)
    val hadWhitespace = i > wsStart

    var title: String? = null
    if (hadWhitespace && i < text.length && text[i] in "\"'(") {
        val titleResult = parseLinkTitle(text, i)
        if (titleResult != null) {
            title = titleResult.first
            i = titleResult.second
            i = skipLinkWhitespace(text, i)
        } else {
            // Title parse failed — backtrack to after destination
            i = wsStart
            i = skipLinkWhitespace(text, i)
        }
    }

    if (i >= text.length || text[i] != ')') return null
    return Triple(resolveEntities(dest.first), title?.let { resolveEntities(it) }, i + 1)
}

/**
 * Skips whitespace including at most one line ending.
 */
private fun skipLinkWhitespace(text: String, start: Int): Int {
    var i = start
    while (i < text.length && text[i] in " \t") i++
    if (i < text.length && text[i] == '\n') {
        i++
        while (i < text.length && text[i] in " \t") i++
    }
    return i
}

/**
 * Parses a link destination. Two forms:
 * - Angle bracket: `<content>`
 * - Bare: balanced parentheses, no spaces/controls
 */
private fun parseLinkDestination(text: String, start: Int): Pair<String, Int>? {
    if (start >= text.length) return null

    if (text[start] == '<') {
        // Angle bracket form
        var i = start + 1
        val sb = StringBuilder()
        while (i < text.length) {
            when (text[i]) {
                '>' -> return sb.toString() to (i + 1)
                '\n', '\r' -> return null
                '<' -> return null
                '\\' -> {
                    if (i + 1 < text.length && text[i + 1] in ASCII_PUNCTUATION) {
                        sb.append(text[i + 1])
                        i += 2
                    } else {
                        sb.append('\\')
                        i++
                    }
                }
                else -> { sb.append(text[i]); i++ }
            }
        }
        return null // no closing >
    } else {
        // Bare form
        var i = start
        var depth = 0
        val sb = StringBuilder()
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '\\' && i + 1 < text.length && text[i + 1] in ASCII_PUNCTUATION -> {
                    sb.append(text[i + 1])
                    i += 2
                }
                ch == '(' -> { depth++; sb.append(ch); i++ }
                ch == ')' -> {
                    if (depth == 0) break
                    depth--; sb.append(ch); i++
                }
                ch <= ' ' || ch.code == 0x7F -> break // space, controls
                else -> { sb.append(ch); i++ }
            }
        }
        if (i == start) return null // empty
        if (depth != 0) return null // unbalanced
        return sb.toString() to i
    }
}

/**
 * Parses a link title: "...", '...', or (...)
 */
private fun parseLinkTitle(text: String, start: Int): Pair<String, Int>? {
    if (start >= text.length) return null
    val openChar = text[start]
    val closeChar = when (openChar) {
        '"' -> '"'
        '\'' -> '\''
        '(' -> ')'
        else -> return null
    }

    var i = start + 1
    val sb = StringBuilder()
    while (i < text.length) {
        when {
            text[i] == closeChar -> return sb.toString() to (i + 1)
            text[i] == '(' && openChar == '(' -> return null // unescaped ( in () title
            text[i] == '\\' && i + 1 < text.length && text[i + 1] in ASCII_PUNCTUATION -> {
                sb.append(text[i + 1])
                i += 2
            }
            text[i] == '\n' -> {
                // Check for blank line
                val afterNewline = i + 1
                var j = afterNewline
                while (j < text.length && text[j] in " \t") j++
                if (j >= text.length || text[j] == '\n') return null // blank line
                sb.append('\n')
                i++
            }
            else -> { sb.append(text[i]); i++ }
        }
    }
    return null // no closing delimiter
}

/**
 * Parses a link label `[content]` at position [start].
 * Returns (rawContent, nextIndex) or null.
 */
private fun parseLinkLabel(text: String, start: Int): Pair<String, Int>? {
    if (start >= text.length || text[start] != '[') return null
    var i = start + 1
    val sb = StringBuilder()
    while (i < text.length && sb.length <= 999) {
        when {
            text[i] == ']' -> return sb.toString() to (i + 1)
            text[i] == '[' -> return null // no nesting
            text[i] == '\\' && i + 1 < text.length -> {
                sb.append(text[i])
                sb.append(text[i + 1])
                i += 2
            }
            else -> { sb.append(text[i]); i++ }
        }
    }
    return null
}

/**
 * Normalizes a link label: Unicode case fold + collapse whitespace + trim.
 */
internal fun normalizeLinkLabel(label: String): String {
    val caseFolded = unicodeCaseFold(label)
    val collapsed = caseFolded.replace(Regex("\\s+"), " ")
    return collapsed.trim()
}

/**
 * Performs Unicode case folding (full), which differs from simple lowercasing
 * for certain characters (e.g., ẞ → ss, ﬁ → fi).
 */
private fun unicodeCaseFold(s: String): String = buildString(s.length) {
    for (ch in s) {
        val folded = CASE_FOLD_SPECIAL[ch]
        if (folded != null) {
            append(folded)
        } else {
            append(ch.lowercaseChar())
        }
    }
}

/**
 * Characters where Unicode case folding differs from simple lowercasing.
 * Maps characters to their full case fold (which may be multi-character).
 */
private val CASE_FOLD_SPECIAL = mapOf(
    'ẞ' to "ss",         // LATIN CAPITAL LETTER SHARP S
    'µ' to "μ",          // MICRO SIGN → GREEK SMALL LETTER MU
    'ﬁ' to "fi",         // LATIN SMALL LIGATURE FI
    'ﬂ' to "fl",         // LATIN SMALL LIGATURE FL
    'ﬀ' to "ff",         // LATIN SMALL LIGATURE FF
    'ﬃ' to "ffi",        // LATIN SMALL LIGATURE FFI
    'ﬄ' to "ffl",        // LATIN SMALL LIGATURE FFL
    'ﬅ' to "st",         // LATIN SMALL LIGATURE LONG S T
    'ﬆ' to "st",         // LATIN SMALL LIGATURE ST
    'ſ' to "s",          // LATIN SMALL LETTER LONG S
)

/**
 * Checks if a list of inlines contains a Link (recursively).
 */
private fun containsLink(inlines: List<Inline>): Boolean {
    for (inline in inlines) {
        if (inline is Inline.Link) return true
        if (inline is Inline.Emphasis && containsLink(inline.children)) return true
        if (inline is Inline.StrongEmphasis && containsLink(inline.children)) return true
    }
    return false
}

// ── Link reference definition parsing ───────────────────────────────────

/**
 * A parsed link reference definition.
 */
data class LinkRefDef(
    val label: String,
    val destination: String,
    val title: String?,
)

/**
 * Extracts leading link reference definitions from paragraph text.
 * Returns the list of definitions found plus the remaining text.
 * CommonMark §4.7: `[label]: destination "title"`
 */
fun parseLinkRefDefs(text: String): Pair<List<LinkRefDef>, String> {
    val defs = mutableListOf<LinkRefDef>()
    var pos = 0

    while (pos < text.length) {
        val result = tryParseSingleLinkRefDef(text, pos) ?: break
        defs.add(result.first)
        pos = result.second
        // Skip optional blank line after def
        if (pos < text.length && text[pos] == '\n') {
            // Don't consume the newline yet — it may be needed
        }
    }

    val remaining = text.substring(pos)
    return defs to remaining
}

/**
 * Tries to parse a single link reference definition starting at [pos].
 * Returns (LinkRefDef, nextPos) or null.
 *
 * Format: `[label]: destination "title"\n`
 * - Up to 3 leading spaces
 * - [label] (up to 999 chars, no unescaped brackets)
 * - : followed by optional whitespace (including up to one line ending)
 * - destination
 * - optional title on same line or next line
 * - must end with a line ending or EOF
 */
private fun tryParseSingleLinkRefDef(text: String, start: Int): Pair<LinkRefDef, Int>? {
    var i = start

    // Up to 3 leading spaces
    var spaces = 0
    while (i < text.length && text[i] == ' ' && spaces < 3) { spaces++; i++ }

    // Parse label
    val label = parseLinkLabel(text, i) ?: return null
    val normalizedLabel = normalizeLinkLabel(label.first)
    if (normalizedLabel.isBlank()) return null
    i = label.second

    // Must have colon
    if (i >= text.length || text[i] != ':') return null
    i++

    // Optional whitespace (spaces/tabs, then optional one line ending, then spaces/tabs)
    while (i < text.length && text[i] in " \t") i++
    val hadNewlineBeforeDest = i < text.length && text[i] == '\n'
    if (hadNewlineBeforeDest) {
        i++
        while (i < text.length && text[i] in " \t") i++
    }

    // Parse destination
    val dest = parseLinkDestination(text, i) ?: return null
    i = dest.second

    // Try to parse title
    var title: String? = null
    val posAfterDest = i
    val wsBeforeTitle = i
    while (i < text.length && text[i] in " \t") i++
    val hadNewlineBeforeTitle = i < text.length && text[i] == '\n'

    if (hadNewlineBeforeTitle) {
        // Title on next line
        val nextLineStart = i + 1
        var j = nextLineStart
        while (j < text.length && text[j] in " \t") j++

        if (j < text.length && text[j] in "\"'(") {
            val titleResult = parseLinkTitle(text, j)
            if (titleResult != null) {
                // Check that nothing follows the title on this line except whitespace
                var afterTitle = titleResult.second
                while (afterTitle < text.length && text[afterTitle] in " \t") afterTitle++
                if (afterTitle >= text.length || text[afterTitle] == '\n') {
                    title = titleResult.first
                    i = afterTitle
                    if (i < text.length && text[i] == '\n') i++
                    return LinkRefDef(normalizedLabel, resolveEntities(dest.first), title?.let { resolveEntities(it) }) to i
                }
            }
        }
        // No valid title on next line — the def ends after dest (current line)
        // Check that dest line ends properly
        i = wsBeforeTitle
        while (i < text.length && text[i] in " \t") i++
        if (i < text.length && text[i] == '\n') {
            i++
            return LinkRefDef(normalizedLabel, resolveEntities(dest.first), null) to i
        }
        if (i >= text.length) {
            return LinkRefDef(normalizedLabel, resolveEntities(dest.first), null) to i
        }
        return null
    }

    // Title on same line
    if (i > wsBeforeTitle && i < text.length && text[i] in "\"'(") {
        val titleResult = parseLinkTitle(text, i)
        if (titleResult != null) {
            var afterTitle = titleResult.second
            while (afterTitle < text.length && text[afterTitle] in " \t") afterTitle++
            if (afterTitle >= text.length || text[afterTitle] == '\n') {
                title = titleResult.first
                i = afterTitle
                if (i < text.length && text[i] == '\n') i++
                return LinkRefDef(normalizedLabel, resolveEntities(dest.first), title?.let { resolveEntities(it) }) to i
            }
        }
        // Title parse failed — fall through to check if def is valid without title
        i = wsBeforeTitle
        while (i < text.length && text[i] in " \t") i++
    }

    // No title — check line ends properly
    if (i >= text.length || text[i] == '\n') {
        if (i < text.length && text[i] == '\n') i++
        return LinkRefDef(normalizedLabel, resolveEntities(dest.first), null) to i
    }

    return null
}

// ── Entity / numeric character reference parsing ───────────────────────

/**
 * Tries to parse an HTML entity or numeric character reference at position [start].
 * Returns (Inline, nextIndex) or null.
 *
 * Resolves the entity to its character(s):
 * - Named: `&name;` → resolved character(s) if valid HTML5 entity
 * - Decimal: `&#digits;` → Unicode character
 * - Hex: `&#xhex;` or `&#Xhex;` → Unicode character
 */
private fun tryParseEntity(text: String, start: Int): Pair<Inline, Int>? {
    if (start >= text.length || text[start] != '&') return null
    var i = start + 1
    if (i >= text.length) return null

    if (text[i] == '#') {
        i++
        if (i >= text.length) return null
        if (text[i] == 'x' || text[i] == 'X') {
            // Hex
            i++
            val hexStart = i
            while (i < text.length && isHexDigit(text[i]) && i - hexStart < 6) i++
            if (i == hexStart || i >= text.length || text[i] != ';') return null
            i++ // consume ';'
            val codePoint = text.substring(hexStart, i - 1).toIntOrNull(16) ?: return null
            val resolved = resolveCodePoint(codePoint)
            return Inline.Text(resolved) to i
        } else {
            // Decimal
            val decStart = i
            while (i < text.length && text[i].isDigit() && i - decStart < 7) i++
            if (i == decStart || i >= text.length || text[i] != ';') return null
            i++
            val codePoint = text.substring(decStart, i - 1).toIntOrNull() ?: return null
            val resolved = resolveCodePoint(codePoint)
            return Inline.Text(resolved) to i
        }
    } else {
        // Named entity
        val nameStart = i
        while (i < text.length && text[i].isLetterOrDigit() && i - nameStart < 31) i++
        if (i == nameStart || i >= text.length || text[i] != ';') return null
        val name = text.substring(nameStart, i)
        i++
        val resolved = HTML5_ENTITIES[name] ?: return null
        return Inline.Text(resolved) to i
    }
}

/**
 * Resolves a numeric code point to its character string.
 * Code point 0 maps to U+FFFD (replacement character).
 */
private fun resolveCodePoint(codePoint: Int): String {
    if (codePoint == 0) return "\uFFFD"
    return if (codePoint <= 0xFFFF) {
        codePoint.toChar().toString()
    } else if (codePoint <= 0x10FFFF) {
        // Supplementary character — encode as surrogate pair
        val high = ((codePoint - 0x10000) shr 10) + 0xD800
        val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
        "${high.toChar()}${low.toChar()}"
    } else {
        "\uFFFD"
    }
}

private fun isHexDigit(ch: Char): Boolean =
    ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'

/**
 * Resolves HTML entities and numeric character references in a string.
 * Used for link destinations, titles, and code fence info strings.
 */
internal fun resolveEntities(text: String): String = buildString {
    var i = 0
    while (i < text.length) {
        if (text[i] == '&') {
            val entity = tryParseEntity(text, i)
            if (entity != null) {
                append((entity.first as Inline.Text).literal)
                i = entity.second
            } else {
                append('&')
                i++
            }
        } else {
            append(text[i])
            i++
        }
    }
}

// ── Autolink parsing ───────────────────────────────────────────────────

/**
 * Tries to parse an autolink `<scheme:...>` or email autolink at position [start].
 * Returns (Inline, nextIndex) or null.
 */
private fun tryParseAutolink(text: String, start: Int): Pair<Inline, Int>? {
    if (start >= text.length || text[start] != '<') return null
    val closeIdx = text.indexOf('>', start + 1)
    if (closeIdx == -1) return null

    val content = text.substring(start + 1, closeIdx)

    // Check for newlines or spaces — not allowed in autolinks
    if (content.contains('\n') || content.contains(' ')) return null

    // URI autolink: scheme:content
    val schemeMatch = URI_SCHEME_REGEX.matchAt(content, 0)
    if (schemeMatch != null) {
        // Validate: no `<` in content
        if (!content.contains('<')) {
            return Inline.Autolink(content) to (closeIdx + 1)
        }
    }

    // Email autolink
    if (EMAIL_REGEX.matches(content)) {
        return Inline.Autolink("mailto:$content") to (closeIdx + 1)
    }

    return null
}

private val URI_SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]{1,31}:")
private val EMAIL_REGEX = Regex(
    "^[a-zA-Z0-9.!#\$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?" +
        "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"
)

// ── Raw HTML inline parsing ────────────────────────────────────────────

/**
 * Tries to parse inline raw HTML at position [start].
 * Returns (htmlText, nextIndex) or null.
 *
 * Handles: open tags, closing tags, HTML comments, processing instructions,
 * declarations, and CDATA sections.
 */
private fun tryParseRawHtml(text: String, start: Int): Pair<String, Int>? {
    if (start >= text.length || text[start] != '<') return null
    var i = start + 1
    if (i >= text.length) return null

    // HTML comment: <!-- ... -->
    if (text.startsWith("!--", i)) {
        // Abruptly closed comments: <!--> and <!----->
        if (i + 3 < text.length && text[i + 3] == '>') {
            return text.substring(start, start + 5) to (start + 5)
        }
        if (i + 4 < text.length && text[i + 3] == '-' && text[i + 4] == '>') {
            return text.substring(start, start + 6) to (start + 6)
        }
        val endIdx = text.indexOf("-->", i + 3)
        if (endIdx == -1) return null
        val result = text.substring(start, endIdx + 3)
        return result to (endIdx + 3)
    }

    // Processing instruction: <? ... ?>
    if (text[i] == '?') {
        val endIdx = text.indexOf("?>", i + 1)
        if (endIdx == -1) return null
        return text.substring(start, endIdx + 2) to (endIdx + 2)
    }

    // Declaration: <! LETTER ... >
    if (text[i] == '!' && i + 1 < text.length && text[i + 1].isUpperCase()) {
        val endIdx = text.indexOf('>', i + 2)
        if (endIdx == -1) return null
        return text.substring(start, endIdx + 1) to (endIdx + 1)
    }

    // CDATA: <![CDATA[ ... ]]>
    if (text.startsWith("![CDATA[", i)) {
        val endIdx = text.indexOf("]]>", i + 8)
        if (endIdx == -1) return null
        return text.substring(start, endIdx + 3) to (endIdx + 3)
    }

    // Closing tag: </tagname ... >
    if (text[i] == '/') {
        i++
        if (i >= text.length || !text[i].isLetter()) return null
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '-')) i++
        // Optional whitespace
        while (i < text.length && text[i] in " \t\n") i++
        if (i >= text.length || text[i] != '>') return null
        return text.substring(start, i + 1) to (i + 1)
    }

    // Open tag: <tagname attributes... /?>
    if (!text[i].isLetter()) return null
    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '-')) i++

    // Attributes
    while (i < text.length) {
        // Skip whitespace
        val wsStart = i
        while (i < text.length && text[i] in " \t\n") i++
        if (i == wsStart) break // no whitespace before attribute

        if (i >= text.length) return null
        if (text[i] == '>' || text[i] == '/') break

        // Attribute name
        if (!text[i].isLetter() && text[i] != '_' && text[i] != ':') return null
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] in "_:.-")) i++

        // Optional value
        // Skip whitespace (save position in case = not found)
        val preEqPos = i
        while (i < text.length && text[i] in " \t\n") i++
        if (i < text.length && text[i] == '=') {
            i++ // consume '='
            while (i < text.length && text[i] in " \t\n") i++
            if (i >= text.length) return null

            when (text[i]) {
                '\'' -> {
                    i++
                    while (i < text.length && text[i] != '\'') i++
                    if (i >= text.length) return null
                    i++ // consume closing quote
                }
                '"' -> {
                    i++
                    while (i < text.length && text[i] != '"') i++
                    if (i >= text.length) return null
                    i++
                }
                else -> {
                    // Unquoted value
                    if (text[i] in " \t\n\"'=<>`") return null
                    while (i < text.length && text[i] !in " \t\n\"'=<>`>") i++
                }
            }
        } else {
            // No `=` found — rewind to position after attribute name
            // so the whitespace can be re-consumed as separator
            i = preEqPos
        }
    }

    // Skip whitespace before closing
    while (i < text.length && text[i] in " \t\n") i++

    // Self-closing or closing
    if (i < text.length && text[i] == '/') i++
    if (i >= text.length || text[i] != '>') return null

    return text.substring(start, i + 1) to (i + 1)
}
