package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.Parser
import parsek.Success
import parsek.commonmark.ast.Inline
import parsek.commonmark.parser.block.normalizeLinkLabel
import parsek.commonmark.parser.block.parseLinkDestination
import parsek.commonmark.parser.block.parseLinkLabel
import parsek.commonmark.parser.block.parseLinkTitle
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

            // 1. Inline link: [text](dest "title")
            tryInlineLinkSuffix(chars, idx)?.let { (dest, title, nextIdx) ->
                val children = contentParser(linkTextChars, input.userContext)
                return@Parser Success(Inline.Link(dest, title, children), nextIdx, input)
            }

            // 2. Full reference: [text][label]
            if (idx < chars.size && chars[idx] == '[') {
                parseLinkLabel(chars, idx)?.let { (rawLabel, afterLabel) ->
                    val label = normalizeLinkLabel(rawLabel)
                    if (label.isNotBlank()) {
                        resolveRef(label)?.let { (dest, title) ->
                            val children = contentParser(linkTextChars, input.userContext)
                            return@Parser Success(
                                Inline.Link(dest, title, children), afterLabel, input,
                            )
                        }
                    }
                }
            }

            // 3. Collapsed reference: [text][]
            if (idx + 1 < chars.size && chars[idx] == '[' && chars[idx + 1] == ']') {
                val label = normalizeLinkLabel(linkTextChars.joinToString(""))
                if (label.isNotBlank()) {
                    resolveRef(label)?.let { (dest, title) ->
                        val children = contentParser(linkTextChars, input.userContext)
                        return@Parser Success(
                            Inline.Link(dest, title, children), idx + 2, input,
                        )
                    }
                }
            }

            // 4. Shortcut reference: [text]
            val label = normalizeLinkLabel(linkTextChars.joinToString(""))
            if (label.isNotBlank()) {
                resolveRef(label)?.let { (dest, title) ->
                    val children = contentParser(linkTextChars, input.userContext)
                    return@Parser Success(
                        Inline.Link(dest, title, children), idx, input,
                    )
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
 * The content between `![` and `]` provides the alt text (rendered as plain
 * text, not as inline nodes).
 *
 * Four syntactic forms are recognised (tried in order):
 *
 * 1. **Inline image**: `![alt](destination "title")`
 * 2. **Full reference**: `![alt][label]`
 * 3. **Collapsed reference**: `![alt][]`
 * 4. **Shortcut reference**: `![alt]`
 *
 * @param resolveRef a function that resolves a normalised link label to
 *   `(destination, title?)`, or returns `null` if the label is undefined.
 *   Defaults to always returning `null` (no reference resolution).
 *
 * @return a [Parser] that succeeds with [Inline.Image], or fails.
 */
fun <U : Any> pImage(
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

            val altText = chars.subList(start + 2, closeBracket).joinToString("")
            val idx = closeBracket + 1

            // 1. Inline image: ![alt](dest "title")
            tryInlineLinkSuffix(chars, idx)?.let { (dest, title, nextIdx) ->
                return@Parser Success(Inline.Image(dest, title, altText), nextIdx, input)
            }

            // 2. Full reference: ![alt][label]
            if (idx < chars.size && chars[idx] == '[') {
                parseLinkLabel(chars, idx)?.let { (rawLabel, afterLabel) ->
                    val label = normalizeLinkLabel(rawLabel)
                    if (label.isNotBlank()) {
                        resolveRef(label)?.let { (dest, title) ->
                            return@Parser Success(
                                Inline.Image(dest, title, altText), afterLabel, input,
                            )
                        }
                    }
                }
            }

            // 3. Collapsed reference: ![alt][]
            if (idx + 1 < chars.size && chars[idx] == '[' && chars[idx + 1] == ']') {
                val label = normalizeLinkLabel(altText)
                if (label.isNotBlank()) {
                    resolveRef(label)?.let { (dest, title) ->
                        return@Parser Success(
                            Inline.Image(dest, title, altText), idx + 2, input,
                        )
                    }
                }
            }

            // 4. Shortcut reference: ![alt]
            val label = normalizeLinkLabel(altText)
            if (label.isNotBlank()) {
                resolveRef(label)?.let { (dest, title) ->
                    return@Parser Success(
                        Inline.Image(dest, title, altText), idx, input,
                    )
                }
            }

            Failure("image", start, input)
        },
        "image",
    )
