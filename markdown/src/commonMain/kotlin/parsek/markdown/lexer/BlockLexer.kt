package parsek.markdown.lexer

import parsek.markdown.lexeme.Lexeme
import parsek.markdown.lexeme.SourceRange

import parsek.markdown.token.Token

/**
 * Block lexer: converts a flat list of [Lexeme]s into a sequence of block-level [Token]s.
 *
 * Works line-by-line since markdown's block structure is line-oriented.
 * Paragraph/heading content is deferred as raw `List<Lexeme>` for the inline pass.
 */

/**
 * Splits a flat list of lexemes into logical lines.
 * Each line is a list of lexemes up to (and including) a [Lexeme.Newline].
 * The last line may not end with a newline.
 */
fun splitLines(lexemes: List<Lexeme>): List<List<Lexeme>> {
    if (lexemes.isEmpty()) return emptyList()
    val lines = mutableListOf<List<Lexeme>>()
    var start = 0
    for (i in lexemes.indices) {
        if (lexemes[i] is Lexeme.Newline) {
            lines.add(lexemes.subList(start, i + 1))
            start = i + 1
        }
    }
    if (start < lexemes.size) {
        lines.add(lexemes.subList(start, lexemes.size))
    }
    return lines
}

/**
 * Returns the number of leading space-equivalent characters in a line.
 * A [Lexeme.Space] = 1, [Lexeme.SpaceRun] = count, [Lexeme.Tab] = up to 4 (to next tab stop).
 * Stops at the first non-whitespace lexeme.
 * Returns the count and the index of the first non-whitespace lexeme.
 */
private fun countLeadingSpaces(line: List<Lexeme>): Pair<Int, Int> {
    var spaces = 0
    var idx = 0
    while (idx < line.size) {
        when (val lex = line[idx]) {
            is Lexeme.Space -> { spaces++; idx++ }
            is Lexeme.SpaceRun -> { spaces += lex.count; idx++ }
            is Lexeme.Tab -> { spaces += 4 - (spaces % 4); idx++ }
            else -> break
        }
    }
    return spaces to idx
}

/**
 * Returns the source range covering a line of lexemes.
 */
private fun lineRange(line: List<Lexeme>): SourceRange {
    if (line.isEmpty()) return SourceRange(0, 0)
    return SourceRange(line.first().range.start, line.last().range.end)
}

/**
 * Returns the range from lexemes[fromIdx] to end of line, or a zero-width range at end.
 */
private fun rangeFrom(line: List<Lexeme>, fromIdx: Int): SourceRange {
    if (fromIdx >= line.size) {
        val end = if (line.isNotEmpty()) line.last().range.end else 0
        return SourceRange(end, end)
    }
    return SourceRange(line[fromIdx].range.start, line.last().range.end)
}

/**
 * Checks if the remaining lexemes (after leading spaces) form only whitespace and/or newline.
 */
private fun isBlankFrom(line: List<Lexeme>, fromIdx: Int): Boolean {
    for (i in fromIdx until line.size) {
        when (line[i]) {
            is Lexeme.Space, is Lexeme.SpaceRun, is Lexeme.Tab, is Lexeme.Newline -> continue
            else -> return false
        }
    }
    return true
}

/**
 * Strips trailing whitespace lexemes (Space, SpaceRun, Tab) from a list of lexemes,
 * also stripping a trailing Newline if present.
 */
private fun stripTrailing(lexemes: List<Lexeme>): List<Lexeme> {
    var end = lexemes.size
    while (end > 0) {
        when (lexemes[end - 1]) {
            is Lexeme.Space, is Lexeme.SpaceRun, is Lexeme.Tab, is Lexeme.Newline -> end--
            else -> break
        }
    }
    return if (end == lexemes.size) lexemes else lexemes.subList(0, end)
}

/**
 * Strips leading whitespace lexemes (Space, SpaceRun, Tab) from a list of lexemes.
 */
private fun stripLeading(lexemes: List<Lexeme>): List<Lexeme> {
    var start = 0
    while (start < lexemes.size) {
        when (lexemes[start]) {
            is Lexeme.Space, is Lexeme.SpaceRun, is Lexeme.Tab -> start++
            else -> break
        }
    }
    return if (start == 0) lexemes else lexemes.subList(start, lexemes.size)
}

