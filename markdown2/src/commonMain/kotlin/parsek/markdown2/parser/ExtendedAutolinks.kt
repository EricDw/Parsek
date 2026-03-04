package parsek.markdown2.parser

import parsek.markdown.ast.Inline

/**
 * Post-processes a list of inlines, scanning [Inline.Text] nodes for GFM
 * extended autolinks (§6.9) and splitting them into [Inline.ExtendedAutolink]
 * and surrounding [Inline.Text] nodes.
 *
 * Extended autolinks recognise bare URLs and email addresses without angle
 * brackets. They can only appear at the start of text, after whitespace, or
 * after one of `*`, `_`, `~`, `(`.
 *
 * Three forms are supported:
 * - **www autolinks**: `www.` followed by a valid domain and optional path.
 * - **URL autolinks**: `http://`, `https://`, or `ftp://` followed by domain + path.
 * - **Email autolinks**: `local@domain` patterns.
 */
internal fun splitExtendedAutolinks(inlines: List<Inline>): List<Inline> {
    val merged = mergeAdjacentText(inlines)
    val result = mutableListOf<Inline>()
    for (inline in merged) {
        when (inline) {
            is Inline.Text -> splitTextAutolinks(inline.literal, result)
            is Inline.Emphasis -> result.add(Inline.Emphasis(splitExtendedAutolinks(inline.children)))
            is Inline.StrongEmphasis -> result.add(Inline.StrongEmphasis(splitExtendedAutolinks(inline.children)))
            is Inline.Strikethrough -> result.add(Inline.Strikethrough(splitExtendedAutolinks(inline.children)))
            is Inline.Link -> result.add(Inline.Link(inline.destination, inline.title, splitExtendedAutolinks(inline.children)))
            else -> result.add(inline)
        }
    }
    return result
}

/**
 * Scans [text] for extended autolinks and appends the resulting [Inline]
 * nodes to [out].
 */
private fun splitTextAutolinks(text: String, out: MutableList<Inline>) {
    var i = 0
    var textStart = 0

    while (i < text.length) {
        val contextOk = i == 0 || run {
            val before = text[i - 1]
            before.isWhitespace() || before == '*' || before == '_' || before == '~' || before == '('
        }

        if (contextOk) {
            // Try www autolink
            if (text.startsWith("www.", i)) {
                val end = scanWwwAutolink(text, i)
                if (end > i + 4) {
                    if (i > textStart) out.add(Inline.Text(text.substring(textStart, i)))
                    val url = text.substring(i, end)
                    out.add(Inline.ExtendedAutolink("http://$url"))
                    textStart = end
                    i = end
                    continue
                }
            }

            // Try URL autolink
            val scheme = detectScheme(text, i)
            if (scheme != null) {
                val end = scanUrlAutolink(text, i + scheme.length, i)
                if (end > i + scheme.length) {
                    if (i > textStart) out.add(Inline.Text(text.substring(textStart, i)))
                    out.add(Inline.ExtendedAutolink(text.substring(i, end)))
                    textStart = end
                    i = end
                    continue
                }
            }

            // Try email autolink
            if (text[i].isLetterOrDigit() || text[i] == '+' || text[i] == '.' || text[i] == '-' || text[i] == '_') {
                val end = scanEmailAutolink(text, i)
                if (end > i) {
                    if (i > textStart) out.add(Inline.Text(text.substring(textStart, i)))
                    val email = text.substring(i, end)
                    out.add(Inline.ExtendedAutolink("mailto:$email"))
                    textStart = end
                    i = end
                    continue
                }
            }
        }
        i++
    }

    if (textStart < text.length) {
        out.add(Inline.Text(text.substring(textStart)))
    }
}

// ── www autolink ────────────────────────────────────────────────────────

private fun scanWwwAutolink(text: String, start: Int): Int {
    var i = start + 4 // skip "www."
    val domainEnd = scanDomain(text, i)
    if (domainEnd <= i) return start
    i = scanPath(text, domainEnd)
    return trimTrailing(text, start, i)
}

