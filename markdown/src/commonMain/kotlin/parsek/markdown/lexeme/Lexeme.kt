package parsek.markdown.lexeme

/**
 * A half-open source range `[start, end)` in the original character input.
 *
 * Every [Lexeme] carries a [SourceRange] so that downstream stages (lexer,
 * parser, highlighter) can map any token back to exact character positions
 * without source maps or position-shifting.
 */
data class SourceRange(val start: Int, val end: Int) {
    val length: Int get() = end - start
}

/**
 * A context-free lexeme produced by the scanner (Stage 1).
 *
 * Each variant carries a [range] recording its position in the original
 * character stream. The scanner groups runs of identical structural
 * characters (e.g. `***` → [AsteriskRun]) since the Markdown grammar
 * depends on run lengths.
 */
sealed interface Lexeme {
    val range: SourceRange

    // ── Structural single characters ────────────────────────────────────

    data class Hash(override val range: SourceRange) : Lexeme
    data class Asterisk(override val range: SourceRange) : Lexeme
    data class Underscore(override val range: SourceRange) : Lexeme
    data class Backtick(override val range: SourceRange) : Lexeme
    data class Tilde(override val range: SourceRange) : Lexeme
    data class BracketOpen(override val range: SourceRange) : Lexeme
    data class BracketClose(override val range: SourceRange) : Lexeme
    data class ParenOpen(override val range: SourceRange) : Lexeme
    data class ParenClose(override val range: SourceRange) : Lexeme
    data class AngleOpen(override val range: SourceRange) : Lexeme
    data class AngleClose(override val range: SourceRange) : Lexeme
    data class Ampersand(override val range: SourceRange) : Lexeme
    data class Semicolon(override val range: SourceRange) : Lexeme
    data class Backslash(override val range: SourceRange) : Lexeme
    data class Pipe(override val range: SourceRange) : Lexeme
    data class Equals(override val range: SourceRange) : Lexeme
    data class Hyphen(override val range: SourceRange) : Lexeme
    data class Plus(override val range: SourceRange) : Lexeme
    data class Exclamation(override val range: SourceRange) : Lexeme
    data class Colon(override val range: SourceRange) : Lexeme
    data class Period(override val range: SourceRange) : Lexeme
    data class Quote(override val range: SourceRange, val char: Char) : Lexeme

    // ── Whitespace ──────────────────────────────────────────────────────

    data class Space(override val range: SourceRange) : Lexeme
    data class Tab(override val range: SourceRange) : Lexeme
    data class Newline(override val range: SourceRange) : Lexeme

    // ── Runs (grouped identical characters) ─────────────────────────────

    data class TextRun(val text: String, override val range: SourceRange) : Lexeme
    data class DigitRun(val text: String, override val range: SourceRange) : Lexeme
    data class SpaceRun(val count: Int, override val range: SourceRange) : Lexeme
    data class HashRun(val count: Int, override val range: SourceRange) : Lexeme
    data class BacktickRun(val count: Int, override val range: SourceRange) : Lexeme
    data class TildeRun(val count: Int, override val range: SourceRange) : Lexeme
    data class AsteriskRun(val count: Int, override val range: SourceRange) : Lexeme
    data class UnderscoreRun(val count: Int, override val range: SourceRange) : Lexeme
    data class HyphenRun(val count: Int, override val range: SourceRange) : Lexeme
    data class EqualsRun(val count: Int, override val range: SourceRange) : Lexeme
}

/**
 * Reconstructs the original source text from a list of lexemes.
 * Useful for round-trip verification in tests.
 */
fun List<Lexeme>.toSourceText(): String = buildString {
    for (lexeme in this@toSourceText) {
        when (lexeme) {
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
            is Lexeme.Quote -> append(lexeme.char)
            is Lexeme.Space -> append(' ')
            is Lexeme.Tab -> append('\t')
            is Lexeme.Newline -> append('\n')
            is Lexeme.TextRun -> append(lexeme.text)
            is Lexeme.DigitRun -> append(lexeme.text)
            is Lexeme.SpaceRun -> repeat(lexeme.count) { append(' ') }
            is Lexeme.HashRun -> repeat(lexeme.count) { append('#') }
            is Lexeme.BacktickRun -> repeat(lexeme.count) { append('`') }
            is Lexeme.TildeRun -> repeat(lexeme.count) { append('~') }
            is Lexeme.AsteriskRun -> repeat(lexeme.count) { append('*') }
            is Lexeme.UnderscoreRun -> repeat(lexeme.count) { append('_') }
            is Lexeme.HyphenRun -> repeat(lexeme.count) { append('-') }
            is Lexeme.EqualsRun -> repeat(lexeme.count) { append('=') }
        }
    }
}