// ── Blank line ──────────────────────────────────────────────────────────

/**
 * Tries to lex a blank line: a line with only whitespace (and newline).
 */
fun tryBlankLine(line: List<Lexeme>): Token.BlankLine? {
    if (isBlankFrom(line, 0)) {
        return Token.BlankLine(lineRange(line))
    }
    return null
}

// ── Thematic break ──────────────────────────────────────────────────────

/**
 * Tries to lex a thematic break: 0–3 spaces, then 3+ of same marker (`-`,`*`,`_`)
 * interspersed with optional spaces/tabs, then line ending/EOF.
 */
fun tryThematicBreak(line: List<Lexeme>): Token.ThematicBreakLine? {
    val (spaces, idx) = countLeadingSpaces(line)
    if (spaces > 3) return null
    if (idx >= line.size) return null

    // Determine marker character
    val markerChar = when (val lex = line[idx]) {
        is Lexeme.Hyphen -> '-'
        is Lexeme.HyphenRun -> '-'
        is Lexeme.Asterisk -> '*'
        is Lexeme.AsteriskRun -> '*'
        is Lexeme.Underscore -> '_'
        is Lexeme.UnderscoreRun -> '_'
        else -> return null
    }

    // Count marker chars, allowing interspersed spaces/tabs
    var count = 0
    var i = idx
    while (i < line.size) {
        when (val lex = line[i]) {
            is Lexeme.Hyphen -> if (markerChar == '-') { count++; i++ } else return null
            is Lexeme.HyphenRun -> if (markerChar == '-') { count += lex.count; i++ } else return null
            is Lexeme.Asterisk -> if (markerChar == '*') { count++; i++ } else return null
            is Lexeme.AsteriskRun -> if (markerChar == '*') { count += lex.count; i++ } else return null
            is Lexeme.Underscore -> if (markerChar == '_') { count++; i++ } else return null
            is Lexeme.UnderscoreRun -> if (markerChar == '_') { count += lex.count; i++ } else return null
            is Lexeme.Space, is Lexeme.SpaceRun, is Lexeme.Tab -> i++
            is Lexeme.Newline -> { i++; break }
            else -> return null
        }
    }

    if (count < 3) return null
    // Make sure we consumed everything (or ended at newline/EOF)
    if (i < line.size) return null

    return Token.ThematicBreakLine(markerChar, lineRange(line))
}

// ── ATX heading ─────────────────────────────────────────────────────────

/**
 * Tries to lex an ATX heading: 0–3 spaces, 1–6 `#`s, then space/tab/line-end.
 * Returns a pair of (AtxHeadingMarker, AtxHeadingContent?) or null.
 */
fun tryAtxHeading(line: List<Lexeme>): Pair<Token.AtxHeadingMarker, Token.AtxHeadingContent?>? {
    val (spaces, idx) = countLeadingSpaces(line)
    if (spaces > 3) return null
    if (idx >= line.size) return null

    // Count hashes
    val level: Int
    val afterHashes: Int
    when (val lex = line[idx]) {
        is Lexeme.Hash -> { level = 1; afterHashes = idx + 1 }
        is Lexeme.HashRun -> {
            if (lex.count > 6) return null
            level = lex.count
            afterHashes = idx + 1
        }
        else -> return null
    }

    // After hashes, must have space/tab, newline, or EOF
    if (afterHashes < line.size) {
        when (line[afterHashes]) {
            is Lexeme.Space, is Lexeme.SpaceRun, is Lexeme.Tab, is Lexeme.Newline -> {}
            else -> return null
        }
    }

    val markerRange = SourceRange(
        line[if (idx > 0) 0 else 0].range.start,
        line[afterHashes - 1].range.end,
    )
    val marker = Token.AtxHeadingMarker(level, markerRange)

    // Extract content: skip leading whitespace after hashes, strip trailing
    // whitespace, strip optional closing hash sequence preceded by whitespace
    val afterMarker = afterHashes
    if (isBlankFrom(line, afterMarker)) {
        return marker to null
    }

    // Content lexemes: everything between marker+space and trailing newline
    var contentStart = afterMarker
    // Skip leading spaces/tabs
    while (contentStart < line.size) {
        when (line[contentStart]) {
            is Lexeme.Space, is Lexeme.SpaceRun, is Lexeme.Tab -> contentStart++
            else -> break
        }
    }

    // Find end of content (before newline)
    var contentEnd = line.size
    if (contentEnd > 0 && line[contentEnd - 1] is Lexeme.Newline) contentEnd--

    if (contentStart >= contentEnd) {
        return marker to null
    }

    var content = line.subList(contentStart, contentEnd)

    // Strip trailing spaces
    content = stripTrailing(content).toList()
    if (content.isEmpty()) return marker to null

    // Strip optional closing hash sequence: trailing hashes (possibly with trailing spaces before them)
    content = stripClosingHashes(content)

    if (content.isEmpty()) return marker to null

    val contentRange = SourceRange(content.first().range.start, content.last().range.end)
    return marker to Token.AtxHeadingContent(content, contentRange)
}

