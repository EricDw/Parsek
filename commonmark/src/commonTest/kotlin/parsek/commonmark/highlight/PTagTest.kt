package parsek.commonmark.highlight

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.pMany1
import parsek.pSatisfy
import parsek.text.pChar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PTagTest {

    private fun input(s: String): ParserInput<Char, SpanSink> =
        ParserInput.of(s.toList(), SpanSink())

    // -------------------------------------------------------------------------
    // Basic behaviour
    // -------------------------------------------------------------------------

    @Test
    fun recordsSpanOnSuccess() {
        val parser = pTag(TokenType.Text, pChar<SpanSink>('a'))
        val result = parser(input("abc"))
        assertIs<Success<Char, Char, SpanSink>>(result)
        assertEquals(listOf(Span(TokenType.Text, 0, 1)), result.input.userContext.spans)
    }

    @Test
    fun doesNotRecordSpanOnFailure() {
        val parser = pTag(TokenType.Text, pChar<SpanSink>('z'))
        val i = input("abc")
        val result = parser(i)
        assertIs<Failure<Char, SpanSink>>(result)
        assertEquals(emptyList(), i.userContext.spans)
    }

    @Test
    fun spanCoversExactConsumedRange() {
        // skip 2 chars manually, then tag the next char
        val i = input("abcd")
        val shifted = ParserInput(i.input, 2, i.userContext)
        val parser = pTag(TokenType.Text, pChar<SpanSink>('c'))
        parser(shifted)
        assertEquals(listOf(Span(TokenType.Text, 2, 3)), i.userContext.spans)
    }

    @Test
    fun spansAccumulateInOrder() {
        val digit  = pTag(TokenType.Text, pSatisfy<Char, SpanSink> { it.isDigit() })
        val letter = pTag(TokenType.HeadingText, pSatisfy<Char, SpanSink> { it.isLetter() })
        val i = input("1a")

        val r1 = digit(i)
        assertIs<Success<Char, Char, SpanSink>>(r1)
        val shifted = ParserInput(i.input, r1.nextIndex, i.userContext)
        letter(shifted)

        assertEquals(
            listOf(
                Span(TokenType.Text, 0, 1),
                Span(TokenType.HeadingText, 1, 2),
            ),
            i.userContext.spans,
        )
    }

    // -------------------------------------------------------------------------
    // Nested tags — inner spans recorded before outer
    // -------------------------------------------------------------------------

    @Test
    fun nestedTagsRecordInnerBeforeOuter() {
        val inner = pTag(TokenType.HeadingMarker, pChar<SpanSink>('a'))
        val outer = pTag(TokenType.HeadingText, pMany1(inner))
        val i = input("aaa")
        outer(i)
        // three inner spans, then one outer span
        assertEquals(
            listOf(
                Span(TokenType.HeadingMarker, 0, 1),
                Span(TokenType.HeadingMarker, 1, 2),
                Span(TokenType.HeadingMarker, 2, 3),
                Span(TokenType.HeadingText,   0, 3),
            ),
            i.userContext.spans,
        )
    }

    // -------------------------------------------------------------------------
    // TokenType variety
    // -------------------------------------------------------------------------

    @Test
    fun recordsCorrectTokenType() {
        val types = listOf(
            TokenType.ThematicBreak,
            TokenType.CodeFence,
            TokenType.LinkDestination,
            TokenType.EmphasisMarker,
        )
        for (type in types) {
            val sink = SpanSink()
            val i = ParserInput.of(listOf('x'), sink)
            pTag(type, pChar<SpanSink>('x'))(i)
            assertEquals(type, sink.spans.single().type)
        }
    }
}
