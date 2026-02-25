package parsek.markdown.parser.block

import parsek.Failure
import parsek.Parser
import parsek.Success
import parsek.markdown.ast.Block
import parsek.markdown.ast.Inline
import parsek.pLabel

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

private fun isBlankLine(content: String): Boolean =
    content.all { it == ' ' || it == '\t' }

/** Strips up to 3 leading space characters from [s]. */
private fun stripUpTo3Spaces(s: String): String {
    var i = 0
    while (i < s.length && i < 3 && s[i] == ' ') i++
    return s.substring(i)
}

private fun advancePastLineEnding(chars: List<Char>, idx: Int): Int = when {
    idx >= chars.size -> idx
    chars[idx] == '\r' && idx + 1 < chars.size && chars[idx + 1] == '\n' -> idx + 2
    chars[idx] == '\r' || chars[idx] == '\n' -> idx + 1
    else -> idx
}

// ---------------------------------------------------------------------------
// Paragraph interrupt detection (§4.9)
// ---------------------------------------------------------------------------

/**
 * Returns `true` if the line starting at [startIdx] would interrupt a paragraph.
 *
 * Per CommonMark §4.9, a paragraph can be interrupted by:
 * - A thematic break
 * - An ATX heading
 * - A fenced code block opening fence
 * - A block quote marker (`>`)
 * - A bullet list item marker
 * - An ordered list item with start number 1
 * - An HTML block start condition (types 1–6 only; type 7 cannot interrupt)
 */
internal fun canInterruptParagraph(chars: List<Char>, startIdx: Int): Boolean {
    // Skip 0–3 leading spaces
    var i = startIdx
    var spaces = 0
    while (spaces < 3 && i < chars.size && chars[i] == ' ') { spaces++; i++ }

    if (i >= chars.size) return false
    val c = chars[i]

    // Thematic break
    if (isThematicBreakLine(chars, startIdx)) return true

    // ATX heading: 1-6 '#' then space/tab/EOL
    if (c == '#') {
        var hashes = 0
        var j = i
        while (j < chars.size && chars[j] == '#') { hashes++; j++ }
        if (hashes in 1..6) {
            val after = chars.getOrNull(j)
            if (after == null || after == ' ' || after == '\t' || after == '\n' || after == '\r') return true
        }
    }

    // Fenced code block: 3+ backticks or tildes
    if (c == '`' || c == '~') {
        var count = 0
        var j = i
        while (j < chars.size && chars[j] == c) { count++; j++ }
        if (count >= 3) return true
    }

    // Block quote marker
    if (c == '>') return true

    // Bullet list marker: -, +, * followed by space/tab then non-blank content.
    // An empty list item (marker + EOL or marker alone) cannot interrupt a paragraph.
    if (c == '-' || c == '+' || c == '*') {
        val after = chars.getOrNull(i + 1)
        if (after == ' ' || after == '\t') {
            // Check there is non-blank content after the spaces
            var j = i + 1
            while (j < chars.size && (chars[j] == ' ' || chars[j] == '\t')) j++
            val nc = chars.getOrNull(j)
            if (nc != null && nc != '\n' && nc != '\r') return true
        }
    }

    // Ordered list: must start at 1 to interrupt a paragraph.
    // An empty ordered list item cannot interrupt a paragraph.
    if (c.isDigit()) {
        var j = i
        while (j < chars.size && chars[j].isDigit()) j++
        val digitCount = j - i
        if (digitCount in 1..9) {
            val delim = chars.getOrNull(j)
            if (delim == '.' || delim == ')') {
                val number = chars.subList(i, j).joinToString("").toInt()
                if (number == 1) {
                    val after = chars.getOrNull(j + 1)
                    if (after == ' ' || after == '\t') {
                        // Check there is non-blank content after the spaces
                        var k = j + 1
                        while (k < chars.size && (chars[k] == ' ' || chars[k] == '\t')) k++
                        val nc = chars.getOrNull(k)
                        if (nc != null && nc != '\n' && nc != '\r') return true
                    }
                }
            }
        }
    }

    // HTML block types 1–6 (type 7 cannot interrupt a paragraph)
    if (c == '<') {
        if (isHtmlBlockInterrupt(chars, i)) return true
    }

    return false
}

/**
 * Checks if `<` at position [idx] starts an HTML block of type 1–6.
 *
 * These are fast, inline checks matching the most common patterns.
 */
