package parsek.commonmark.parser.inline

import parsek.commonmark.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals

class PEmphasisTest {

    // =====================================================================
    // classifyDelimiterRun
    // =====================================================================

    // -------------------------------------------------------------------------
    // Left-flanking detection
    // -------------------------------------------------------------------------

    @Test
    fun leftFlankingAtStartFollowedByLetter() {
        // Start of input (*foo) → canOpen=true, canClose=false
        val (canOpen, canClose) = classifyDelimiterRun(null, 'f', '*')
        assertEquals(true, canOpen)
        assertEquals(false, canClose)
    }

    @Test
    fun notLeftFlankingFollowedBySpace() {
        // (foo * bar) — '*' followed by ' '
        val (canOpen, _) = classifyDelimiterRun('o', ' ', '*')
        assertEquals(false, canOpen)
    }

    // -------------------------------------------------------------------------
    // Right-flanking detection
    // -------------------------------------------------------------------------

    @Test
    fun rightFlankingAtEndPrecededByLetter() {
        // (foo*) → canOpen=false, canClose=true
        val (canOpen, canClose) = classifyDelimiterRun('o', null, '*')
        assertEquals(false, canOpen)
        assertEquals(true, canClose)
    }

    @Test
    fun notRightFlankingPrecededBySpace() {
        val (_, canClose) = classifyDelimiterRun(' ', 'f', '*')
        assertEquals(false, canClose)
    }

    // -------------------------------------------------------------------------
    // Both flanking
    // -------------------------------------------------------------------------

    @Test
    fun bothFlankingStar() {
        // (a*b) — both sides are non-whitespace, non-punctuation
        val (canOpen, canClose) = classifyDelimiterRun('a', 'b', '*')
        assertEquals(true, canOpen)
        assertEquals(true, canClose)
    }

    // -------------------------------------------------------------------------
    // Underscore-specific rules
    // -------------------------------------------------------------------------

    @Test
    fun underscoreBothFlankingPrecededByPunctuation() {
        // (."_"a) — left-flanking, right-flanking, preceded by punctuation
        // canOpen = leftFlanking AND (!rightFlanking OR precededByPunct) = true AND (false OR true) = true
        // canClose = rightFlanking AND (!leftFlanking OR followedByPunct) = true AND (false OR false) = false
        val (canOpen, canClose) = classifyDelimiterRun('.', 'a', '_')
        assertEquals(true, canOpen)
        assertEquals(false, canClose)
    }

    @Test
    fun underscoreBothFlankingFollowedByPunctuation() {
        // (a_.) — left-flanking, right-flanking, followed by punctuation
        // canOpen = leftFlanking AND (!rightFlanking OR precededByPunct) = true AND (false OR false) = false
        // canClose = rightFlanking AND (!leftFlanking OR followedByPunct) = true AND (false OR true) = true
        val (canOpen, canClose) = classifyDelimiterRun('a', '.', '_')
        assertEquals(false, canOpen)
        assertEquals(true, canClose)
    }

    @Test
    fun underscoreLeftFlankingNotRightFlanking() {
        // Start of input → preceded by whitespace → not right-flanking
        // (_foo) → canOpen=true, canClose=false
        val (canOpen, canClose) = classifyDelimiterRun(null, 'f', '_')
        assertEquals(true, canOpen)
        assertEquals(false, canClose)
    }

    @Test
    fun underscoreIntraword() {
        // (a_b) — both flanking, neither preceded nor followed by punctuation
        // canOpen = left AND (!right OR precededByPunct) = true AND (false OR false) = false
        // canClose = right AND (!left OR followedByPunct) = true AND (false OR false) = false
        val (canOpen, canClose) = classifyDelimiterRun('a', 'b', '_')
        assertEquals(false, canOpen)
        assertEquals(false, canClose)
    }

    // =====================================================================
    // processEmphasis
    // =====================================================================

    /** Shorthand for creating a content token. */
    private fun c(text: String) = EmphasisToken.Content(Inline.Text(text))

