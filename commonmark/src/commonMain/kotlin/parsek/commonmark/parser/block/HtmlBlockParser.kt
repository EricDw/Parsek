package parsek.commonmark.parser.block

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.pLabel

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Block-level HTML tag names per CommonMark 0.31.2 spec, §4.6. */
private val BLOCK_LEVEL_TAGS = setOf(
    "address", "article", "aside", "base", "basefont", "blockquote", "body",
    "caption", "center", "col", "colgroup", "dd", "details", "dialog", "dir",
    "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form",
    "frame", "frameset", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header",
    "hr", "html", "iframe", "legend", "li", "link", "main", "menu", "menuitem",
    "meta", "nav", "noframes", "ol", "optgroup", "option", "p", "param",
    "search", "section", "summary", "table", "tbody", "td", "tfoot", "th",
    "thead", "title", "tr", "track", "ul",
)

/** Type-1 HTML block open tag names (case-insensitive). */
private val TYPE1_OPEN_TAGS = listOf("pre", "script", "style", "textarea")

/** Type-1 HTML block end tag strings (searched case-insensitively within a line). */
private val TYPE1_CLOSE_TAGS = listOf("</pre>", "</script>", "</style>", "</textarea>")

// ---------------------------------------------------------------------------
// Line reader
// ---------------------------------------------------------------------------

/** Result of reading one line from the character buffer. */
private data class LineResult(
    /** Line content WITHOUT the line ending. */
    val content: String,
    /** Index immediately after the consumed line ending (or after content if at EOF). */
    val nextIdx: Int,
    /** `true` if a line ending (`\n`, `\r`, or `\r\n`) was consumed. */
    val hasEnding: Boolean,
)

/**
 * Reads one line starting at [startIdx] in [chars].
 *
 * Consumes characters up to (but not including) the line ending, then consumes
 * the ending itself (`\n`, `\r\n`, or standalone `\r`). If the input ends before
 * a line ending is found, [LineResult.hasEnding] is `false`.
 */
private fun readLine(chars: List<Char>, startIdx: Int): LineResult {
    var i = startIdx
    while (i < chars.size && chars[i] != '\n' && chars[i] != '\r') i++
    val content = chars.subList(startIdx, i).joinToString("")
    val hasEnding: Boolean
    val nextIdx: Int
    when {
        i >= chars.size -> { hasEnding = false; nextIdx = i }
        chars[i] == '\r' && i + 1 < chars.size && chars[i + 1] == '\n' -> {
            hasEnding = true; nextIdx = i + 2
        }
        else -> { hasEnding = true; nextIdx = i + 1 }  // \r or \n
    }
    return LineResult(content, nextIdx, hasEnding)
}

// ---------------------------------------------------------------------------
// Pattern helpers
// ---------------------------------------------------------------------------

/** Returns `true` if [chars] starting at [idx] matches [prefix] (case-sensitive). */
private fun startsWith(chars: List<Char>, idx: Int, prefix: String): Boolean {
    if (idx + prefix.length > chars.size) return false
    return prefix.indices.all { chars[idx + it] == prefix[it] }
}

/** Returns `true` if [chars] starting at [idx] matches [prefix] (case-insensitive). */
private fun startsWithIgnoreCase(chars: List<Char>, idx: Int, prefix: String): Boolean {
    if (idx + prefix.length > chars.size) return false
    return prefix.indices.all { chars[idx + it].equals(prefix[it], ignoreCase = true) }
}

// ---------------------------------------------------------------------------
// Tag-name reader
// ---------------------------------------------------------------------------

/**
 * Reads an HTML tag name starting at [idx] in [chars].
 *
 * A tag name matches `[A-Za-z][A-Za-z0-9-]*`. Returns `(name, nextIdx)` or `null`
 * if the character at [idx] does not start a valid tag name.
 */
private fun readTagName(chars: List<Char>, idx: Int): Pair<String, Int>? {
    if (idx >= chars.size || !chars[idx].isLetter()) return null
    var i = idx
    while (i < chars.size && (chars[i].isLetterOrDigit() || chars[i] == '-')) i++
    return Pair(chars.subList(idx, i).joinToString(""), i)
}

// ---------------------------------------------------------------------------
// Type detection
// ---------------------------------------------------------------------------

/**
 * Detects the HTML block type (1–7) from the content at [idx] in [chars].
 * [idx] should already point past any leading whitespace.
 *
 * Returns the type number, or `null` if no start condition is satisfied.
 */
