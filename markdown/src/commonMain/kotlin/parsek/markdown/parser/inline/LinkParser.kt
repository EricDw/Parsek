package parsek.markdown.parser.inline

import parsek.Failure
import parsek.Parser
import parsek.Success
import parsek.markdown.ast.Inline
import parsek.markdown.parser.block.normalizeLinkLabel
import parsek.markdown.parser.block.parseLinkDestination
import parsek.markdown.parser.block.parseLinkLabel
import parsek.markdown.parser.block.parseLinkTitle
import parsek.pLabel

/**
 * A resolver for link reference definitions. Given a normalised label, returns
 * the `(destination, title?)` pair, or `null` if the label is undefined.
 */
typealias LinkRefResolver = (label: String) -> Pair<String, String?>?

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

/**
 * Scans for the matching `]` starting at [start] (the index just past the
 * opening `[`). Handles nesting and backslash escapes.
 *
 * Returns the index of the closing `]`, or `null` if not found.
 */
private fun findClosingBracket(chars: List<Char>, start: Int): Int? {
    var depth = 1
    var i = start
    while (i < chars.size) {
        when {
            chars[i] == '\\' && i + 1 < chars.size -> i += 2
            chars[i] == '`' -> {
                // Skip code span: count backtick run length and find matching closing run
                val tickStart = i
                while (i < chars.size && chars[i] == '`') i++
                val tickLen = i - tickStart
                // Find matching closing backtick run
                var found = false
                while (i < chars.size) {
                    if (chars[i] == '`') {
                        val closeStart = i
                        while (i < chars.size && chars[i] == '`') i++
                        if (i - closeStart == tickLen) { found = true; break }
                    } else {
                        i++
                    }
                }
                if (!found) {
                    // No matching closing backticks; backticks are literal
                    i = tickStart + tickLen
                }
            }
            chars[i] == '<' -> {
                // Skip <...> constructs (autolinks, raw HTML) that might contain ]
                val angleEnd = skipAngleBracketContent(chars, i)
                if (angleEnd != null) {
                    i = angleEnd
                } else {
                    i++
                }
            }
            chars[i] == '[' -> { depth++; i++ }
            chars[i] == ']' -> {
                depth--
                if (depth == 0) return i
                i++
            }
            else -> i++
        }
    }
    return null
}

/**
 * Skips an angle-bracket construct `<...>` starting at [start].
 * Matches autolinks and raw HTML tags. Returns the index after `>`, or null.
 */
private fun skipAngleBracketContent(chars: List<Char>, start: Int): Int? {
    if (start >= chars.size || chars[start] != '<') return null
    var i = start + 1
    // Simple scan: find the closing '>' without line breaks
    while (i < chars.size) {
        when (chars[i]) {
            '>' -> return i + 1
            '\n', '\r' -> return null  // no line breaks in angle constructs
            '<' -> return null  // nested < not allowed
            else -> i++
        }
    }
    return null
}

/**
 * Tries to parse an inline link suffix: `(destination "title"?)` starting
 * at [idx] (expected to be the `(` character).
 *
 * Returns `(destination, title, nextIndex)` or `null` on failure.
 */
private fun tryInlineLinkSuffix(chars: List<Char>, idx: Int): Triple<String, String?, Int>? {
    if (idx >= chars.size || chars[idx] != '(') return null
    var i = idx + 1

    // Skip optional whitespace (including up to one line ending).
    i = skipLinkWhitespace(chars, i)

    // Empty parens: ()
    if (i < chars.size && chars[i] == ')') return Triple("", null, i + 1)

    // Parse destination.
    val (dest, afterDest) = parseLinkDestination(chars, i) ?: return null
    i = afterDest

    // Skip optional whitespace.
    val posAfterDest = i
    i = skipLinkWhitespace(chars, i)
    val hadWs = i > posAfterDest

    // Check for closing ')' or title.
    if (i < chars.size && chars[i] == ')') return Triple(dest, null, i + 1)

    // Try title (must have whitespace before it).
    if (!hadWs) return null
    val c = chars.getOrNull(i)
    if (c != '"' && c != '\'' && c != '(') return null
    val (title, afterTitle) = parseLinkTitle(chars, i) ?: return null
    i = afterTitle

    // Skip optional whitespace after title.
    i = skipLinkWhitespace(chars, i)

    // Must end with ')'.
    if (i >= chars.size || chars[i] != ')') return null
    return Triple(dest, title, i + 1)
}