    /** Shorthand for creating a delimiter run token. */
    private fun d(
        char: Char,
        length: Int,
        canOpen: Boolean,
        canClose: Boolean,
    ) = EmphasisToken.DelimiterRun(char, length, canOpen, canClose)

    // -------------------------------------------------------------------------
    // Basic emphasis
    // -------------------------------------------------------------------------

    @Test
    fun singleStarEmphasis() {
        // *foo* → Emphasis([Text("foo")])
        val tokens = listOf(
            d('*', 1, canOpen = true, canClose = false),
            c("foo"),
            d('*', 1, canOpen = false, canClose = true),
        )
        val result = processEmphasis(tokens)
        assertEquals(listOf(Inline.Emphasis(listOf(Inline.Text("foo")))), result)
    }

    @Test
    fun singleUnderscoreEmphasis() {
        // _foo_ → Emphasis([Text("foo")])
        val tokens = listOf(
            d('_', 1, canOpen = true, canClose = false),
            c("foo"),
            d('_', 1, canOpen = false, canClose = true),
        )
        val result = processEmphasis(tokens)
        assertEquals(listOf(Inline.Emphasis(listOf(Inline.Text("foo")))), result)
    }

    // -------------------------------------------------------------------------
    // Basic strong emphasis
    // -------------------------------------------------------------------------

    @Test
    fun doubleStarStrong() {
        // **foo** → StrongEmphasis([Text("foo")])
        val tokens = listOf(
            d('*', 2, canOpen = true, canClose = false),
            c("foo"),
            d('*', 2, canOpen = false, canClose = true),
        )
        val result = processEmphasis(tokens)
        assertEquals(listOf(Inline.StrongEmphasis(listOf(Inline.Text("foo")))), result)
    }

    @Test
    fun doubleUnderscoreStrong() {
        // __foo__ → StrongEmphasis([Text("foo")])
        val tokens = listOf(
            d('_', 2, canOpen = true, canClose = false),
            c("foo"),
            d('_', 2, canOpen = false, canClose = true),
        )
        val result = processEmphasis(tokens)
        assertEquals(listOf(Inline.StrongEmphasis(listOf(Inline.Text("foo")))), result)
    }

    // -------------------------------------------------------------------------
    // Combined emphasis and strong: ***foo***
    // -------------------------------------------------------------------------