private fun detectHtmlBlockType(chars: List<Char>, idx: Int): Int? {
    if (idx >= chars.size || chars[idx] != '<') return null

    // Type 2: <!--
    if (startsWith(chars, idx, "<!--")) return 2

    // Type 5: <![CDATA[
    if (startsWith(chars, idx, "<![CDATA[")) return 5

    val next = chars.getOrNull(idx + 1)

    // Type 4: <! followed by an ASCII uppercase letter
    if (next == '!' && chars.getOrNull(idx + 2)?.let { it in 'A'..'Z' } == true) return 4

    // Type 3: <?
    if (next == '?') return 3

    // Type 1: <pre, <script, <style, <textarea (case-insensitive)
    //         followed by whitespace, '>', or end of line
    for (tag in TYPE1_OPEN_TAGS) {
        if (startsWithIgnoreCase(chars, idx + 1, tag)) {
            val after = idx + 1 + tag.length
            val c = chars.getOrNull(after)
            if (c == null || c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '>') return 1
        }
    }

    // Type 6: open or close block-level tag
    if (isType6Start(chars, idx)) return 6

    // Type 7: complete open or close tag (not matching types 1–6)
    if (isType7Start(chars, idx)) return 7

    return null
}

/**
 * Returns `true` if the text at [idx] is a type-6 HTML block start condition.
 *
 * Type 6 begins with `<` (optionally `</`), a block-level tag name, followed by
 * a space, tab, `>`, `/>`, or end of line.
 */
private fun isType6Start(chars: List<Char>, idx: Int): Boolean {
    if (idx >= chars.size || chars[idx] != '<') return false
    var i = idx + 1
    if (i < chars.size && chars[i] == '/') i++
    val (name, afterName) = readTagName(chars, i) ?: return false
    if (name.lowercase() !in BLOCK_LEVEL_TAGS) return false
    val c = chars.getOrNull(afterName)
    return c == null || c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '>' ||
        (c == '/' && chars.getOrNull(afterName + 1) == '>')
}

/**
 * Returns `true` if the text at [idx] is a type-7 HTML block start condition.
 *
 * Type 7 is a complete open or close tag (with valid attribute syntax) not
 * matching type 1–6 conditions, where the rest of the line contains only
 * optional horizontal whitespace.
 */
private fun isType7Start(chars: List<Char>, idx: Int): Boolean {
    if (idx >= chars.size || chars[idx] != '<') return false
    var i = idx + 1
    val isClose = i < chars.size && chars[i] == '/'
    if (isClose) i++
    val (name, afterName) = readTagName(chars, i) ?: return false
    i = afterName
    val lowerName = name.lowercase()
    if (lowerName in BLOCK_LEVEL_TAGS) return false
    if (TYPE1_OPEN_TAGS.any { it == lowerName }) return false
    if (isClose) {
        // Close tag: </tagname whitespace* >
        while (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) i++
        if (i >= chars.size || chars[i] != '>') return false
        i++ // consume >
    } else {
        // Open tag: attributes* /? >
        i = parseOpenTagTo(chars, i) ?: return false
    }
    // Rest of line must be only horizontal whitespace
    while (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) i++
    return i >= chars.size || chars[i] == '\n' || chars[i] == '\r'
}

/**
 * Parses open-tag attributes starting at [startIdx], consuming up to and
 * including the closing `>`.
 *
 * Handles: `(space+ attrname (= attrval)?)* /? >`
 *
 * Returns the index immediately after `>`, or `null` if the sequence is invalid.
 * Whitespace is limited to spaces and tabs (no newlines) so that type-7
 * detection stays on a single line.
 */
private fun parseOpenTagTo(chars: List<Char>, startIdx: Int): Int? {
    var i = startIdx
    // Zero or more attributes, each preceded by mandatory whitespace.
    while (true) {
        val ws = i
        while (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) i++
        if (i == ws) break                // no whitespace → no more attributes
        if (i >= chars.size) return null
        val c = chars[i]
        if (!c.isLetter() && c != '_' && c != ':') {
            i = ws; break                 // not an attribute name start, backtrack
        }
        // Attribute name: [A-Za-z_:][A-Za-z0-9_.:-]*
        while (i < chars.size && (chars[i].isLetterOrDigit() || chars[i] in "_:.-")) i++
        // Optional = attrval
        if (i < chars.size && chars[i] == '=') {
            i++ // consume '='
            while (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) i++
            if (i >= chars.size) return null
            when (chars[i]) {
                '"' -> {
                    i++
                    while (i < chars.size && chars[i] != '"') i++
                    if (i >= chars.size) return null
                    i++ // consume closing "
                }
                '\'' -> {
                    i++
                    while (i < chars.size && chars[i] != '\'') i++
                    if (i >= chars.size) return null
                    i++ // consume closing '
                }
                else -> {
                    val start = i
                    while (i < chars.size && chars[i] !in " \t\n\r\"'=<>`") i++
                    if (i == start) return null // empty unquoted value not allowed
                }
            }
        }
    }
    // Optional trailing whitespace before /> or >
    while (i < chars.size && (chars[i] == ' ' || chars[i] == '\t')) i++
    // Optional '/' then mandatory '>'
    if (i < chars.size && chars[i] == '/') i++
    if (i >= chars.size || chars[i] != '>') return null
    return i + 1
}

