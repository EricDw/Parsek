package parsek.commonmark.parser.inline

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PCodeSpanTest {

    private fun parse(input: String) =
        pCodeSpan<Unit>()(ParserInput.of(input.toList(), Unit))

    // -------------------------------------------------------------------------
    // Basic single/double/triple backtick delimiters
    // -------------------------------------------------------------------------

    @Test
    fun singleBacktick() {
        val result = parse("`foo`")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan("foo"), result.value)
    }

    @Test
    fun doubleBacktick() {
        val result = parse("``foo``")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan("foo"), result.value)
    }

    @Test
    fun tripleBacktick() {
        val result = parse("```foo```")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan("foo"), result.value)
    }

    // -------------------------------------------------------------------------
    // Content that contains backticks of a different length
    // -------------------------------------------------------------------------

    @Test
    fun singleBacktickInsideDouble() {
        // ``foo`bar`` — the single backtick inside is literal content.
        val result = parse("``foo`bar``")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan("foo`bar"), result.value)
    }

    @Test
    fun doubleBacktickInsideSingle() {
        // `foo``bar` — the double backtick inside is literal content.
        val result = parse("`foo``bar`")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan("foo``bar"), result.value)
    }

    @Test
    fun singleBacktickAloneInsideDouble() {
        // `` ` `` — content is " ` " → strip spaces → "`"
        val result = parse("`` ` ``")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan("`"), result.value)
    }

    // -------------------------------------------------------------------------
    // Space-stripping rule
    // -------------------------------------------------------------------------

    @Test
    fun stripOneLeadingTrailingSpace() {
        // ` foo ` → strip leading/trailing → "foo"
        val result = parse("` foo `")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan("foo"), result.value)
    }

    @Test
    fun stripOnlyOneSpace() {
        // `  foo  ` → strip one from each end → " foo "
        val result = parse("`  foo  `")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan(" foo "), result.value)
    }

    @Test
    fun noStripWhenAllSpaces() {
        // `  ` — content is "  " (all spaces) → no strip
        val result = parse("`  `")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan("  "), result.value)
    }

    @Test
    fun noStripWhenOnlyLeadingSpace() {
        // ` foo` — only leading space, no trailing → no strip
        val result = parse("` foo`")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan(" foo"), result.value)
    }

    @Test
    fun noStripWhenOnlyTrailingSpace() {
        // `foo ` — only trailing space, no leading → no strip
        val result = parse("`foo `")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan("foo "), result.value)
    }

    @Test
    fun singleSpaceContent() {
        // ` ` — single space, all spaces → no strip
        val result = parse("` `")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan(" "), result.value)
    }

    // -------------------------------------------------------------------------
    // Line ending normalisation
    // -------------------------------------------------------------------------

    @Test
    fun lineFeedNormalisedToSpace() {
        val result = parse("`foo\nbar`")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan("foo bar"), result.value)
    }

    @Test
    fun crlfNormalisedToSingleSpace() {
        // \r\n counts as one line ending → one space
        val result = parse("`foo\r\nbar`")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan("foo bar"), result.value)
    }

    @Test
    fun crNormalisedToSpace() {
        val result = parse("`foo\rbar`")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan("foo bar"), result.value)
    }

    @Test
    fun lineEndingWithStripRule() {
        // `\nfoo\n` → " foo " → strip → "foo"
        val result = parse("`\nfoo\n`")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(Inline.CodeSpan("foo"), result.value)
    }

    // -------------------------------------------------------------------------
    // nextIndex
    // -------------------------------------------------------------------------

    @Test
    fun nextIndexSingleBacktick() {
        // "`foo`" = 5 characters.
        val result = parse("`foo`")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(5, result.nextIndex)
    }

    @Test
    fun nextIndexDoubleBacktick() {
        // "``foo``" = 7 characters.
        val result = parse("``foo``")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(7, result.nextIndex)
    }

    @Test
    fun doesNotConsumeFollowingChars() {
        val result = parse("`foo`bar")
        assertIs<Success<Char, Inline, Unit>>(result)
        assertEquals(5, result.nextIndex)
        assertEquals(Inline.CodeSpan("foo"), result.value)
    }

    // -------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------

    @Test
    fun failureNoClosingBacktick() {
        assertIs<Failure<Char, Unit>>(parse("`foo"))
    }

    @Test
    fun failureWrongLengthClose() {
        // Opening is ``, closing is ` (wrong length).
        assertIs<Failure<Char, Unit>>(parse("``foo`"))
    }

    @Test
    fun failureEmptyInput() {
        assertIs<Failure<Char, Unit>>(parse(""))
    }

    @Test
    fun failureNoBacktick() {
        assertIs<Failure<Char, Unit>>(parse("foo"))
    }
}