    @Test
    fun tripleStarCombined() {
        // ***foo*** → Emphasis([StrongEmphasis([Text("foo")])])
        val tokens = listOf(
            d('*', 3, canOpen = true, canClose = false),
            c("foo"),
            d('*', 3, canOpen = false, canClose = true),
        )
        val result = processEmphasis(tokens)
        assertEquals(
            listOf(Inline.Emphasis(listOf(Inline.StrongEmphasis(listOf(Inline.Text("foo")))))),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Nested emphasis: *foo **bar** baz*
    // -------------------------------------------------------------------------

    @Test
    fun nestedStrongInsideEmphasis() {
        // *foo **bar** baz* → Emphasis([Text("foo "), StrongEmphasis([Text("bar")]), Text(" baz")])
        val tokens = listOf(
            d('*', 1, canOpen = true, canClose = false),
            c("foo "),
            d('*', 2, canOpen = true, canClose = false),
            c("bar"),
            d('*', 2, canOpen = false, canClose = true),
            c(" baz"),
            d('*', 1, canOpen = false, canClose = true),
        )
        val result = processEmphasis(tokens)
        assertEquals(
            listOf(
                Inline.Emphasis(
                    listOf(
                        Inline.Text("foo "),
                        Inline.StrongEmphasis(listOf(Inline.Text("bar"))),
                        Inline.Text(" baz"),
                    ),
                ),
            ),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Rule-of-3 prevention
    // -------------------------------------------------------------------------

    @Test
    fun ruleOf3PreventsMatch() {
        // When closer can both open and close, and sum of original lengths
        // is a multiple of 3 (but not both individually), the match is skipped.
        //
        // Tokens: *opener(1) **both(2,closer+opener) — sum = 1+2 = 3 → no match
        val tokens = listOf(
            d('*', 1, canOpen = true, canClose = false),
            c("foo"),
            d('*', 2, canOpen = true, canClose = true),
            c("bar"),
        )
        val result = processEmphasis(tokens)
        // No match → all delimiters become text
        assertEquals(
            listOf(
                Inline.Text("*"),
                Inline.Text("foo"),
                Inline.Text("**"),
                Inline.Text("bar"),
            ),
            result,
        )
    }

    @Test
    fun ruleOf3AllowsWhenBothMultiplesOf3() {
        // Sum = 3+3 = 6, multiple of 3, but both are multiples of 3 → allowed
        val tokens = listOf(
            d('*', 3, canOpen = true, canClose = true),
            c("foo"),
            d('*', 3, canOpen = true, canClose = true),
        )
        val result = processEmphasis(tokens)
        // Should match (strong then emphasis, wrapping "foo")
        assertEquals(
            listOf(Inline.Emphasis(listOf(Inline.StrongEmphasis(listOf(Inline.Text("foo")))))),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Spec example: *foo**bar**baz*
    // -------------------------------------------------------------------------

    @Test
    fun specExampleNestedStrong() {
        // *foo**bar**baz* → Emphasis([Text("foo"), StrongEmphasis([Text("bar")]), Text("baz")])
        // D1(*,1,open) D2(**,2,both) D3(**,2,both) D4(*,1,close)
        // D2+D3 match (sum=4, not mult of 3) → Strong([Text("bar")])
        // Then D1+D4 match → Emphasis wrapping everything
        val tokens = listOf(
            d('*', 1, canOpen = true, canClose = false),
            c("foo"),
            d('*', 2, canOpen = true, canClose = true),
            c("bar"),
            d('*', 2, canOpen = true, canClose = true),
            c("baz"),
            d('*', 1, canOpen = false, canClose = true),
        )
        val result = processEmphasis(tokens)
        assertEquals(
            listOf(
                Inline.Emphasis(
                    listOf(
                        Inline.Text("foo"),
                        Inline.StrongEmphasis(listOf(Inline.Text("bar"))),
                        Inline.Text("baz"),
                    ),
                ),
            ),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Unmatched delimiters become text
    // -------------------------------------------------------------------------

    @Test
    fun unmatchedOpener() {
        // *foo → Text("*"), Text("foo")
        val tokens = listOf(
            d('*', 1, canOpen = true, canClose = false),
            c("foo"),
        )
        val result = processEmphasis(tokens)
        assertEquals(
            listOf(Inline.Text("*"), Inline.Text("foo")),
            result,
        )
    }

    @Test
    fun unmatchedCloser() {
        // foo* → Text("foo"), Text("*")
        val tokens = listOf(
            c("foo"),
            d('*', 1, canOpen = false, canClose = true),
        )
        val result = processEmphasis(tokens)
        assertEquals(
            listOf(Inline.Text("foo"), Inline.Text("*")),
            result,
        )
    }

    @Test
    fun mismatchedCharacters() {
        // *foo_ — different chars, no match
        val tokens = listOf(
            d('*', 1, canOpen = true, canClose = false),
            c("foo"),
            d('_', 1, canOpen = false, canClose = true),
        )
        val result = processEmphasis(tokens)
        assertEquals(
            listOf(Inline.Text("*"), Inline.Text("foo"), Inline.Text("_")),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // No delimiters — content passes through unchanged
    // -------------------------------------------------------------------------

    @Test
    fun noDelimiters() {
        val tokens = listOf(c("hello"), c(" "), c("world"))
        val result = processEmphasis(tokens)
        assertEquals(
            listOf(Inline.Text("hello"), Inline.Text(" "), Inline.Text("world")),
            result,
        )
    }

    @Test
    fun emptyInput() {
        assertEquals(emptyList(), processEmphasis(emptyList()))
    }

    // -------------------------------------------------------------------------
    // Multiple independent emphasis spans
    // -------------------------------------------------------------------------

    @Test
    fun twoSeparateEmphasisSpans() {
        // *foo* bar *baz*
        val tokens = listOf(
            d('*', 1, canOpen = true, canClose = false),
            c("foo"),
            d('*', 1, canOpen = false, canClose = true),
            c(" bar "),
            d('*', 1, canOpen = true, canClose = false),
            c("baz"),
            d('*', 1, canOpen = false, canClose = true),
        )
        val result = processEmphasis(tokens)
        assertEquals(
            listOf(
                Inline.Emphasis(listOf(Inline.Text("foo"))),
                Inline.Text(" bar "),
                Inline.Emphasis(listOf(Inline.Text("baz"))),
            ),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Content with existing inline nodes (not just text)
    // -------------------------------------------------------------------------

    @Test
    fun emphasisWrappingCodeSpan() {
        // *`code`* → Emphasis([CodeSpan("code")])
        val tokens = listOf(
            d('*', 1, canOpen = true, canClose = false),
            EmphasisToken.Content(Inline.CodeSpan("code")),
            d('*', 1, canOpen = false, canClose = true),
        )
        val result = processEmphasis(tokens)
        assertEquals(
            listOf(Inline.Emphasis(listOf(Inline.CodeSpan("code")))),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Partial delimiter consumption
    // -------------------------------------------------------------------------

    @Test
    fun partialConsumptionOfDelimiters() {
        // ****foo**** → StrongEmphasis([StrongEmphasis([Text("foo")])])
        // Opener: 4 stars, Closer: 4 stars
        // First match uses 2 → Strong, remaining 2+2
        // Second match uses 2 → Strong wrapping the first Strong
        val tokens = listOf(
            d('*', 4, canOpen = true, canClose = false),
            c("foo"),
            d('*', 4, canOpen = false, canClose = true),
        )
        val result = processEmphasis(tokens)
        assertEquals(
            listOf(
                Inline.StrongEmphasis(
                    listOf(Inline.StrongEmphasis(listOf(Inline.Text("foo")))),
                ),
            ),
            result,
        )
    }

    @Test
    fun fiveStars() {
        // *****foo***** → Emphasis([StrongEmphasis([StrongEmphasis([Text("foo")])])])
        // 5 stars: use 2 → Strong, 3 remaining
        // 3 remaining: use 2 → Strong, 1 remaining
        // 1 remaining: use 1 → Emphasis
        val tokens = listOf(
            d('*', 5, canOpen = true, canClose = false),
            c("foo"),
            d('*', 5, canOpen = false, canClose = true),
        )
        val result = processEmphasis(tokens)
        assertEquals(
            listOf(
                Inline.Emphasis(
                    listOf(
                        Inline.StrongEmphasis(
                            listOf(Inline.StrongEmphasis(listOf(Inline.Text("foo")))),
                        ),
                    ),
                ),
            ),
            result,
        )
    }

    // -------------------------------------------------------------------------
    // Inner unmatched delimiters become text
    // -------------------------------------------------------------------------

    @Test
    fun innerUnmatchedDelimiterBecomesText() {
        // *foo _bar* baz_ — the inner _ is consumed as text inside the * emphasis
        val tokens = listOf(
            d('*', 1, canOpen = true, canClose = false),
            c("foo "),
            d('_', 1, canOpen = true, canClose = false),
            c("bar"),
            d('*', 1, canOpen = false, canClose = true),
            c(" baz"),
            d('_', 1, canOpen = false, canClose = true),
        )
        val result = processEmphasis(tokens)
        assertEquals(
            listOf(
                Inline.Emphasis(listOf(Inline.Text("foo "), Inline.Text("_"), Inline.Text("bar"))),
                Inline.Text(" baz"),
                Inline.Text("_"),
            ),
            result,
        )
    }
}