/**
 * Skips spaces, tabs, and up to one line ending (CR, LF, or CRLF).
 * Returns the new index.
 */
private fun skipLinkWhitespace(chars: List<Char>, startIdx: Int): Int {
    var i = startIdx
    while (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) i++
    if (i < chars.size && (chars[i] == '\n' || chars[i] == '\r')) {
        if (chars[i] == '\r' && i + 1 < chars.size && chars[i + 1] == '\n') i += 2 else i++
        while (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) i++
    }
    return i
}

/**
 * Returns `true` if any inline in the list (recursively) is a [Inline.Link].
 * Used to enforce the spec rule that links cannot contain other links.
 */
private fun containsLink(inlines: List<Inline>): Boolean =
    inlines.any { inline ->
        when (inline) {
            is Inline.Link -> true
            is Inline.Emphasis -> containsLink(inline.children)
            is Inline.StrongEmphasis -> containsLink(inline.children)
            is Inline.Image -> containsLink(inline.children)
            else -> false
        }
    }

// ---------------------------------------------------------------------------
// pLink
// ---------------------------------------------------------------------------

/**
 * Parses a CommonMark link (§6.6–6.8).
 *
 * Four syntactic forms are recognised (tried in order):
 *
 * 1. **Inline link**: `[text](destination "title")`
 * 2. **Full reference**: `[text][label]` — looks up `label` in the reference map.
 * 3. **Collapsed reference**: `[text][]` — uses the link text as the label.
 * 4. **Shortcut reference**: `[text]` — same as collapsed, with no trailing `[]`.
 *
 * The link text between `[` and `]` is recursively parsed as inline content
 * using [contentParser]. Reference-style links are resolved via [resolveRef].
 *
 * @param contentParser a function that parses a list of characters into a
 *   list of inline nodes (including emphasis post-processing). This enables
 *   mutual recursion between `pLink` and the top-level inline parser.
 * @param resolveRef a function that resolves a normalised link label to
 *   `(destination, title?)`, or returns `null` if the label is undefined.
 *   Defaults to always returning `null` (no reference resolution).
 *
 * @return a [Parser] that succeeds with [Inline.Link], or fails.
 */
fun <U : Any> pLink(
    contentParser: (chars: List<Char>, userContext: U) -> List<Inline>,
    resolveRef: LinkRefResolver = { null },
): Parser<Char, Inline, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            val start = input.index

            // Must start with '['.
            if (start >= chars.size || chars[start] != '[')
                return@Parser Failure("link", start, input)

            // Find matching ']'.
            val closeBracket = findClosingBracket(chars, start + 1)
                ?: return@Parser Failure("link", start, input)

            val linkTextChars = chars.subList(start + 1, closeBracket)
            val idx = closeBracket + 1

            // Parse link text children once (used by all forms).
            // If children contain a link, the outer link must fail (§6.6).
            val children by lazy {
                val c = contentParser(linkTextChars, input.userContext)
                if (containsLink(c)) null else c
            }

            // 1. Inline link: [text](dest "title")
            tryInlineLinkSuffix(chars, idx)?.let { (dest, title, nextIdx) ->
                children?.let { c ->
                    return@Parser Success(Inline.Link(dest, title, c), nextIdx, input)
                }
            }

            // 2. Full reference: [text][label]
            // 3. Collapsed reference: [text][]
            // Per spec §6.6: if `]` is followed by `[`, we MUST try full/collapsed
            // reference. If these fail, we do NOT fall back to shortcut — the
            // presence of `[` after `]` prevents shortcut interpretation.
            if (idx < chars.size && chars[idx] == '[') {
                // Try collapsed first: [text][]
                if (idx + 1 < chars.size && chars[idx + 1] == ']') {
                    val collLabel = normalizeLinkLabel(linkTextChars.joinToString(""))
                    if (collLabel.isNotBlank()) {
                        resolveRef(collLabel)?.let { (dest, title) ->
                            children?.let { c ->
                                return@Parser Success(
                                    Inline.Link(dest, title, c), idx + 2, input,
                                )
                            }
                        }
                    }
                } else {
                    // Try full reference: [text][label]
                    parseLinkLabel(chars, idx)?.let { (rawLabel, afterLabel) ->
                        val fullLabel = normalizeLinkLabel(rawLabel)
                        if (fullLabel.isNotBlank()) {
                            resolveRef(fullLabel)?.let { (dest, title) ->
                                children?.let { c ->
                                    return@Parser Success(
                                        Inline.Link(dest, title, c), afterLabel, input,
                                    )
                                }
                            }
                        }
                    }
                }
                // `[` followed `]` but neither full nor collapsed resolved — no shortcut fallback.
                return@Parser Failure("link", start, input)
            }

            // 4. Shortcut reference: [text] (only when NOT followed by `[`)
            val label = normalizeLinkLabel(linkTextChars.joinToString(""))
            if (label.isNotBlank()) {
                resolveRef(label)?.let { (dest, title) ->
                    children?.let { c ->
                        return@Parser Success(
                            Inline.Link(dest, title, c), idx, input,
                        )
                    }
                }
            }

            Failure("link", start, input)
        },
        "link",
    )

