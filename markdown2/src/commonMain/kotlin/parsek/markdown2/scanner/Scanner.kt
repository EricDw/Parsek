package parsek.markdown2.scanner

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.pChoice
import parsek.pMany
import parsek.markdown2.lexeme.Lexeme

/**
 * A single lexeme parser: tries each character/run parser in priority order.
 *
 * Priority:
 * 1. Newline (handles \r\n normalization)
 * 2. Runnable structural chars (hash, asterisk, underscore, backtick, tilde, hyphen, equals)
 * 3. Runnable whitespace (space)
 * 4. Non-runnable whitespace (tab)
 * 5. Non-runnable structural chars
 * 6. Quote chars
 * 7. Digit runs
 * 8. Text runs (fallback)
 */
fun <U : Any> pLexeme(): Parser<Char, Lexeme, U> = pChoice(
    // Newline first (handles \r\n)
    pNewlineLexeme(),
    // Runnable structural
    pHashLexeme(),
    pAsteriskLexeme(),
    pUnderscoreLexeme(),
    pBacktickLexeme(),
    pTildeLexeme(),
    pHyphenLexeme(),
    pEqualsLexeme(),
    // Runnable whitespace
    pSpaceLexeme(),
    // Non-runnable whitespace
    pTabLexeme(),
    // Non-runnable structural
    pBracketOpenLexeme(),
    pBracketCloseLexeme(),
    pParenOpenLexeme(),
    pParenCloseLexeme(),
    pAngleOpenLexeme(),
    pAngleCloseLexeme(),
    pAmpersandLexeme(),
    pSemicolonLexeme(),
    pBackslashLexeme(),
    pPipeLexeme(),
    pPlusLexeme(),
    pExclamationLexeme(),
    pColonLexeme(),
    pPeriodLexeme(),
    // Quotes
    pQuoteLexeme(),
    // Digits
    pDigitRunLexeme(),
    // Text (fallback)
    pTextRunLexeme(),
)

/**
 * Scans an entire document into a flat list of [Lexeme]s.
 *
 * Every character in the input is consumed into exactly one lexeme.
 * Each lexeme carries a [SourceRange][parsek.markdown2.lexeme.SourceRange]
 * recording its position in the original character stream.
 *
 * Usage:
 * ```kotlin
 * val input = ParserInput.of("# Hello\n".toList(), Unit)
 * val result = pScanDocument<Unit>()(input)
 * // Success([HashRun(1,..), Space(..), TextRun("Hello",..), Newline(..)], ...)
 * ```
 */
fun <U : Any> pScanDocument(): Parser<Char, List<Lexeme>, U> = pMany(pLexeme())

/**
 * Convenience function: scans a string into a list of lexemes.
 *
 * @return the list of lexemes, or throws [IllegalStateException] if scanning fails
 *   (which should not happen for any valid string input since the scanner is exhaustive).
 */
fun scanDocument(text: String): List<Lexeme> {
    val input = ParserInput.of(text.toList(), Unit)
    return when (val result = pScanDocument<Unit>()(input)) {
        is Success -> result.value
        is Failure -> error("Scanner failed at index ${result.index}: ${result.message}")
    }
}
