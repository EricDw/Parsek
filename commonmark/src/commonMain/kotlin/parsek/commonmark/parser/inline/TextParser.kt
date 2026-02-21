package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.Parser
import parsek.Success
import parsek.commonmark.ast.Inline
import parsek.pLabel

/**
 * Returns `true` if [ch] is a "safe" character that cannot start any other
 * inline parser. Runs of safe characters are batched into a single
 * [Inline.Text] node by [pText].
 */
private fun isSafeChar(ch: Char): Boolean =
    ch != '\\' && ch != '`' && ch != '*' && ch != '_' && ch != '[' &&
        ch != '!' && ch != '<' && ch != '&' && ch != '\n' && ch != '\r'

/**
 * Parses fallback inline text (§6.12).
 *
 * This parser is the lowest-priority alternative in the inline choice chain.
 * It consumes characters that were not claimed by any other inline parser.
 *
 * As an optimisation, consecutive "safe" characters (those that cannot start
 * any inline construct) are batched into a single [Inline.Text] node. When
 * the current character is a special character that no higher-priority parser
 * could handle, exactly one character is consumed.
 *
 * @return a [Parser] that always succeeds with [Inline.Text] (unless at EOF).
 */
fun <U : Any> pText(): Parser<Char, Inline, U> =
    pLabel(
        Parser { input ->
            val chars = input.input
            val start = input.index

            if (start >= chars.size)
                return@Parser Failure("text", start, input)

            // Try to consume a batch of safe characters.
            var i = start
            while (i < chars.size && isSafeChar(chars[i])) i++

            // If the scan stopped at a line ending, backtrack past any
            // trailing spaces/tabs so that pLineBreak can see them and
            // correctly distinguish hard breaks (2+ spaces) from soft breaks.
            if (i > start && i < chars.size && (chars[i] == '\n' || chars[i] == '\r')) {
                var j = i - 1
                while (j >= start && (chars[j] == ' ' || chars[j] == '\t')) j--
                if (j + 1 < i) i = j + 1
            }

            if (i > start) {
                val text = chars.subList(start, i).joinToString("")
                return@Parser Success(Inline.Text(text), i, input)
            }

            // Fallback: consume exactly one character.
            Success(Inline.Text(chars[start].toString()), start + 1, input)
        },
        "text",
    )