// ---------------------------------------------------------------------------
// pImage
// ---------------------------------------------------------------------------

/**
 * Parses a CommonMark image (§6.9).
 *
 * The syntax is identical to a link, but starts with `![` instead of `[`.
 * The content between `![` and `]` is parsed as inline content to produce
 * [Inline.Image.children]; the alt text is derived from flattening those
 * children to plain text in the renderer.
 *
 * Four syntactic forms are recognised (tried in order):
 *
 * 1. **Inline image**: `![alt](destination "title")`
 * 2. **Full reference**: `![alt][label]`
 * 3. **Collapsed reference**: `![alt][]`
 * 4. **Shortcut reference**: `![alt]`
 *
 * @param contentParser a function that parses a list of characters into a
 *   list of inline nodes. This enables recursive inline parsing of alt text.
 * @param resolveRef a function that resolves a normalised link label to
 *   `(destination, title?)`, or returns `null` if the label is undefined.
 *   Defaults to always returning `null` (no reference resolution).
 *
 * @return a [Parser] that succeeds with [Inline.Image], or fails.
 */
fun <U : Any> pImage(
    contentParser: (chars: List<Char>, userContext: U) -> List<Inline> = { _, _ -> emptyList() },
    resolveRef: LinkRefResolver = { null },
): Parser<Char, Inline, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            val start = input.index

            // Must start with '!['.
            if (start + 1 >= chars.size || chars[start] != '!' || chars[start + 1] != '[')
                return@Parser Failure("image", start, input)

            // Find matching ']'.
            val closeBracket = findClosingBracket(chars, start + 2)
                ?: return@Parser Failure("image", start, input)

            val altChars = chars.subList(start + 2, closeBracket)
            val altText = altChars.joinToString("")
            val idx = closeBracket + 1

            // 1. Inline image: ![alt](dest "title")
            tryInlineLinkSuffix(chars, idx)?.let { (dest, title, nextIdx) ->
                val children = contentParser(altChars, input.userContext)
                return@Parser Success(
                    Inline.Image(dest, title, altText, children), nextIdx, input,
                )
            }

            // 2. Full reference: ![alt][label]
            // 3. Collapsed reference: ![alt][]
            // Same rule as links: `[` after `]` prevents shortcut fallback.
            if (idx < chars.size && chars[idx] == '[') {
                if (idx + 1 < chars.size && chars[idx + 1] == ']') {
                    val collLabel = normalizeLinkLabel(altText)
                    if (collLabel.isNotBlank()) {
                        resolveRef(collLabel)?.let { (dest, title) ->
                            val children = contentParser(altChars, input.userContext)
                            return@Parser Success(
                                Inline.Image(dest, title, altText, children), idx + 2, input,
                            )
                        }
                    }
                } else {
                    parseLinkLabel(chars, idx)?.let { (rawLabel, afterLabel) ->
                        val fullLabel = normalizeLinkLabel(rawLabel)
                        if (fullLabel.isNotBlank()) {
                            resolveRef(fullLabel)?.let { (dest, title) ->
                                val children = contentParser(altChars, input.userContext)
                                return@Parser Success(
                                    Inline.Image(dest, title, altText, children), afterLabel, input,
                                )
                            }
                        }
                    }
                }
                return@Parser Failure("image", start, input)
            }

            // 4. Shortcut reference: ![alt] (only when NOT followed by `[`)
            val label = normalizeLinkLabel(altText)
            if (label.isNotBlank()) {
                resolveRef(label)?.let { (dest, title) ->
                    val children = contentParser(altChars, input.userContext)
                    return@Parser Success(
                        Inline.Image(dest, title, altText, children), idx, input,
                    )
                }
            }

            Failure("image", start, input)
        },
        "image",
    )