/**
 * Strips a trailing closing hash sequence from ATX heading content.
 * The sequence must be preceded by space/tab (or the content is all hashes).
 */
private fun stripClosingHashes(content: List<Lexeme>): List<Lexeme> {
    if (content.isEmpty()) return content

    // Check if last element is hashes
    val last = content.last()
    val hasTrailingHashes = last is Lexeme.Hash || last is Lexeme.HashRun
    if (!hasTrailingHashes) return content

    // Find where trailing hashes start
    var hashStart = content.size - 1
    while (hashStart > 0) {
        val prev = content[hashStart - 1]
        if (prev is Lexeme.Hash || prev is Lexeme.HashRun) {
            hashStart--
        } else {
            break
        }
    }

    // Must be preceded by space/tab (or be the entire content)
    if (hashStart == 0) {
        // Entire content is hashes — this is valid (empty heading)
        return emptyList()
    }
    val preceding = content[hashStart - 1]
    if (preceding !is Lexeme.Space && preceding !is Lexeme.SpaceRun && preceding !is Lexeme.Tab) {
        return content
    }

    // Strip the hashes AND the preceding whitespace
    val trimmed = stripTrailing(content.subList(0, hashStart))
    return trimmed
}

// ── Setext heading underline ────────────────────────────────────────────

/**
 * Tries to lex a setext heading underline: 0–3 spaces, then 1+ `=` or `-`, optional trailing spaces.
 */
fun trySetextUnderline(line: List<Lexeme>): Token.SetextUnderline? {
    val (spaces, idx) = countLeadingSpaces(line)
    if (spaces > 3) return null
    if (idx >= line.size) return null

    val level: Int = when (line[idx]) {
        is Lexeme.Equals, is Lexeme.EqualsRun -> 1
        is Lexeme.Hyphen, is Lexeme.HyphenRun -> 2
        else -> return null
    }

    // Must be all same char (runs of = or -) with optional trailing whitespace
    var i = idx
    while (i < line.size) {
        when (val lex = line[i]) {
            is Lexeme.Equals, is Lexeme.EqualsRun -> if (level != 1) return null else i++
            is Lexeme.Hyphen, is Lexeme.HyphenRun -> if (level != 2) return null else i++
            is Lexeme.Space, is Lexeme.SpaceRun, is Lexeme.Tab -> { i++; break }
            is Lexeme.Newline -> { i++; break }
            else -> return null
        }
    }

    // After the underline chars and optional space, only whitespace/newline allowed
    while (i < line.size) {
        when (line[i]) {
            is Lexeme.Space, is Lexeme.SpaceRun, is Lexeme.Tab, is Lexeme.Newline -> i++
            else -> return null
        }
    }

    // Reconstruct the raw text for orphaned-setext → paragraph fallback
    val text = lexemesToText(line).trimEnd('\n', '\r')
    return Token.SetextUnderline(level, text, lineRange(line))
}

// ── Fenced code block ───────────────────────────────────────────────────

/**
 * Tries to lex a code fence opening: 0–3 spaces, 3+ backticks or tildes.
 * Returns (CodeFenceOpen, CodeFenceInfo?) or null.
 */