// ── URL autolink ────────────────────────────────────────────────────────

private fun detectScheme(text: String, start: Int): String? {
    for (scheme in listOf("https://", "http://", "ftp://")) {
        if (text.regionMatches(start, scheme, 0, scheme.length, ignoreCase = true)) return scheme
    }
    return null
}

private fun scanUrlAutolink(text: String, afterScheme: Int, start: Int): Int {
    val domainEnd = scanDomain(text, afterScheme)
    if (domainEnd <= afterScheme) return afterScheme
    val pathEnd = scanPath(text, domainEnd)
    return trimTrailing(text, start, pathEnd)
}

// ── Email autolink ──────────────────────────────────────────────────────

private fun scanEmailAutolink(text: String, start: Int): Int {
    var i = start

    // Local part
    val localStart = i
    while (i < text.length && isEmailLocalChar(text[i])) i++
    if (i == localStart) return start

    // @
    if (i >= text.length || text[i] != '@') return start
    i++

    // Domain
    val domainStart = i
    var hasDot = false
    while (i < text.length && isEmailDomainChar(text[i])) {
        if (text[i] == '.') hasDot = true
        i++
    }
    if (i == domainStart || !hasDot) return start

    // Last char of domain cannot be - or _
    if (text[i - 1] == '-' || text[i - 1] == '_') return start

    // Trailing . excluded
    while (i > domainStart && text[i - 1] == '.') i--
    if (i == domainStart) return start

    // Re-verify dot
    var stillHasDot = false
    for (j in domainStart until i) if (text[j] == '.') stillHasDot = true
    if (!stillHasDot) return start

    return i
}

private fun isEmailLocalChar(ch: Char): Boolean =
    ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_' || ch == '+'

private fun isEmailDomainChar(ch: Char): Boolean =
    ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_'

// ── Shared helpers ──────────────────────────────────────────────────────

private fun scanDomain(text: String, start: Int): Int {
    var i = start
    while (i < text.length && isDomainChar(text[i])) i++
    if (i == start) return start

    val domain = text.substring(start, i)
    if ('.' !in domain) return start

    val segments = domain.split('.')
    if (segments.size >= 2 && '_' in segments[segments.size - 1]) return start
    if (segments.size >= 2 && '_' in segments[segments.size - 2]) return start

    return i
}

private fun isDomainChar(ch: Char): Boolean =
    ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.'

private fun scanPath(text: String, start: Int): Int {
    var i = start
    while (i < text.length && text[i] != ' ' && text[i] != '\t' &&
        text[i] != '\n' && text[i] != '\r' && text[i] != '<'
    ) {
        i++
    }
    return i
}

/**
 * Merges adjacent [Inline.Text] nodes into a single node.
 */
private fun mergeAdjacentText(inlines: List<Inline>): List<Inline> {
    if (inlines.size <= 1) return inlines
    val result = mutableListOf<Inline>()
    var pendingText: StringBuilder? = null
    for (inline in inlines) {
        if (inline is Inline.Text) {
            if (pendingText == null) {
                pendingText = StringBuilder(inline.literal)
            } else {
                pendingText.append(inline.literal)
            }
        } else {
            if (pendingText != null) {
                result.add(Inline.Text(pendingText.toString()))
                pendingText = null
            }
            result.add(inline)
        }
    }
    if (pendingText != null) {
        result.add(Inline.Text(pendingText.toString()))
    }
    return result
}

private fun trimTrailing(text: String, start: Int, end: Int): Int {
    var e = end
    while (e > start) {
        val last = text[e - 1]
        when {
            last in "?!.,:*_~" -> e--
            last == ')' -> {
                var open = 0; var close = 0
                for (j in start until e) {
                    if (text[j] == '(') open++
                    if (text[j] == ')') close++
                }
                if (close > open) e-- else return e
            }
            last == ';' -> {
                val semiIdx = e - 1
                var j = semiIdx - 1
                while (j >= start && text[j].isLetterOrDigit()) j--
                if (j >= start && text[j] == '&') e = j
                else return e
            }
            else -> return e
        }
    }
    return e
}
