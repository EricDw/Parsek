package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.Parser
import parsek.Success
import parsek.commonmark.ast.Inline
import parsek.pLabel

// ---------------------------------------------------------------------------
// pAutolink
// ---------------------------------------------------------------------------

/**
 * Parses a CommonMark autolink (§6.8).
 *
 * Two forms are recognised:
 * - **URI**: `<scheme:path>` where the scheme is 2–32 ASCII letters and the
 *   path contains no whitespace, `<`, or control characters.
 * - **Email**: `<local@domain>` matching the CommonMark email pattern.
 *
 * The angle brackets are consumed but not stored; [Inline.Autolink.url]
 * contains only the URI or email address.
 *
 * @return a [Parser] that succeeds with [Inline.Autolink], or fails.
 */
fun <U : Any> pAutolink(): Parser<Char, Inline, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            val start = input.index

            if (start >= chars.size || chars[start] != '<')
                return@Parser Failure("autolink", start, input)

            tryUriAutolink(chars, start)?.let { (url, end) ->
                return@Parser Success(Inline.Autolink(url), end, input)
            }
            tryEmailAutolink(chars, start)?.let { (email, end) ->
                return@Parser Success(Inline.Autolink(email), end, input)
            }

            Failure("autolink", start, input)
        },
        "autolink",
    )

/**
 * URI autolink: `<scheme:path>`
 * - scheme: 2–32 ASCII letters
 * - path: zero or more chars that are not space, `<`, control chars (≤ U+001F or U+007F)
 */
private fun tryUriAutolink(chars: List<Char>, start: Int): Pair<String, Int>? {
    var i = start + 1  // skip '<'
    val schemeStart = i
    while (i < chars.size && chars[i].isLetter()) i++
    val schemeLen = i - schemeStart
    if (schemeLen < 2 || schemeLen > 32) return null
    if (i >= chars.size || chars[i] != ':') return null
    i++  // skip ':'
    while (i < chars.size &&
        chars[i] != '>' && chars[i] != '<' &&
        chars[i] > '\u0020' && chars[i] != '\u007F'
    ) i++
    if (i >= chars.size || chars[i] != '>') return null
    val url = chars.subList(start + 1, i).joinToString("")
    return Pair(url, i + 1)
}

/**
 * Email autolink: `<local@domain>`
 * - local: one or more chars from `[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]`
 * - domain: labels of `[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?` separated by `.`
 */
private fun tryEmailAutolink(chars: List<Char>, start: Int): Pair<String, Int>? {
    var i = start + 1  // skip '<'
    // Local part
    val localStart = i
    while (i < chars.size && isEmailLocalChar(chars[i])) i++
    if (i == localStart) return null
    if (i >= chars.size || chars[i] != '@') return null
    i++  // skip '@'
    // Domain: must start with alphanumeric
    val domainStart = i
    if (domainStart >= chars.size || !chars[domainStart].isLetterOrDigit()) return null
    while (i < chars.size && (chars[i].isLetterOrDigit() || chars[i] == '-' || chars[i] == '.')) i++
    val domainEnd = i
    if (domainEnd == domainStart) return null
    // Domain must end with alphanumeric
    if (!chars[domainEnd - 1].isLetterOrDigit()) return null
    // No label may start or end with '-', and no '..' allowed
    for (j in domainStart until domainEnd - 1) {
        if (chars[j] == '.' && (chars[j + 1] == '.' || chars[j + 1] == '-')) return null
        if (chars[j] == '-' && chars[j + 1] == '.') return null
    }
    if (i >= chars.size || chars[i] != '>') return null
    val email = chars.subList(start + 1, i).joinToString("")
    return Pair(email, i + 1)
}

private fun isEmailLocalChar(c: Char): Boolean =
    c.isLetterOrDigit() || c in ".!#\$%&'*+/=?^_`{|}~-"

// ---------------------------------------------------------------------------
// pRawHtml
// ---------------------------------------------------------------------------

/**
 * Parses an inline raw HTML construct (§6.11).
 *
 * Six forms are recognised (tried in order):
 * 1. CDATA section: `<![CDATA[…]]>`
 * 2. HTML comment: `<!--…-->`
 * 3. Processing instruction: `<?…?>`
 * 4. Declaration: `<!LETTER…>`
 * 5. Closing tag: `</tagname>`
 * 6. Open tag: `<tagname attrs… /? >`
 *
 * The full source text (including `<` and `>`) is stored in [Inline.RawHtml.literal].
 *
 * @return a [Parser] that succeeds with [Inline.RawHtml], or fails.
 */
fun <U : Any> pRawHtml(): Parser<Char, Inline, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            val start = input.index

            if (start >= chars.size || chars[start] != '<')
                return@Parser Failure("raw HTML", start, input)

            val end = tryCdata(chars, start)
                ?: tryHtmlComment(chars, start)
                ?: tryProcessingInstruction(chars, start)
                ?: tryDeclaration(chars, start)
                ?: tryCloseTag(chars, start)
                ?: tryOpenTag(chars, start)
                ?: return@Parser Failure("raw HTML", start, input)

            val literal = chars.subList(start, end).joinToString("")
            Success(Inline.RawHtml(literal), end, input)
        },
        "raw HTML",
    )