fun tryCodeFenceOpen(line: List<Lexeme>): Pair<Token.CodeFenceOpen, Token.CodeFenceInfo?>? {
    val (spaces, idx) = countLeadingSpaces(line)
    if (spaces > 3) return null
    if (idx >= line.size) return null

    val fenceChar: Char
    val fenceLength: Int
    when (val lex = line[idx]) {
        is Lexeme.Backtick -> { fenceChar = '`'; fenceLength = 1 }
        is Lexeme.BacktickRun -> { fenceChar = '`'; fenceLength = lex.count }
        is Lexeme.Tilde -> { fenceChar = '~'; fenceLength = 1 }
        is Lexeme.TildeRun -> { fenceChar = '~'; fenceLength = lex.count }
        else -> return null
    }

    if (fenceLength < 3) return null

    val fenceRange = SourceRange(line.first().range.start, line[idx].range.end)
    val open = Token.CodeFenceOpen(fenceChar, fenceLength, spaces, fenceRange)

    // Info string: everything after fence, trimmed, up to newline
    val afterFence = idx + 1
    if (isBlankFrom(line, afterFence)) {
        return open to null
    }

    // Collect info string lexemes
    var infoEnd = line.size
    if (infoEnd > 0 && line[infoEnd - 1] is Lexeme.Newline) infoEnd--

    val infoLexemes = stripLeading(stripTrailing(line.subList(afterFence, infoEnd)))

    // Backtick fences cannot contain backticks in info string
    if (fenceChar == '`') {
        for (lex in infoLexemes) {
            if (lex is Lexeme.Backtick || lex is Lexeme.BacktickRun) return null
        }
    }

    val infoText = lexemesToText(infoLexemes).trim()
    if (infoText.isEmpty()) return open to null

    val infoRange = SourceRange(infoLexemes.first().range.start, infoLexemes.last().range.end)
    return open to Token.CodeFenceInfo(infoText, infoRange)
}

/**
 * Tries to lex a closing code fence that matches the given opening.
 */
fun tryCodeFenceClose(line: List<Lexeme>, openChar: Char, openLength: Int): Token.CodeFenceClose? {
    val (spaces, idx) = countLeadingSpaces(line)
    if (spaces > 3) return null
    if (idx >= line.size) return null

    val fenceChar: Char
    val fenceLength: Int
    when (val lex = line[idx]) {
        is Lexeme.Backtick -> { fenceChar = '`'; fenceLength = 1 }
        is Lexeme.BacktickRun -> { fenceChar = '`'; fenceLength = lex.count }
        is Lexeme.Tilde -> { fenceChar = '~'; fenceLength = 1 }
        is Lexeme.TildeRun -> { fenceChar = '~'; fenceLength = lex.count }
        else -> return null
    }

    if (fenceChar != openChar) return null
    if (fenceLength < openLength) return null

    // After fence: only whitespace/newline allowed
    if (!isBlankFrom(line, idx + 1)) return null

    return Token.CodeFenceClose(fenceChar, fenceLength, lineRange(line))
}

// ── Indented code ───────────────────────────────────────────────────────

/**
 * Tries to lex an indented code line: 4+ spaces (or 1+ tab) of leading indent.
 * Returns the line with the indent prefix stripped.
 */
fun tryIndentedCodeLine(line: List<Lexeme>): Token.IndentedCodeLine? {
    val (spaces, idx) = countLeadingSpaces(line)
    if (spaces < 4) return null

    // Must have non-whitespace content after the indent
    if (isBlankFrom(line, idx)) return null

    // Strip exactly 4 spaces worth of indent
    val stripped = stripIndent(line, 4)
    val literal = lexemesToText(stripped)
    return Token.IndentedCodeLine(literal, lineRange(line))
}

// ── HTML block detection ────────────────────────────────────────────────

/** HTML block type 6 tag names (case-insensitive). */
private val HTML_BLOCK_6_TAGS = setOf(
    "address", "article", "aside", "base", "basefont", "blockquote", "body",
    "caption", "center", "col", "colgroup", "dd", "details", "dialog", "dir",
    "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form",
    "frame", "frameset", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header",
    "hr", "html", "iframe", "legend", "li", "link", "main", "menu", "menuitem",
    "nav", "noframes", "ol", "optgroup", "option", "p", "param", "search",
    "section", "summary", "table", "tbody", "td", "tfoot", "th", "thead",
    "title", "tr", "track", "ul",
)

