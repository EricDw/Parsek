package parsek.markdown.highlight.inline

import parsek.markdown.ast.Inline
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.TokenType
import parsek.markdown.highlight.emit

/**
 * Emits [TokenType.ExtendedAutolinkUrl] spans for extended autolinks found
 * during post-processing of text nodes.
 *
 * Since extended autolinks are detected as a post-processing step (not a
 * combinator parser), this function walks the inline tree and emits spans
 * for any [Inline.ExtendedAutolink] nodes, using their position within the
 * original text to compute span ranges.
 *
 * Note: This function is designed to be called during highlight inline
 * resolution, where positions are relative to the paragraph's raw content.
 * The [textOffset] parameter allows adjusting the base position.
 */
fun emitExtendedAutolinkSpans(
    inlines: List<Inline>,
    sink: SpanSink,
    textOffset: Int = 0,
) {
    var pos = textOffset
    for (inline in inlines) {
        when (inline) {
            is Inline.Text -> pos += inline.literal.length
            is Inline.ExtendedAutolink -> {
                // The URL in the AST includes the scheme prefix that was added
                // (http:// for www, mailto: for email). The source text is shorter.
                val sourceLen = computeSourceLength(inline.url)
                sink.emit(TokenType.ExtendedAutolinkUrl, pos, pos + sourceLen)
                pos += sourceLen
            }
            is Inline.Emphasis -> {
                emitExtendedAutolinkSpans(inline.children, sink, pos)
                pos += inlineTextLength(inline)
            }
            is Inline.StrongEmphasis -> {
                emitExtendedAutolinkSpans(inline.children, sink, pos)
                pos += inlineTextLength(inline)
            }
            is Inline.Strikethrough -> {
                emitExtendedAutolinkSpans(inline.children, sink, pos)
                pos += inlineTextLength(inline)
            }
            else -> pos += inlineTextLength(inline)
        }
    }
}

/**
 * Computes the length of the source text for an extended autolink URL.
 * The URL stored in the AST has a scheme prefix added (`http://` for www
 * autolinks, `mailto:` for email autolinks) that wasn't in the source.
 */
private fun computeSourceLength(url: String): Int = when {
    url.startsWith("http://www.") -> url.length - 7  // "http://" was prepended to "www.…"
    url.startsWith("mailto:") -> url.length - 7       // "mailto:" was prepended
    else -> url.length                                  // URL autolinks have the scheme in source
}

private fun inlineTextLength(inline: Inline): Int = when (inline) {
    is Inline.Text -> inline.literal.length
    is Inline.CodeSpan -> inline.literal.length + 2 // approximate
    is Inline.SoftBreak -> 1
    is Inline.HardBreak -> 1
    else -> 0
}