// ---------------------------------------------------------------------------
// End-condition checker
// ---------------------------------------------------------------------------

/** Returns `true` if [line] satisfies the end condition for HTML block [blockType] 1–5. */
private fun endConditionReached(blockType: Int, line: String): Boolean = when (blockType) {
    1 -> TYPE1_CLOSE_TAGS.any { line.contains(it, ignoreCase = true) }
    2 -> line.contains("-->")
    3 -> line.contains("?>")
    4 -> line.contains(">")
    5 -> line.contains("]]>")
    else -> false
}

// ---------------------------------------------------------------------------
// Public parser
// ---------------------------------------------------------------------------

/**
 * Parses a CommonMark HTML block (types 1–7).
 *
 * The CommonMark spec defines seven HTML block types, distinguished by their
 * start and end conditions:
 *
 * | Type | Start condition                                        | End condition                            |
 * |------|--------------------------------------------------------|------------------------------------------|
 * | 1    | `<pre`, `<script`, `<style`, `<textarea` (case-ins.)  | Line containing the matching closing tag |
 * | 2    | `<!--`                                                 | Line containing `-->`                    |
 * | 3    | `<?`                                                   | Line containing `?>`                     |
 * | 4    | `<!` + ASCII uppercase letter                          | Line containing `>`                      |
 * | 5    | `<![CDATA[`                                            | Line containing `]]>`                    |
 * | 6    | Block-level HTML tag (open or close)                   | Blank line (not consumed)                |
 * | 7    | Complete open or close tag (not types 1–6)             | Blank line (not consumed)                |
 *
 * The literal includes all consumed lines with line endings normalised to `\n`.
 * For types 1–5 the end-condition line is included in the literal; for types 6–7
 * the terminating blank line is **not** consumed and **not** included.
 *
 * **Note:** Type 7 cannot interrupt a paragraph. This ordering constraint is
 * enforced by the document parser, not by this parser.
 *
 * @return a [Parser] that succeeds with [Block.HtmlBlock] or fails.
 */
fun <U : Any> pHtmlBlock(): Parser<Char, Block.HtmlBlock, U> =
    pLabel(
        Parser { input ->
            val chars = input.input

            // 1. Optional 0–3 leading spaces.
            var idx = input.index
            var leading = 0
            while (leading < 3 && idx < chars.size && chars[idx] == ' ') {
                leading++; idx++
            }

            // 2. Detect HTML block type.
            val blockType = detectHtmlBlockType(chars, idx)
                ?: return@Parser Failure("HTML block", input.index, input)

            // 3. Read the first line (starting from input.index to preserve leading spaces).
            val firstLine = readLine(chars, input.index)
            idx = firstLine.nextIdx

            val literal = StringBuilder()
            literal.append(firstLine.content)
            if (firstLine.hasEnding) literal.append('\n')

            // 4. Accumulate further lines based on end condition.
            if (blockType in 1..5) {
                // End condition: a line *contains* the end marker (inclusive).
                if (!endConditionReached(blockType, firstLine.content)) {
                    while (idx < chars.size) {
                        val line = readLine(chars, idx)
                        idx = line.nextIdx
                        literal.append(line.content)
                        if (line.hasEnding) literal.append('\n')
                        if (endConditionReached(blockType, line.content)) break
                    }
                }
            } else {
                // Types 6–7: end at blank line (not consumed) or EOF.
                while (idx < chars.size) {
                    val line = readLine(chars, idx)
                    if (line.content.all { it == ' ' || it == '\t' }) break
                    idx = line.nextIdx
                    literal.append(line.content)
                    if (line.hasEnding) literal.append('\n')
                }
            }

            Success(Block.HtmlBlock(literal.toString()), idx, input)
        },
        "HTML block",
    )