/** HTML block type 1 tag names (case-insensitive). */
private val HTML_BLOCK_1_TAGS = setOf("pre", "script", "style", "textarea")

/**
 * Detects the HTML block type (1–7) for a line, or 0 if not an HTML block start.
 * Requires 0–3 leading spaces.
 */
fun detectHtmlBlockType(line: List<Lexeme>): Int {
    val (spaces, idx) = countLeadingSpaces(line)
    if (spaces > 3) return 0

    val text = lexemesToText(line.subList(idx, line.size)).trimEnd('\n', '\r')
    if (!text.startsWith("<")) return 0

    // Type 1: <pre, <script, <style, <textarea (case-insensitive)
    for (tag in HTML_BLOCK_1_TAGS) {
        if (text.startsWith("<$tag", ignoreCase = true) &&
            (text.length == tag.length + 1 ||
                text[tag.length + 1].let { it == ' ' || it == '\t' || it == '>' || it == '\n' || it == '\r' })
        ) return 1
    }

    // Type 2: <!--
    if (text.startsWith("<!--")) return 2

    // Type 3: <?
    if (text.startsWith("<?")) return 3

    // Type 4: <! followed by uppercase letter
    if (text.startsWith("<!") && text.length > 2 && text[2].isUpperCase()) return 4

    // Type 5: <![CDATA[
    if (text.startsWith("<![CDATA[")) return 5

    // Type 6 and 7: need to parse tag name
    val tagMatch = Regex("^</?([a-zA-Z][a-zA-Z0-9-]*)").find(text) ?: return 0
    val tagName = tagMatch.groupValues[1].lowercase()

    // Type 6: block-level tag
    if (tagName in HTML_BLOCK_6_TAGS) return 6

    // Type 7: complete open or close tag on one line
    if (isCompleteHtmlTag(text)) return 7

    return 0
}

private fun isCompleteHtmlTag(text: String): Boolean {
    // Open tag: <tagname attrs...>  or  <tagname attrs.../>
    // Close tag: </tagname>
    val openMatch = Regex("^<[a-zA-Z][a-zA-Z0-9-]*(?:\\s+[a-zA-Z_:][a-zA-Z0-9_.:-]*(?:\\s*=\\s*(?:[^\\s\"'=<>`]+|'[^']*'|\"[^\"]*\"))?)*\\s*/?>\\s*$").matches(text)
    if (openMatch) return true
    val closeMatch = Regex("^</[a-zA-Z][a-zA-Z0-9-]*\\s*>\\s*$").matches(text)
    return closeMatch
}

/**
 * Checks if a line contains the end condition for an HTML block type.
 */
fun htmlBlockEndCondition(text: String, type: Int): Boolean = when (type) {
    1 -> {
        val lower = text.lowercase()
        "</pre>" in lower || "</script>" in lower || "</style>" in lower || "</textarea>" in lower
    }
    2 -> "-->" in text
    3 -> "?>" in text
    4 -> ">" in text
    5 -> "]]>" in text
    6 -> false // blank line (handled externally)
    7 -> false // blank line (handled externally)
    else -> false
}

// ── Helpers ─────────────────────────────────────────────────────────────

/**
 * Converts a list of lexemes back to their source text representation.
 */