// ---------------------------------------------------------------------------
// Raw HTML helpers
// ---------------------------------------------------------------------------

/** Open tag: `<tagname (attr)* whitespace? /? >` */
private fun tryOpenTag(chars: List<Char>, start: Int): Int? {
    var i = start + 1
    if (i >= chars.size || !chars[i].isLetter()) return null
    i++
    while (i < chars.size && (chars[i].isLetterOrDigit() || chars[i] == '-')) i++
    // Zero or more attributes
    while (i < chars.size) {
        val beforeWs = i
        while (i < chars.size && isHtmlWs(chars[i])) i++
        if (i < chars.size && (chars[i] == '>' || chars[i] == '/')) break
        if (i == beforeWs) return null  // no whitespace before attribute
        // Attribute name: starts with letter, '_', or ':'
        if (i >= chars.size || (!chars[i].isLetter() && chars[i] != '_' && chars[i] != ':')) return null
        i++
        while (i < chars.size && (chars[i].isLetterOrDigit() || chars[i] in "-_.:")) i++
        // Optional = value
        val savedI = i
        while (i < chars.size && isHtmlWs(chars[i])) i++
        if (i < chars.size && chars[i] == '=') {
            i++
            while (i < chars.size && isHtmlWs(chars[i])) i++
            when {
                i < chars.size && chars[i] == '"' -> {
                    i++
                    while (i < chars.size && chars[i] != '"') i++
                    if (i >= chars.size) return null
                    i++
                }
                i < chars.size && chars[i] == '\'' -> {
                    i++
                    while (i < chars.size && chars[i] != '\'') i++
                    if (i >= chars.size) return null
                    i++
                }
                else -> {
                    val uvStart = i
                    while (i < chars.size && !isHtmlWs(chars[i]) &&
                        chars[i] != '"' && chars[i] != '\'' && chars[i] != '=' &&
                        chars[i] != '<' && chars[i] != '>' && chars[i] != '`'
                    ) i++
                    if (i == uvStart) return null
                }
            }
        } else {
            i = savedI  // no '='; restore to just after attr name
        }
    }
    while (i < chars.size && isHtmlWs(chars[i])) i++
    if (i < chars.size && chars[i] == '/') i++
    if (i >= chars.size || chars[i] != '>') return null
    return i + 1
}

/** Closing tag: `</tagname whitespace* >` */
private fun tryCloseTag(chars: List<Char>, start: Int): Int? {
    if (start + 1 >= chars.size || chars[start + 1] != '/') return null
    var i = start + 2
    if (i >= chars.size || !chars[i].isLetter()) return null
    i++
    while (i < chars.size && (chars[i].isLetterOrDigit() || chars[i] == '-')) i++
    while (i < chars.size && isHtmlWs(chars[i])) i++
    if (i >= chars.size || chars[i] != '>') return null
    return i + 1
}

/**
 * HTML comment: `<!--content-->` where content:
 * - does not start with `>` or `->`
 * - does not contain `--`
 * - does not end with `-`
 */
private fun tryHtmlComment(chars: List<Char>, start: Int): Int? {
    if (!matchStr(chars, start, "<!--")) return null
    var i = start + 4
    if (i < chars.size && chars[i] == '>') return null
    if (i + 1 < chars.size && chars[i] == '-' && chars[i + 1] == '>') return null
    while (i < chars.size) {
        if (chars[i] == '-' && i + 1 < chars.size && chars[i + 1] == '-') {
            if (i + 2 < chars.size && chars[i + 2] == '>') {
                // Content must not end with '-'
                if (i > start + 4 && chars[i - 1] == '-') return null
                return i + 3
            }
            return null  // '--' in content not followed by '>'
        }
        i++
    }
    return null
}

/** Processing instruction: `<?…?>` */
private fun tryProcessingInstruction(chars: List<Char>, start: Int): Int? {
    if (!matchStr(chars, start, "<?")) return null
    var i = start + 2
    while (i + 1 < chars.size) {
        if (chars[i] == '?' && chars[i + 1] == '>') return i + 2
        i++
    }
    return null
}

/** Declaration: `<!UPPER-LETTER…>` */
private fun tryDeclaration(chars: List<Char>, start: Int): Int? {
    if (!matchStr(chars, start, "<!")) return null
    val letterIdx = start + 2
    if (letterIdx >= chars.size || !chars[letterIdx].isUpperCase()) return null
    var i = letterIdx + 1
    while (i < chars.size && chars[i] != '>') i++
    if (i >= chars.size) return null
    return i + 1
}

/** CDATA section: `<![CDATA[…]]>` */
private fun tryCdata(chars: List<Char>, start: Int): Int? {
    if (!matchStr(chars, start, "<![CDATA[")) return null
    var i = start + 9
    while (i + 2 < chars.size) {
        if (chars[i] == ']' && chars[i + 1] == ']' && chars[i + 2] == '>') return i + 3
        i++
    }
    return null
}

private fun matchStr(chars: List<Char>, start: Int, s: String): Boolean {
    if (start + s.length > chars.size) return false
    for (j in s.indices) if (chars[start + j] != s[j]) return false
    return true
}

private fun isHtmlWs(c: Char): Boolean =
    c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C'
