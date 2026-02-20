package parsek.commonmark.highlight

/**
 * The semantic type of a highlighted span in a CommonMark document.
 *
 * Token types cover both block-level markers and inline constructs. A syntax
 * highlighter maps each type to a colour or style; a semantic analyser can use
 * the types for structure-aware tooling.
 */
sealed interface TokenType {

    // -------------------------------------------------------------------------
    // Block — thematic break
    // -------------------------------------------------------------------------

    /** The marker characters of a thematic break (`---`, `***`, `___`). */
    data object ThematicBreak : TokenType

    // -------------------------------------------------------------------------
    // Block — headings
    // -------------------------------------------------------------------------

    /** The `#` characters of an ATX heading, or the `=`/`-` underline of a Setext heading. */
    data object HeadingMarker : TokenType

    /** The text content of a heading. */
    data object HeadingText : TokenType

    // -------------------------------------------------------------------------
    // Block — code blocks
    // -------------------------------------------------------------------------

    /** The opening or closing fence of a fenced code block (`` ``` `` or `~~~`). */
    data object CodeFence : TokenType

    /** The info string following a fenced code block opening fence. */
    data object CodeInfo : TokenType

    /** The content lines of a fenced or indented code block. */
    data object CodeContent : TokenType

    // -------------------------------------------------------------------------
    // Block — block quotes and lists
    // -------------------------------------------------------------------------

    /** The `>` marker of a block quote. */
    data object BlockQuoteMarker : TokenType

    /** The bullet (`-`, `+`, `*`) or ordered (`1.`, `2)`) list item marker. */
    data object ListMarker : TokenType

    // -------------------------------------------------------------------------
    // Block — HTML and link reference definitions
    // -------------------------------------------------------------------------

    /** A raw HTML block. */
    data object HtmlBlock : TokenType

    /** The `[label]` part of a link reference definition. */
    data object LinkLabel : TokenType

    /** A link or image destination URL. */
    data object LinkDestination : TokenType

    /** A link or image title (`"…"`, `'…'`, or `(…)`). */
    data object LinkTitle : TokenType

    // -------------------------------------------------------------------------
    // Inline — emphasis and strong
    // -------------------------------------------------------------------------

    /** A delimiter run opening or closing emphasis (`*` or `_`). */
    data object EmphasisMarker : TokenType

    /** A delimiter run opening or closing strong emphasis (`**` or `__`). */
    data object StrongMarker : TokenType

    // -------------------------------------------------------------------------
    // Inline — code spans
    // -------------------------------------------------------------------------

    /** The backtick run(s) delimiting an inline code span. */
    data object CodeSpanDelimiter : TokenType

    /** The content of an inline code span. */
    data object CodeSpanContent : TokenType

    // -------------------------------------------------------------------------
    // Inline — links and images
    // -------------------------------------------------------------------------

    /** The `[` or `]` bracket of an inline link or image. */
    data object LinkBracket : TokenType

    /** The `(` or `)` parenthesis of an inline link. */
    data object LinkParen : TokenType

    /** The `!` prefix of an image. */
    data object ImageMarker : TokenType

    // -------------------------------------------------------------------------
    // Inline — autolinks and raw HTML
    // -------------------------------------------------------------------------

    /** A URI or email address inside an autolink (`<…>`). */
    data object AutolinkUrl : TokenType

    /** An inline raw HTML tag. */
    data object HtmlInline : TokenType

    // -------------------------------------------------------------------------
    // Inline — escapes and entities
    // -------------------------------------------------------------------------

    /** A backslash escape sequence (`\!`, `\*`, etc.). */
    data object EscapeSequence : TokenType

    /** An HTML character entity reference (`&amp;`, `&#42;`, `&#x2A;`). */
    data object EntityRef : TokenType

    // -------------------------------------------------------------------------
    // Inline — breaks and plain text
    // -------------------------------------------------------------------------

    /** A hard line break (two trailing spaces or `\` before a newline). */
    data object HardBreak : TokenType

    /** A soft line break (a plain newline inside a paragraph). */
    data object SoftBreak : TokenType

    /** A run of unstyled plain text. */
    data object Text : TokenType
}
