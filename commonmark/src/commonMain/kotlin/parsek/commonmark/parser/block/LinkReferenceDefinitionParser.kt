package parsek.commonmark.parser.block

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.pLabel

// ---------------------------------------------------------------------------
// Label helpers
// ---------------------------------------------------------------------------

/**
 * Reads a link label `[content]` starting at [idx] in [chars].
 *
 * Rules (CommonMark §4.7):
 * - Must start with `[`
 * - Content must not contain unescaped `[` or `]`
 * - At most 999 characters (not counting the brackets)
 * - Backslash escapes within the content are preserved verbatim for the caller
 *   to normalise as needed
 *
 * Returns `(rawContent, nextIdx)` — the raw label text without brackets — or
 * `null` if the label is syntactically invalid.
 */
internal fun parseLinkLabel(chars: List<Char>, idx: Int): Pair<String, Int>? {
    if (idx >= chars.size || chars[idx] != '[') return null
    var i = idx + 1
    val sb = StringBuilder()
    while (i < chars.size) {
        when {
            chars[i] == ']' -> return Pair(sb.toString(), i + 1)
            chars[i] == '[' -> return null  // unescaped '[' not allowed
            chars[i] == '\\' && i + 1 < chars.size -> {
                sb.append('\\'); sb.append(chars[i + 1])
                i += 2
            }
            else -> { sb.append(chars[i]); i++ }
        }
        if (sb.length > 999) return null
    }
    return null  // no closing ']'
}

/**
 * Normalises a link label string for use as a reference map key.
 *
 * Normalisation steps:
 * 1. Unicode case folding (via [String.lowercase]).
 * 2. Collapse runs of whitespace to a single space.
 * 3. Trim leading and trailing whitespace.
 *
 * This function is `internal` so it can be reused by the inline link parsers
 * in Phase 5.
 */
internal fun normalizeLinkLabel(label: String): String =
    label.lowercase().replace(Regex("\\s+"), " ").trim()

// ---------------------------------------------------------------------------
// Destination helpers
// ---------------------------------------------------------------------------

/**
 * Reads a link destination starting at [idx] in [chars].
 *
 * Two forms are recognised (CommonMark §4.7):
 * - **Angle-bracket**: `<content>` — no line endings; unescaped `<` or `>` are
 *   not permitted inside. The surrounding `<` and `>` are not included in the
 *   returned string.
 * - **Bare**: a non-empty sequence without ASCII spaces, control characters, or
 *   line endings. Unescaped parentheses must be balanced.
 *
 * Backslash escapes in both forms are processed (the backslash is stripped from
 * the returned value).
 *
 * Returns `(destination, nextIdx)` or `null` on failure.
 */
internal fun parseLinkDestination(chars: List<Char>, idx: Int): Pair<String, Int>? {
    if (idx >= chars.size) return null
    if (chars[idx] == '<') {
        // Angle-bracket form.
        var i = idx + 1
        val sb = StringBuilder()
        while (i < chars.size && chars[i] != '>') {
            when {
                chars[i] == '\n' || chars[i] == '\r' -> return null  // no line endings
                chars[i] == '<' -> return null                         // unescaped '<'
                chars[i] == '\\' && i + 1 < chars.size -> { sb.append(chars[i + 1]); i += 2 }
                else -> { sb.append(chars[i]); i++ }
            }
        }
        if (i >= chars.size) return null   // no closing '>'
        return Pair(sb.toString(), i + 1)
    } else {
        // Bare form: no spaces / controls, balanced unescaped parentheses.
        var i = idx
        val sb = StringBuilder()
        var depth = 0
        while (i < chars.size) {
            when {
                chars[i] == '\\' && i + 1 < chars.size -> { sb.append(chars[i + 1]); i += 2 }
                chars[i] == '(' -> { depth++; sb.append(chars[i]); i++ }
                chars[i] == ')' -> {
                    if (depth == 0) break
                    depth--; sb.append(chars[i]); i++
                }
                chars[i] == ' ' || chars[i] == '\t' ||
                chars[i] <= '\u001F' || chars[i] == '\u007F' ||
                chars[i] == '\n' || chars[i] == '\r' -> break
                else -> { sb.append(chars[i]); i++ }
            }
        }
        if (depth != 0) return null   // unbalanced parentheses
        if (sb.isEmpty()) return null  // bare destination must be non-empty
        return Pair(sb.toString(), i)
    }
}