private val HTML_BLOCK_TAGS_TYPE1 = listOf("pre", "script", "style", "textarea")
private val HTML_BLOCK_TAGS_TYPE6 = setOf(
    "address", "article", "aside", "base", "basefont", "blockquote", "body",
    "caption", "center", "col", "colgroup", "dd", "details", "dialog", "dir",
    "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form",
    "frame", "frameset", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header",
    "hr", "html", "iframe", "legend", "li", "link", "main", "menu", "menuitem",
    "meta", "nav", "noframes", "ol", "optgroup", "option", "p", "param",
    "search", "section", "summary", "table", "tbody", "td", "tfoot", "th",
    "thead", "title", "tr", "track", "ul",
)

private fun isHtmlBlockInterrupt(chars: List<Char>, idx: Int): Boolean {
    if (idx >= chars.size || chars[idx] != '<') return false

    // Type 2: <!--
    if (matchesAt(chars, idx, "<!--")) return true
    // Type 3: <?
    if (chars.getOrNull(idx + 1) == '?') return true
    // Type 4: <! + uppercase letter
    if (chars.getOrNull(idx + 1) == '!' && chars.getOrNull(idx + 2)?.let { it in 'A'..'Z' } == true) return true
    // Type 5: <![CDATA[
    if (matchesAt(chars, idx, "<![CDATA[")) return true

    // Type 1: <pre, <script, <style, <textarea (case-insensitive)
    for (tag in HTML_BLOCK_TAGS_TYPE1) {
        if (matchesAtIgnoreCase(chars, idx + 1, tag)) {
            val after = chars.getOrNull(idx + 1 + tag.length)
            if (after == null || after == ' ' || after == '\t' || after == '\n' || after == '\r' || after == '>') return true
        }
    }

    // Type 6: block-level open/close tag
    var j = idx + 1
    val isClose = j < chars.size && chars[j] == '/'
    if (isClose) j++
    if (j >= chars.size || !chars[j].isLetter()) return false
    val nameStart = j
    while (j < chars.size && (chars[j].isLetterOrDigit() || chars[j] == '-')) j++
    val name = chars.subList(nameStart, j).joinToString("").lowercase()
    if (name !in HTML_BLOCK_TAGS_TYPE6) return false
    val after = chars.getOrNull(j)
    return after == null || after == ' ' || after == '\t' || after == '\n' || after == '\r' || after == '>' ||
        (after == '/' && chars.getOrNull(j + 1) == '>')
}

private fun matchesAt(chars: List<Char>, idx: Int, s: String): Boolean {
    if (idx + s.length > chars.size) return false
    return s.indices.all { chars[idx + it] == s[it] }
}

private fun matchesAtIgnoreCase(chars: List<Char>, idx: Int, s: String): Boolean {
    if (idx + s.length > chars.size) return false
    return s.indices.all { chars[idx + it].equals(s[it], ignoreCase = true) }
}

/**
 * Reads one line of content (without the line ending) starting at [startIdx].
 *
 * Returns `(content, nextIdx)` where `nextIdx` is the position immediately
 * after the consumed line ending, or at EOF if there is none.
 */
private fun readLineContent(chars: List<Char>, startIdx: Int): Pair<String, Int> {
    var i = startIdx
    while (i < chars.size && chars[i] != '\n' && chars[i] != '\r') i++
    val content = chars.subList(startIdx, i).joinToString("")
    return Pair(content, advancePastLineEnding(chars, i))
}

/**
 * Returns the setext heading level for the line beginning at [startIdx]
 * (1 for an `=` underline, 2 for a `-` underline), or `null` if the line is
 * not a valid setext heading underline.
 *
 * A setext heading underline is:
 * - 0–3 leading space characters
 * - One or more `=` or `-` characters (all the same)
 * - Optional trailing spaces/tabs
 * - Ends at a line ending or EOF
 */
internal fun setextUnderlineLevel(chars: List<Char>, startIdx: Int): Int? {
    var i = startIdx
    var spaces = 0
    while (spaces < 3 && i < chars.size && chars[i] == ' ') { spaces++; i++ }
    val c = chars.getOrNull(i) ?: return null
    val level = when (c) { '=' -> 1; '-' -> 2; else -> return null }
    val markStart = i
    while (i < chars.size && chars[i] == c) i++
    if (i == markStart) return null
    while (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) i++
    return if (i >= chars.size || chars[i] == '\n' || chars[i] == '\r') level else null
}

