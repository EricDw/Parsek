package parsek.commonmark.ast

/**
 * A node in the inline content of a CommonMark document.
 *
 * Inline nodes appear inside block-level containers such as [Block.Paragraph]
 * and [Block.Heading]. The sealed hierarchy mirrors the inline node types
 * defined in the CommonMark 0.31.2 specification.
 */
sealed interface Inline {

    /** A run of literal text. */
    data class Text(val literal: String) : Inline

    /** A soft line break (a plain newline inside a paragraph). */
    data object SoftBreak : Inline

    /**
     * A hard line break (two or more trailing spaces, or a backslash,
     * before a newline inside a paragraph).
     */
    data object HardBreak : Inline

    /** An inline code span delimited by backtick runs. */
    data class CodeSpan(val literal: String) : Inline

    /** Emphasis (`*text*` or `_text_`). */
    data class Emphasis(val children: List<Inline>) : Inline

    /** Strong emphasis (`**text**` or `__text__`). */
    data class StrongEmphasis(val children: List<Inline>) : Inline

    /** An inline link. */
    data class Link(
        val destination: String,
        val title: String?,
        val children: List<Inline>,
    ) : Inline

    /** An inline image. */
    data class Image(
        val destination: String,
        val title: String?,
        val alt: String,
    ) : Inline

    /** A URI or email address enclosed in angle brackets. */
    data class Autolink(val url: String) : Inline

    /** A raw HTML tag or construct appearing inline. */
    data class RawHtml(val literal: String) : Inline

    /** An HTML character entity reference (e.g. `&amp;`, `&#42;`, `&#x2A;`). */
    data class HtmlEntity(val literal: String) : Inline
}
