package parsek.markdown2.scanner

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.markdown2.lexeme.Lexeme
import parsek.markdown2.lexeme.SourceRange

/**
 * Parsers for individual characters and character runs.
 *
 * Each parser reads `Char` tokens and produces a [Lexeme] with the correct
 * [SourceRange]. Run parsers greedily consume consecutive identical characters
 * (2+) into a single run lexeme; when only one character is present the
 * corresponding single-character lexeme is produced instead.
 */

// ── Helpers ─────────────────────────────────────────────────────────────

/**
 * Creates a parser that matches a single [char] and produces a [Lexeme]
 * via [factory]. If two or more consecutive [char]s are present, produces
 * a run lexeme via [runFactory] instead.
 */
internal fun <U : Any> pRunnable(
    char: Char,
    factory: (SourceRange) -> Lexeme,
    runFactory: (Int, SourceRange) -> Lexeme,
): Parser<Char, Lexeme, U> = Parser { input ->
    if (input.isAtEnd || input.current() != char) {
        return@Parser Failure("Expected '$char'", input.index, input)
    }
    val start = input.index
    var pos = start + 1
    while (pos < input.input.size && input.input[pos] == char) {
        pos++
    }
    val count = pos - start
    val range = SourceRange(start, pos)
    val lexeme = if (count == 1) factory(range) else runFactory(count, range)
    Success(lexeme, pos, input)
}

/**
 * Creates a parser that matches exactly one [char] and produces a [Lexeme]
 * via [factory]. Does not group into runs.
 */
internal fun <U : Any> pSingleChar(
    char: Char,
    factory: (SourceRange) -> Lexeme,
): Parser<Char, Lexeme, U> = Parser { input ->
    if (input.isAtEnd || input.current() != char) {
        Failure("Expected '$char'", input.index, input)
    } else {
        val range = SourceRange(input.index, input.index + 1)
        Success(factory(range), input.index + 1, input)
    }
}

// ── Runnable structural characters ──────────────────────────────────────

internal fun <U : Any> pHashLexeme(): Parser<Char, Lexeme, U> =
    pRunnable('#', { Lexeme.Hash(it) }, { n, r -> Lexeme.HashRun(n, r) })

internal fun <U : Any> pAsteriskLexeme(): Parser<Char, Lexeme, U> =
    pRunnable('*', { Lexeme.Asterisk(it) }, { n, r -> Lexeme.AsteriskRun(n, r) })

internal fun <U : Any> pUnderscoreLexeme(): Parser<Char, Lexeme, U> =
    pRunnable('_', { Lexeme.Underscore(it) }, { n, r -> Lexeme.UnderscoreRun(n, r) })

internal fun <U : Any> pBacktickLexeme(): Parser<Char, Lexeme, U> =
    pRunnable('`', { Lexeme.Backtick(it) }, { n, r -> Lexeme.BacktickRun(n, r) })

internal fun <U : Any> pTildeLexeme(): Parser<Char, Lexeme, U> =
    pRunnable('~', { Lexeme.Tilde(it) }, { n, r -> Lexeme.TildeRun(n, r) })

internal fun <U : Any> pHyphenLexeme(): Parser<Char, Lexeme, U> =
    pRunnable('-', { Lexeme.Hyphen(it) }, { n, r -> Lexeme.HyphenRun(n, r) })

internal fun <U : Any> pEqualsLexeme(): Parser<Char, Lexeme, U> =
    pRunnable('=', { Lexeme.Equals(it) }, { n, r -> Lexeme.EqualsRun(n, r) })

// ── Runnable whitespace ─────────────────────────────────────────────────

internal fun <U : Any> pSpaceLexeme(): Parser<Char, Lexeme, U> =
    pRunnable(' ', { Lexeme.Space(it) }, { n, r -> Lexeme.SpaceRun(n, r) })

// ── Non-runnable structural characters ──────────────────────────────────

internal fun <U : Any> pBracketOpenLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar('[') { Lexeme.BracketOpen(it) }

internal fun <U : Any> pBracketCloseLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar(']') { Lexeme.BracketClose(it) }

internal fun <U : Any> pParenOpenLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar('(') { Lexeme.ParenOpen(it) }

internal fun <U : Any> pParenCloseLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar(')') { Lexeme.ParenClose(it) }