// ---------------------------------------------------------------------------
// pSetextHeading
// ---------------------------------------------------------------------------

/**
 * Parses a CommonMark setext heading (§4.3).
 *
 * A setext heading consists of one or more non-blank content lines followed
 * immediately by a setext heading underline on a line by itself:
 * - A run of `=` characters → level-1 heading
 * - A run of `-` characters → level-2 heading
 *
 * The underline allows 0–3 leading spaces and optional trailing spaces/tabs.
 * Content lines may also have 0–3 leading spaces, which are stripped.
 *
 * The heading's raw content is formed by joining the (leading-space-stripped)
 * content lines with `\n` and trimming trailing whitespace. Inline content is
 * produced as a single [Inline.Text] stub until the inline pass is implemented.
 *
 * The rule that a setext heading cannot interrupt a paragraph is enforced by
 * the document parser, not by this parser.
 *
 * @return a [Parser] that succeeds with [Block.Heading] (level 1 or 2) or fails.
 */
fun <U : Any> pSetextHeading(): Parser<Char, Block.Heading, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            var idx = input.index
            val contentLines = mutableListOf<String>()

            while (true) {
                if (idx >= chars.size) break  // EOF with no underline → fail

                // If we have accumulated content, check whether the current line
                // is a setext heading underline.
                if (contentLines.isNotEmpty()) {
                    val level = setextUnderlineLevel(chars, idx)
                    if (level != null) {
                        // Consume the underline line.
                        while (idx < chars.size && chars[idx] != '\n' && chars[idx] != '\r') idx++
                        idx = advancePastLineEnding(chars, idx)
                        val content = contentLines.joinToString("\n").trimEnd()
                        val inlines: List<Inline> =
                            if (content.isEmpty()) emptyList() else listOf(Inline.Text(content))
                        return@Parser Success(Block.Heading(level, inlines), idx, input)
                    }
                }

                // Read the next potential content line.
                // If we already have content, check if this line would interrupt
                // a paragraph — setext heading content follows the same rules.
                if (contentLines.isNotEmpty() && canInterruptParagraph(chars, idx)) break

                val (lineContent, nextIdx) = readLineContent(chars, idx)
                if (isBlankLine(lineContent)) break  // blank line terminates search → fail
                contentLines.add(stripUpTo3Spaces(lineContent))
                idx = nextIdx
            }

            Failure("setext heading", input.index, input)
        },
        "setext heading",
    )

// ---------------------------------------------------------------------------
// pParagraph
// ---------------------------------------------------------------------------

/**
 * Parses a CommonMark paragraph (§4.9).
 *
 * A paragraph is one or more non-blank lines. The parser accumulates lines
 * until a blank line or end of input. The terminating blank line is **not**
 * consumed.
 *
 * Content lines may have 0–3 leading spaces, which are stripped. The raw
 * content is formed by joining the stripped lines with `\n` and trimming
 * trailing whitespace. Inline content is produced as a single [Inline.Text]
 * stub until the inline pass is implemented in Phase 5.
 *
 * The rule that certain block types can interrupt a paragraph is enforced by
 * the document parser, not by this parser.
 *
 * @return a [Parser] that succeeds with [Block.Paragraph] or fails.
 */
fun <U : Any> pParagraph(): Parser<Char, Block.Paragraph, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            var idx = input.index
            val contentLines = mutableListOf<String>()

            while (idx < chars.size) {
                // On continuation lines (not the first), check if this line
                // would start a block that can interrupt a paragraph (§4.9).
                if (contentLines.isNotEmpty() && canInterruptParagraph(chars, idx)) break

                val (lineContent, nextIdx) = readLineContent(chars, idx)
                if (isBlankLine(lineContent)) break
                // Strip leading whitespace from each paragraph content line
                contentLines.add(lineContent.trimStart())
                idx = nextIdx
            }

            if (contentLines.isEmpty())
                return@Parser Failure("paragraph", input.index, input)

            val content = contentLines.joinToString("\n").trimEnd()
            val inlines: List<Inline> =
                if (content.isEmpty()) emptyList() else listOf(Inline.Text(content))
            Success(Block.Paragraph(inlines), idx, input)
        },
        "paragraph",
    )
