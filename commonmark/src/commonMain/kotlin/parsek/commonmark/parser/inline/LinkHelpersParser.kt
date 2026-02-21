package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.Parser
import parsek.Success
import parsek.commonmark.parser.block.normalizeLinkLabel
import parsek.commonmark.parser.block.parseLinkDestination
import parsek.commonmark.parser.block.parseLinkLabel
import parsek.commonmark.parser.block.parseLinkTitle
import parsek.pLabel

// ---------------------------------------------------------------------------
// pLinkDestination
// ---------------------------------------------------------------------------

/**
 * Parses a CommonMark link destination (§4.7).
 *
 * Two forms are recognised:
 * - **Angle-bracket**: `<content>` — no line endings or unescaped `<`/`>`;
 *   may be empty. The brackets are consumed but not stored.
 * - **Bare**: a non-empty sequence of characters without ASCII spaces or
 *   control characters; unescaped parentheses must be balanced.
 *
 * Backslash escapes in both forms are processed (the backslash is stripped).
 *
 * @return a [Parser] that succeeds with the destination [String], or fails.
 */
fun <U : Any> pLinkDestination(): Parser<Char, String, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            val start = input.index
            val (destination, nextIdx) = parseLinkDestination(chars, start)
                ?: return@Parser Failure("link destination", start, input)
            Success(destination, nextIdx, input)
        },
        "link destination",
    )

// ---------------------------------------------------------------------------
// pLinkTitle
// ---------------------------------------------------------------------------

/**
 * Parses a CommonMark link title (§4.7).
 *
 * Three delimiter forms are recognised:
 * - `"content"` — no unescaped `"`.
 * - `'content'` — no unescaped `'`.
 * - `(content)` — no unescaped `(` or `)`.
 *
 * A blank line inside any form causes the parse to fail. Backslash escapes
 * are processed (the backslash is stripped). The delimiters are consumed
 * but not stored.
 *
 * @return a [Parser] that succeeds with the title [String], or fails.
 */
fun <U : Any> pLinkTitle(): Parser<Char, String, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            val start = input.index
            val (title, nextIdx) = parseLinkTitle(chars, start)
                ?: return@Parser Failure("link title", start, input)
            Success(title, nextIdx, input)
        },
        "link title",
    )

// ---------------------------------------------------------------------------
// pLinkLabel
// ---------------------------------------------------------------------------

/**
 * Parses a CommonMark link label `[content]` (§4.7).
 *
 * Rules:
 * - Content must not contain unescaped `[` or `]`.
 * - At most 999 characters (not counting brackets).
 * - The label must be non-blank after normalisation.
 *
 * The returned string is the **normalised** label: Unicode case-folded
 * (lowercase), whitespace runs collapsed to a single space, and leading/
 * trailing whitespace trimmed. This form is suitable for use as a reference
 * map lookup key.
 *
 * @return a [Parser] that succeeds with the normalised label [String], or fails.
 */
fun <U : Any> pLinkLabel(): Parser<Char, String, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            val start = input.index
            val (rawLabel, nextIdx) = parseLinkLabel(chars, start)
                ?: return@Parser Failure("link label", start, input)
            val normalizedLabel = normalizeLinkLabel(rawLabel)
            if (normalizedLabel.isBlank())
                return@Parser Failure("link label", start, input)
            Success(normalizedLabel, nextIdx, input)
        },
        "link label",
    )