internal fun <U : Any> pAngleOpenLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar('<') { Lexeme.AngleOpen(it) }

internal fun <U : Any> pAngleCloseLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar('>') { Lexeme.AngleClose(it) }

internal fun <U : Any> pAmpersandLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar('&') { Lexeme.Ampersand(it) }

internal fun <U : Any> pSemicolonLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar(';') { Lexeme.Semicolon(it) }

internal fun <U : Any> pBackslashLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar('\\') { Lexeme.Backslash(it) }

internal fun <U : Any> pPipeLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar('|') { Lexeme.Pipe(it) }

internal fun <U : Any> pPlusLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar('+') { Lexeme.Plus(it) }

internal fun <U : Any> pExclamationLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar('!') { Lexeme.Exclamation(it) }

internal fun <U : Any> pColonLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar(':') { Lexeme.Colon(it) }

internal fun <U : Any> pPeriodLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar('.') { Lexeme.Period(it) }

// ── Quote characters ────────────────────────────────────────────────────

internal fun <U : Any> pQuoteLexeme(): Parser<Char, Lexeme, U> = Parser { input ->
    if (input.isAtEnd) return@Parser Failure("Expected quote", input.index, input)
    val ch = input.current()
    if (ch == '"' || ch == '\'') {
        val range = SourceRange(input.index, input.index + 1)
        Success(Lexeme.Quote(range, ch), input.index + 1, input)
    } else {
        Failure("Expected quote", input.index, input)
    }
}

// ── Whitespace (non-runnable) ───────────────────────────────────────────

internal fun <U : Any> pTabLexeme(): Parser<Char, Lexeme, U> =
    pSingleChar('\t') { Lexeme.Tab(it) }

internal fun <U : Any> pNewlineLexeme(): Parser<Char, Lexeme, U> = Parser { input ->
    if (input.isAtEnd) return@Parser Failure("Expected newline", input.index, input)
    val ch = input.current()
    if (ch == '\n') {
        val range = SourceRange(input.index, input.index + 1)
        Success(Lexeme.Newline(range), input.index + 1, input)
    } else if (ch == '\r') {
        // Normalize \r\n and \r to a single Newline lexeme
        val end = if (input.index + 1 < input.input.size && input.input[input.index + 1] == '\n') {
            input.index + 2
        } else {
            input.index + 1
        }
        val range = SourceRange(input.index, end)
        Success(Lexeme.Newline(range), end, input)
    } else {
        Failure("Expected newline", input.index, input)
    }
}

// ── Digit runs ──────────────────────────────────────────────────────────

internal fun <U : Any> pDigitRunLexeme(): Parser<Char, Lexeme, U> = Parser { input ->
    if (input.isAtEnd || !input.current().isDigit()) {
        return@Parser Failure("Expected digit", input.index, input)
    }
    val start = input.index
    var pos = start
    while (pos < input.input.size && input.input[pos].isDigit()) {
        pos++
    }
    val text = buildString {
        for (i in start until pos) append(input.input[i])
    }
    val range = SourceRange(start, pos)
    Success(Lexeme.DigitRun(text, range), pos, input)
}

// ── Text runs (fallback for non-structural characters) ──────────────────

/** Set of characters that are NOT grouped into text runs. */
private val STRUCTURAL_CHARS = setOf(
    '#', '*', '_', '`', '~', '[', ']', '(', ')', '<', '>', '&', ';',
    '\\', '|', '=', '-', '+', '!', ':', '.', '"', '\'',
    ' ', '\t', '\n', '\r',
)

internal fun <U : Any> pTextRunLexeme(): Parser<Char, Lexeme, U> = Parser { input ->
    if (input.isAtEnd) return@Parser Failure("Expected text", input.index, input)
    val ch = input.current()
    if (ch in STRUCTURAL_CHARS || ch.isDigit()) {
        return@Parser Failure("Expected text character", input.index, input)
    }
    val start = input.index
    var pos = start
    while (pos < input.input.size) {
        val c = input.input[pos]
        if (c in STRUCTURAL_CHARS || c.isDigit()) break
        pos++
    }
    val text = buildString {
        for (i in start until pos) append(input.input[i])
    }
    val range = SourceRange(start, pos)
    Success(Lexeme.TextRun(text, range), pos, input)
}
