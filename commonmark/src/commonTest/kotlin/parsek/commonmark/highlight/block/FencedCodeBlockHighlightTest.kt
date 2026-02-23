package parsek.commonmark.highlight.block

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.highlight.Span
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FencedCodeBlockHighlightTest {

    private fun highlight(s: String): List<Span> {
        val sink = SpanSink()
        val input = ParserInput.of(s.toList(), sink)
        val result = pFencedCodeBlockHighlight()(input)
        assertIs<Success<*, *, *>>(result)
        return sink.spans
    }

    @Test
    fun simpleBacktickFence() {
        // "```\ncode\n```\n"
        val spans = highlight("```\ncode\n```\n")
        assertEquals(
            listOf(
                Span(TokenType.CodeFence, 0, 3),       // ```
                Span(TokenType.CodeContent, 4, 9),     // code\n
                Span(TokenType.CodeFence, 9, 13),      // ```\n
            ),
            spans,
        )
    }

    @Test
    fun fenceWithInfoString() {
        // "```kotlin\nval x = 1\n```\n"
        // indices: ``` = 0-2, kotlin = 3-8, \n = 9, val x = 1 = 10-18, \n = 19, ``` = 20-22, \n = 23
        val spans = highlight("```kotlin\nval x = 1\n```\n")
        assertEquals(
            listOf(
                Span(TokenType.CodeFence, 0, 3),       // ```
                Span(TokenType.CodeInfo, 3, 9),        // kotlin
                Span(TokenType.CodeContent, 10, 20),   // val x = 1\n
                Span(TokenType.CodeFence, 20, 24),     // ```\n
            ),
            spans,
        )
    }

    @Test
    fun tildeFence() {
        // "~~~\nhi\n~~~\n"
        val spans = highlight("~~~\nhi\n~~~\n")
        assertEquals(
            listOf(
                Span(TokenType.CodeFence, 0, 3),      // ~~~
                Span(TokenType.CodeContent, 4, 7),    // hi\n
                Span(TokenType.CodeFence, 7, 11),     // ~~~\n
            ),
            spans,
        )
    }

    @Test
    fun unclosedFence() {
        // "```\ncode\n" — no closing fence
        val spans = highlight("```\ncode\n")
        assertEquals(
            listOf(
                Span(TokenType.CodeFence, 0, 3),       // ```
                Span(TokenType.CodeContent, 4, 9),     // code\n
            ),
            spans,
        )
    }

    @Test
    fun emptyFencedBlock() {
        // "```\n```\n"
        val spans = highlight("```\n```\n")
        assertEquals(
            listOf(
                Span(TokenType.CodeFence, 0, 3),      // ```
                Span(TokenType.CodeFence, 4, 8),      // ```\n
            ),
            spans,
        )
    }

    @Test
    fun multipleContentLines() {
        // "```\na\nb\n```\n"
        val spans = highlight("```\na\nb\n```\n")
        assertEquals(
            listOf(
                Span(TokenType.CodeFence, 0, 3),      // ```
                Span(TokenType.CodeContent, 4, 6),    // a\n
                Span(TokenType.CodeContent, 6, 8),    // b\n
                Span(TokenType.CodeFence, 8, 12),     // ```\n
            ),
            spans,
        )
    }

    @Test
    fun failureProducesNoSpans() {
        val sink = SpanSink()
        val input = ParserInput.of("not a fence\n".toList(), sink)
        val result = pFencedCodeBlockHighlight()(input)
        assertIs<Failure<*, *>>(result)
        assertEquals(emptyList(), sink.spans)
    }
}