fun lexemesToText(lexemes: List<Lexeme>): String = buildString {
    for (lex in lexemes) {
        when (lex) {
            is Lexeme.Hash -> append('#')
            is Lexeme.Asterisk -> append('*')
            is Lexeme.Underscore -> append('_')
            is Lexeme.Backtick -> append('`')
            is Lexeme.Tilde -> append('~')
            is Lexeme.BracketOpen -> append('[')
            is Lexeme.BracketClose -> append(']')
            is Lexeme.ParenOpen -> append('(')
            is Lexeme.ParenClose -> append(')')
            is Lexeme.AngleOpen -> append('<')
            is Lexeme.AngleClose -> append('>')
            is Lexeme.Ampersand -> append('&')
            is Lexeme.Semicolon -> append(';')
            is Lexeme.Backslash -> append('\\')
            is Lexeme.Pipe -> append('|')
            is Lexeme.Equals -> append('=')
            is Lexeme.Hyphen -> append('-')
            is Lexeme.Plus -> append('+')
            is Lexeme.Exclamation -> append('!')
            is Lexeme.Colon -> append(':')
            is Lexeme.Period -> append('.')
            is Lexeme.Quote -> append(lex.char)
            is Lexeme.Space -> append(' ')
            is Lexeme.Tab -> append('\t')
            is Lexeme.Newline -> append('\n')
            is Lexeme.TextRun -> append(lex.text)
            is Lexeme.DigitRun -> append(lex.text)
            is Lexeme.SpaceRun -> repeat(lex.count) { append(' ') }
            is Lexeme.HashRun -> repeat(lex.count) { append('#') }
            is Lexeme.BacktickRun -> repeat(lex.count) { append('`') }
            is Lexeme.TildeRun -> repeat(lex.count) { append('~') }
            is Lexeme.AsteriskRun -> repeat(lex.count) { append('*') }
            is Lexeme.UnderscoreRun -> repeat(lex.count) { append('_') }
            is Lexeme.HyphenRun -> repeat(lex.count) { append('-') }
            is Lexeme.EqualsRun -> repeat(lex.count) { append('=') }
        }
    }
}

/**
 * Strips exactly [count] spaces worth of indent from the front of a line.
 * Tabs count as enough spaces to reach the next tab stop (multiple of 4).
 */
fun stripIndent(line: List<Lexeme>, count: Int): List<Lexeme> {
    var stripped = 0
    var idx = 0
    while (idx < line.size && stripped < count) {
        when (val lex = line[idx]) {
            is Lexeme.Space -> { stripped++; idx++ }
            is Lexeme.SpaceRun -> {
                if (stripped + lex.count <= count) {
                    stripped += lex.count; idx++
                } else {
                    // Partial strip: need to split the SpaceRun
                    val remaining = count - stripped
                    stripped = count
                    val leftover = lex.count - remaining
                    val result = mutableListOf<Lexeme>()
                    if (leftover == 1) {
                        result.add(Lexeme.Space(SourceRange(lex.range.start + remaining, lex.range.end)))
                    } else {
                        result.add(Lexeme.SpaceRun(leftover, SourceRange(lex.range.start + remaining, lex.range.end)))
                    }
                    result.addAll(line.subList(idx + 1, line.size))
                    return result
                }
            }
            is Lexeme.Tab -> {
                val tabWidth = 4 - (stripped % 4)
                if (stripped + tabWidth <= count) {
                    stripped += tabWidth; idx++
                } else {
                    // Tab partially consumed: replace with remaining spaces
                    val consumed = count - stripped
                    val remaining = tabWidth - consumed
                    stripped = count
                    val result = mutableListOf<Lexeme>()
                    if (remaining > 0) {
                        if (remaining == 1) {
                            result.add(Lexeme.Space(SourceRange(lex.range.start, lex.range.end)))
                        } else {
                            result.add(Lexeme.SpaceRun(remaining, SourceRange(lex.range.start, lex.range.end)))
                        }
                    }
                    result.addAll(line.subList(idx + 1, line.size))
                    return result
                }
            }
            else -> break
        }
    }
    return if (idx == 0) line else line.subList(idx, line.size)
}

// ── Block lexer orchestration ───────────────────────────────────────────

/**
 * State machine for block-level lexing.
 */
sealed interface BlockLexState {
    data object Normal : BlockLexState
    data class InFencedCode(val fenceChar: Char, val fenceLength: Int, val indent: Int) : BlockLexState
    data class InHtmlBlock(val type: Int) : BlockLexState
}

/**
 * Lexes a flat list of lexemes into block-level tokens.
 *
 * This is the main entry point for the block lexer. It processes lines
 * sequentially, tracking state for multi-line constructs (fenced code blocks,
 * HTML blocks).
 */