// ---------------------------------------------------------------------------
// Title helpers
// ---------------------------------------------------------------------------

/**
 * Reads a link title starting at [idx] in [chars].
 *
 * Three forms are recognised (CommonMark §4.7):
 * - `"content"` — no unescaped `"`.
 * - `'content'` — no unescaped `'`.
 * - `(content)` — no unescaped `(` or `)`.
 *
 * A blank line (a line containing only spaces and tabs) inside any form causes
 * the parse to fail. Backslash escapes are processed (the backslash is stripped).
 *
 * Returns `(title, nextIdx)` or `null` on failure.
 */
internal fun parseLinkTitle(chars: List<Char>, idx: Int): Pair<String, Int>? {
    if (idx >= chars.size) return null
    val open = chars[idx]
    val close: Char = when (open) { '"' -> '"'; '\'' -> '\''; '(' -> ')'; else -> return null }
    var i = idx + 1
    val sb = StringBuilder()
    while (i < chars.size && chars[i] != close) {
        when {
            open == '(' && chars[i] == '(' -> return null  // unescaped '(' in paren form
            chars[i] == '\\' && i + 1 < chars.size -> { sb.append(chars[i + 1]); i += 2 }
            chars[i] == '\n' || chars[i] == '\r' -> {
                // Consume the line ending, then check for a blank next line.
                val nlLen = if (chars[i] == '\r' && i + 1 < chars.size && chars[i + 1] == '\n') 2 else 1
                sb.append('\n')
                i += nlLen
                // Peek: if the next line is blank (only spaces/tabs then EOL/EOF), fail.
                var j = i
                while (j < chars.size && (chars[j] == ' ' || chars[j] == '\t')) j++
                if (j >= chars.size || chars[j] == '\n' || chars[j] == '\r') return null
            }
            else -> { sb.append(chars[i]); i++ }
        }
    }
    if (i >= chars.size) return null  // no closing delimiter
    return Pair(sb.toString(), i + 1)
}

// ---------------------------------------------------------------------------
// Line-ending utility
// ---------------------------------------------------------------------------

/** Advances past one line ending (`\n`, `\r\n`, or `\r`) at [idx], or returns [idx]. */
private fun consumeLineEnding(chars: List<Char>, idx: Int): Int = when {
    idx >= chars.size -> idx
    chars[idx] == '\r' && idx + 1 < chars.size && chars[idx + 1] == '\n' -> idx + 2
    chars[idx] == '\r' || chars[idx] == '\n' -> idx + 1
    else -> idx
}

// ---------------------------------------------------------------------------
// Public parser
// ---------------------------------------------------------------------------

/**
 * Parses a CommonMark link reference definition.
 *
 * A link reference definition has the form:
 * ```
 * [label]: destination optional-title
 * ```
 * Where:
 * - `label` is 0–999 characters (no unescaped `[` or `]`); must be non-blank
 *   after normalisation.
 * - Between `]:` and the destination: optional spaces/tabs and at most one line
 *   ending (with optional leading spaces/tabs on the continuation line).
 * - The destination is either an angle-bracket form `<…>` or a bare form.
 * - After the destination: optional spaces/tabs and at most one line ending,
 *   then the optional title. A title must have at least one space/tab or a line
 *   ending separating it from the destination.
 * - The title is `"…"`, `'…'`, or `(…)`, with backslash escapes and no blank
 *   lines inside.
 * - After the title (or destination if absent): only optional spaces/tabs, then
 *   EOL or EOF.
 *
 * The [Block.LinkReferenceDefinition.label] is stored in normalised form:
 * Unicode case-folded (lowercase), whitespace sequences collapsed to a single
 * space, leading/trailing whitespace trimmed.
 *
 * Backslash escapes in the destination and title are processed (the backslash
 * is stripped from the stored value).
 *
 * **Note:** The rule that a link reference definition cannot interrupt a
 * paragraph is enforced by the document parser, not by this parser.
 *
 * @return a [Parser] that succeeds with [Block.LinkReferenceDefinition] or fails.
 */
