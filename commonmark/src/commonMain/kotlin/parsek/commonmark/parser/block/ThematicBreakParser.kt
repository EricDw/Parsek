package parsek.commonmark.parser.block

import parsek.Failure
import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.pEof
import parsek.pLabel
import parsek.pMany
import parsek.pMap
import parsek.pOr
import parsek.pSatisfy
import parsek.text.pLineEnding
import parsek.text.pSpaceOrTab
import parsek.text.pUpTo3Spaces

/**
 * Parses a CommonMark thematic break.
 *
 * A thematic break consists of:
 * - 0–3 optional leading spaces
 * - Three or more of the same marker character (`-`, `_`, or `*`), with any
 *   number of spaces or tabs optionally interspersed between the markers
 * - No other non-whitespace characters on the line
 * - A line ending or end of input
 *
 * Examples of valid thematic breaks:
 * ```
 * ---
 * * * *
 * ___
 *  - - -
 *   ***
 * ```
 *
 * @return a [Parser] that succeeds with [Block.ThematicBreak] on a valid
 *   thematic break line, or fails otherwise.
 */
fun <U : Any> pThematicBreak(): Parser<Char, Block.ThematicBreak, U> =
    pLabel(
        Parser { input ->
            // 1. Consume 0–3 leading spaces.
            val indentResult = pUpTo3Spaces<U>()(input) as Success
            var idx = indentResult.nextIndex

            // 2. Determine the marker character from the first non-space token.
            val marker = input.input.getOrNull(idx)
                ?: return@Parser Failure("thematic break", idx, input)
            if (marker != '-' && marker != '_' && marker != '*')
                return@Parser Failure("thematic break", idx, input)

            val markerP = pSatisfy<Char, U> { it == marker }

            // A single tail step: optional whitespace then one marker.
            val spacesThenMarker = Parser<Char, Char, U> { pos ->
                val spaces = pMany(pSpaceOrTab<U>())(pos) as Success
                markerP(ParserInput(pos.input, spaces.nextIndex, pos.userContext))
            }

            // 3. Consume the first marker, then greedily consume (spaces* marker)*.
            var markerCount = 0
            when (val r = markerP(ParserInput(input.input, idx, input.userContext))) {
                is Failure -> return@Parser Failure("thematic break", idx, input)
                is Success -> { markerCount++; idx = r.nextIndex }
            }
            while (true) {
                when (val r = spacesThenMarker(ParserInput(input.input, idx, input.userContext))) {
                    is Failure -> break
                    is Success -> { markerCount++; idx = r.nextIndex }
                }
            }
            if (markerCount < 3)
                return@Parser Failure("thematic break", idx, input)

            // 4. Allow trailing spaces/tabs.
            val trailing = pMany(pSpaceOrTab<U>())(
                ParserInput(input.input, idx, input.userContext)
            ) as Success
            idx = trailing.nextIndex

            // 5. Must be followed by a line ending or EOF.
            val lineEnd = pOr(
                pMap(pLineEnding<U>()) { Unit },
                pMap(pEof<Char, U>()) { Unit },
            )(ParserInput(input.input, idx, input.userContext))
            when (lineEnd) {
                is Failure -> return@Parser Failure("thematic break", idx, input)
                is Success -> idx = lineEnd.nextIndex
            }

            Success(Block.ThematicBreak, idx, input)
        },
        "thematic break",
    )