fun blockLex(lexemes: List<Lexeme>): List<Token> {
    val lines = splitLines(lexemes)
    val tokens = mutableListOf<Token>()
    var state: BlockLexState = BlockLexState.Normal
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        when (state) {
            is BlockLexState.InFencedCode -> {
                val close = tryCodeFenceClose(line, state.fenceChar, state.fenceLength)
                if (close != null) {
                    tokens.add(close)
                    state = BlockLexState.Normal
                } else {
                    // Code content line — strip indent up to opening fence indent
                    val stripped = if (state.indent > 0) stripIndent(line, state.indent) else line
                    val literal = lexemesToText(stripped)
                    tokens.add(Token.CodeContent(literal, lineRange(line)))
                }
                i++
            }

            is BlockLexState.InHtmlBlock -> {
                val text = lexemesToText(line)
                val isBlank = tryBlankLine(line) != null

                when (state.type) {
                    1, 2, 3, 4, 5 -> {
                        tokens.add(Token.HtmlBlockLine(text, lineRange(line)))
                        if (htmlBlockEndCondition(text, state.type)) {
                            state = BlockLexState.Normal
                        }
                    }
                    6, 7 -> {
                        if (isBlank) {
                            // Blank line ends type 6/7 — don't consume it
                            state = BlockLexState.Normal
                            continue // re-process this line in Normal state
                        }
                        tokens.add(Token.HtmlBlockLine(text, lineRange(line)))
                    }
                }
                i++
            }

            is BlockLexState.Normal -> {
                // Try each block type in CommonMark precedence order

                // 1. Blank line (but if it has 4+ leading spaces, treat as indented code)
                val blank = tryBlankLine(line)
                if (blank != null) {
                    val (blankSpaces, _) = countLeadingSpaces(line)
                    if (blankSpaces >= 4) {
                        // Blank line with indentation — emit as IndentedCodeLine
                        // so the parser can preserve whitespace in code blocks
                        val stripped = stripIndent(line, 4)
                        val literal = lexemesToText(stripped)
                        tokens.add(Token.IndentedCodeLine(literal, lineRange(line)))
                    } else {
                        tokens.add(blank)
                    }
                    i++
                    continue
                }

                // 2. Thematic break
                val tb = tryThematicBreak(line)
                if (tb != null) {
                    tokens.add(tb)
                    i++
                    continue
                }

                // 3. ATX heading
                val atx = tryAtxHeading(line)
                if (atx != null) {
                    tokens.add(atx.first)
                    if (atx.second != null) tokens.add(atx.second!!)
                    i++
                    continue
                }

                // 4. Fenced code block
                val fence = tryCodeFenceOpen(line)
                if (fence != null) {
                    tokens.add(fence.first)
                    if (fence.second != null) tokens.add(fence.second!!)
                    state = BlockLexState.InFencedCode(fence.first.fenceChar, fence.first.fenceLength, fence.first.indent)
                    i++
                    continue
                }

                // 5. HTML block
                val htmlType = detectHtmlBlockType(line)
                if (htmlType > 0) {
                    val text = lexemesToText(line)
                    tokens.add(Token.HtmlBlockLine(text, lineRange(line)))
                    // Check if start line also contains end condition
                    if (htmlType in 1..5 && htmlBlockEndCondition(text, htmlType)) {
                        // Already ended
                    } else {
                        state = BlockLexState.InHtmlBlock(htmlType)
                    }
                    i++
                    continue
                }

                // 6. Indented code line (cannot interrupt paragraph — handled by parser)
                val indented = tryIndentedCodeLine(line)
                if (indented != null) {
                    tokens.add(indented)
                    i++
                    continue
                }

                // 7. Setext heading underline
                val setext = trySetextUnderline(line)
                if (setext != null) {
                    tokens.add(setext)
                    i++
                    continue
                }

                // 8. Paragraph line (fallback)
                val contentLexemes = extractParagraphContent(line)
                val range = if (contentLexemes.isNotEmpty()) {
                    SourceRange(contentLexemes.first().range.start, line.last().range.end)
                } else {
                    lineRange(line)
                }
                tokens.add(Token.ParagraphLine(contentLexemes, range))
                i++
            }
        }
    }

    return tokens
}

/**
 * Extracts the content lexemes of a paragraph line (strips up to 3 leading spaces and trailing newline).
 */
private fun extractParagraphContent(line: List<Lexeme>): List<Lexeme> {
    val (spaces, idx) = countLeadingSpaces(line)
    // Strip up to 3 leading spaces
    val content = if (spaces <= 3 && idx > 0) line.subList(idx, line.size) else line
    // Remove trailing newline
    return if (content.isNotEmpty() && content.last() is Lexeme.Newline) {
        content.subList(0, content.size - 1)
    } else {
        content
    }
}