fun <U : Any> pLinkReferenceDefinition(): Parser<Char, Block.LinkReferenceDefinition, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            var idx = input.index

            // 1. Optional 0–3 leading spaces.
            var leading = 0
            while (leading < 3 && idx < chars.size && chars[idx] == ' ') { leading++; idx++ }

            // 2. Link label [content].
            val (labelRaw, afterLabel) = parseLinkLabel(chars, idx)
                ?: return@Parser Failure("link reference definition", input.index, input)
            idx = afterLabel

            // Must be immediately followed by ':'.
            if (idx >= chars.size || chars[idx] != ':')
                return@Parser Failure("link reference definition", input.index, input)
            idx++ // consume ':'

            // 3. Optional spaces/tabs, then at most one line ending before the destination.
            while (idx < chars.size && (chars[idx] == ' ' || chars[idx] == '\t')) idx++
            if (idx < chars.size && (chars[idx] == '\n' || chars[idx] == '\r')) {
                idx = consumeLineEnding(chars, idx)
                while (idx < chars.size && (chars[idx] == ' ' || chars[idx] == '\t')) idx++
            }

            // 4. Link destination (required).
            val (destination, afterDest) = parseLinkDestination(chars, idx)
                ?: return@Parser Failure("link reference definition", input.index, input)
            idx = afterDest

            // 5. Optional title.
            //
            // The title must be separated from the destination by at least one space/tab
            // or a line ending. We save the position right after the destination so we
            // can backtrack if no title is found.
            val posAfterDest = idx

            // Consume optional spaces/tabs.
            while (idx < chars.size && (chars[idx] == ' ' || chars[idx] == '\t')) idx++
            val hadWsAfterDest = idx > posAfterDest

            // Consume optional one line ending.
            var hadLineEndingAfterDest = false
            if (idx < chars.size && (chars[idx] == '\n' || chars[idx] == '\r')) {
                idx = consumeLineEnding(chars, idx)
                hadLineEndingAfterDest = true
                while (idx < chars.size && (chars[idx] == ' ' || chars[idx] == '\t')) idx++
            }

            val title: String?
            val c = chars.getOrNull(idx)
            if ((hadWsAfterDest || hadLineEndingAfterDest) && (c == '"' || c == '\'' || c == '(')) {
                // Title-start found and there is adequate separation — title is required here.
                val (t, afterTitle) = parseLinkTitle(chars, idx)
                    ?: return@Parser Failure("link reference definition", input.index, input)
                // After the title, only optional spaces/tabs then EOL/EOF.
                var checkIdx = afterTitle
                while (checkIdx < chars.size && (chars[checkIdx] == ' ' || chars[checkIdx] == '\t')) checkIdx++
                if (checkIdx < chars.size && chars[checkIdx] != '\n' && chars[checkIdx] != '\r')
                    return@Parser Failure("link reference definition", input.index, input)
                title = t
                idx = consumeLineEnding(chars, checkIdx)
            } else {
                // No title. The definition ends at the end of the destination line.
                // Backtrack to right after the destination and verify the line is clean.
                title = null
                idx = posAfterDest
                while (idx < chars.size && (chars[idx] == ' ' || chars[idx] == '\t')) idx++
                if (idx < chars.size && chars[idx] != '\n' && chars[idx] != '\r')
                    return@Parser Failure("link reference definition", input.index, input)
                idx = consumeLineEnding(chars, idx)
            }

            // 6. Validate: label must not be blank after normalisation.
            val normalizedLabel = normalizeLinkLabel(labelRaw)
            if (normalizedLabel.isBlank())
                return@Parser Failure("link reference definition", input.index, input)

            Success(
                Block.LinkReferenceDefinition(normalizedLabel, destination, title),
                idx,
                input,
            )
        },
        "link reference definition",
    )
