package parsek.commonmark.highlight

import parsek.Failure
import parsek.Parser
import parsek.Success

/**
 * Returns a [Parser] that runs [parser] and, on success, records a [Span]
 * covering the consumed range into the [SpanSink] user context.
 *
 * On failure the result is propagated unchanged and no span is recorded.
 * Spans are recorded in the order parsers succeed, so nested calls to [pTag]
 * produce inner spans before outer ones when both succeed.
 *
 * ### Example
 * ```kotlin
 * val pHeadingMarker = pTag(TokenType.HeadingMarker, pMany1(pChar('#')))
 * val pHeadingText   = pTag(TokenType.HeadingText,   pRestOfLine())
 * val pHeading       = pAnd(pHeadingMarker, pHeadingText)
 *
 * val sink  = SpanSink()
 * val input = ParserInput.of("## Hello".toList(), sink)
 * pHeading(input)
 * // sink.spans == [Span(HeadingMarker, 0, 2), Span(HeadingText, 3, 8)]
 * ```
 *
 * @param type the [TokenType] to attach to the recorded [Span].
 * @param parser the parser whose consumed range is recorded on success.
 * @return a [Parser] that behaves identically to [parser] but records a span.
 */
fun <O> pTag(
    type: TokenType,
    parser: Parser<Char, O, SpanSink>,
): Parser<Char, O, SpanSink> =
    Parser { input ->
        val start = input.index
        when (val result = parser(input)) {
            is Failure -> result
            is Success -> {
                input.userContext.spans += Span(type, start, result.nextIndex)
                result
            }
        }
    }
