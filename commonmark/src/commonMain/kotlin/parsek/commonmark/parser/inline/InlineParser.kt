package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Inline
import parsek.pChoice
import parsek.pLabel
import parsek.pMany
import parsek.pMap

// ---------------------------------------------------------------------------
// pDelimiterRun — emphasis delimiter tokeniser
// ---------------------------------------------------------------------------

/**
 * Parses a run of emphasis delimiter characters (`*` or `_`), classifying the
 * run as left-flanking, right-flanking, or both according to the CommonMark
 * flanking rules (§6.2).
 *
 * The character immediately before the run (or start-of-input) and immediately
 * after (or end-of-input) are inspected to determine flanking. The returned
 * [EmphasisToken.DelimiterRun] carries the `canOpen` and `canClose` flags
 * that [processEmphasis] uses for matching.
 *
 * @return a [Parser] that succeeds with [EmphasisToken.DelimiterRun], or fails
 *   if the current character is not `*` or `_`.
 */
private fun <U : Any> pDelimiterRun(): Parser<Char, EmphasisToken, U> =
    Parser { input ->
        val chars = input.input
        val start = input.index

        if (start >= chars.size)
            return@Parser Failure("delimiter run", start, input)

        val delimChar = chars[start]
        if (delimChar != '*' && delimChar != '_')
            return@Parser Failure("delimiter run", start, input)

        var i = start
        while (i < chars.size && chars[i] == delimChar) i++
        val length = i - start

        val charBefore = if (start > 0) chars[start - 1] else null
        val charAfter = if (i < chars.size) chars[i] else null
        val (canOpen, canClose) = classifyDelimiterRun(charBefore, charAfter, delimChar)

        Success(EmphasisToken.DelimiterRun(delimChar, length, canOpen, canClose), i, input)
    }

// ---------------------------------------------------------------------------
// parseInlineContent — recursive inline content parser
// ---------------------------------------------------------------------------

/**
 * Parses a list of characters as inline content, including emphasis
 * post-processing. Used by [pLink] for recursive inline parsing of link text.
 *
 * @param chars the characters to parse.
 * @param userContext the user context threaded through parsing.
 * @param resolveRef the link reference resolver.
 * @return a list of [Inline] nodes, or an empty list if parsing fails.
 */
internal fun <U : Any> parseInlineContent(
    chars: List<Char>,
    userContext: U,
    resolveRef: LinkRefResolver,
): List<Inline> {
    if (chars.isEmpty()) return emptyList()
    val innerInput = ParserInput(chars, 0, userContext)
    val result = pMany(pInlineToken<U>(resolveRef))(innerInput)
    return if (result is Success) processEmphasis(result.value)
    else emptyList()
}

// ---------------------------------------------------------------------------
// pInlineToken — single inline token parser
// ---------------------------------------------------------------------------

/**
 * Parses a single inline token, returning an [EmphasisToken] for emphasis
 * post-processing. Non-delimiter inlines are wrapped in [EmphasisToken.Content];
 * delimiter runs (`*`/`_`) are returned as [EmphasisToken.DelimiterRun].
 *
 * The alternatives are tried in CommonMark precedence order:
 * 1. Backslash escape
 * 2. HTML entity
 * 3. Code span
 * 4. Autolink
 * 5. Raw HTML
 * 6. Line break (hard or soft)
 * 7. Image (`![…]`)
 * 8. Link (`[…]`)
 * 9. Delimiter run (`*` / `_`)
 * 10. Text (fallback)
 *
 * Images are tried before links so that `![` is not mistakenly parsed as a
 * link starting with `!` as literal text.
 */
private fun <U : Any> pInlineToken(
    resolveRef: LinkRefResolver,
): Parser<Char, EmphasisToken, U> {
    val contentParser: (List<Char>, U) -> List<Inline> = { chars, ctx ->
        parseInlineContent(chars, ctx, resolveRef)
    }

    return pChoice(
        pMap(pBackslashEscape<U>()) { EmphasisToken.Content(it) },
        pMap(pHtmlEntity<U>()) { EmphasisToken.Content(it) },
        pMap(pCodeSpan<U>()) { EmphasisToken.Content(it) },
        pMap(pAutolink<U>()) { EmphasisToken.Content(it) },
        pMap(pRawHtml<U>()) { EmphasisToken.Content(it) },
        pMap(pLineBreak<U>()) { EmphasisToken.Content(it) },
        pMap(pImage<U>(resolveRef)) { EmphasisToken.Content(it) },
        pMap(pLink<U>(contentParser, resolveRef)) { EmphasisToken.Content(it) },
        pDelimiterRun(),
        pMap(pText<U>()) { EmphasisToken.Content(it) },
    )
}

// ---------------------------------------------------------------------------
// pInlines — top-level inline entry point
// ---------------------------------------------------------------------------

/**
 * Parses a complete inline content stream into a list of [Inline] nodes.
 *
 * This is the top-level inline entry point that wires all Phase 5 inline
 * parsers together. The parse proceeds in two stages:
 *
 * 1. **Tokenise**: parse the input into a flat list of [EmphasisToken]s
 *    using [pInlineToken], where `*`/`_` runs become
 *    [EmphasisToken.DelimiterRun] entries and everything else becomes
 *    [EmphasisToken.Content].
 *
 * 2. **Emphasis resolution**: [processEmphasis] matches delimiter runs
 *    according to the CommonMark emphasis algorithm (§6.2, rules 1–17)
 *    and produces properly nested [Inline.Emphasis] and
 *    [Inline.StrongEmphasis] wrappers.
 *
 * @param resolveRef a function that resolves a normalised link label to
 *   `(destination, title?)`, or returns `null` if the label is undefined.
 *   Defaults to always returning `null` (no reference resolution).
 *
 * @return a [Parser] that succeeds with a `List<Inline>`, or fails on
 *   empty input.
 */
fun <U : Any> pInlines(
    resolveRef: LinkRefResolver = { null },
): Parser<Char, List<Inline>, U> =
    pLabel(
        pMap(pMany(pInlineToken(resolveRef))) { tokens ->
            processEmphasis(tokens)
        },
        "inlines",
    )
