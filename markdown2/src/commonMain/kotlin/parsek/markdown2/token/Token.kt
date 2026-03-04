package parsek.markdown2.token

import parsek.markdown2.lexeme.Lexeme
import parsek.markdown2.lexeme.SourceRange

/**
 * A semantic token produced by the lexer (Stage 2).
 *
 * Block tokens are produced by the block lexer (one per logical line or construct).
 * Inline tokens are produced by the inline lexer when processing paragraph/heading content.
 *
 * Every token carries a [range] tracing back to the original source characters
 * via the constituent lexemes' [SourceRange]s.
 */
sealed interface Token {
    val range: SourceRange

    // ── Block tokens ────────────────────────────────────────────────────

    /** A line containing only whitespace (or empty). */
    data class BlankLine(override val range: SourceRange) : Token

    /**
     * A thematic break line (`---`, `***`, `___`, etc.).
     * @property marker the marker character ('-', '*', or '_')
     */
    data class ThematicBreakLine(val marker: Char, override val range: SourceRange) : Token

    /**
     * An ATX heading marker (`#` through `######`).
     * @property level 1–6
     */
    data class AtxHeadingMarker(
        val level: Int,
        override val range: SourceRange,
    ) : Token

    /**
     * The inline content of an ATX heading, as raw lexemes for later inline parsing.
     * @property lexemes the content lexemes (between marker and optional closing hashes)
     */
    data class AtxHeadingContent(
        val lexemes: List<Lexeme>,
        override val range: SourceRange,
    ) : Token

    /**
     * A setext heading underline (`===` or `---`).
     * @property level 1 for `=`, 2 for `-`
     * @property text the raw text of the underline (for orphaned-setext → paragraph fallback)
     */
    data class SetextUnderline(
        val level: Int,
        val text: String,
        override val range: SourceRange,
    ) : Token

    /**
     * An opening code fence (`` ``` `` or `~~~`).
     * @property fenceChar the fence character ('`' or '~')
     * @property fenceLength the number of fence characters (≥ 3)
     * @property indent the number of leading spaces (0–3)
     */
    data class CodeFenceOpen(
        val fenceChar: Char,
        val fenceLength: Int,
        val indent: Int,
        override val range: SourceRange,
    ) : Token

    /**
     * A closing code fence.
     * @property fenceChar the fence character ('`' or '~')
     * @property fenceLength the number of fence characters
     */
    data class CodeFenceClose(
        val fenceChar: Char,
        val fenceLength: Int,
        override val range: SourceRange,
    ) : Token

    /**
     * The info string after an opening code fence.
     * @property info the trimmed info string text
     */
    data class CodeFenceInfo(
        val info: String,
        override val range: SourceRange,
    ) : Token

    /**
     * A line of code content (inside a fenced or indented code block).
     * @property literal the raw text content of the line (including trailing newline if present)
     */
    data class CodeContent(
        val literal: String,
        override val range: SourceRange,
    ) : Token

    /**
     * A line of indented code (4+ spaces or 1+ tab of leading indent).
     * @property literal the raw text with the indent prefix stripped
     */
    data class IndentedCodeLine(
        val literal: String,
        override val range: SourceRange,
    ) : Token

    /**
     * A paragraph line — inline content deferred for the inline pass.
     * @property lexemes the raw lexemes of the line content
     */
    data class ParagraphLine(
        val lexemes: List<Lexeme>,
        override val range: SourceRange,
    ) : Token

    /**
     * An HTML block line.
     * @property literal the raw HTML content
     */
    data class HtmlBlockLine(
        val literal: String,
        override val range: SourceRange,
    ) : Token

    // ── Container block tokens (Phase 3) ────────────────────────────────

    /**
     * A block quote marker (`>`).
     */
    data class BlockQuoteMarker(override val range: SourceRange) : Token

    /**
     * A bullet list marker (`-`, `+`, or `*`).
     * @property marker the bullet character
     */
    data class BulletMarker(
        val marker: Char,
        override val range: SourceRange,
    ) : Token

    /**
     * An ordered list marker (e.g. `1.`, `2)`, etc.).
     * @property number the start number
     * @property delimiter the delimiter character ('.' or ')')
     */
    data class OrderedMarker(
        val number: Int,
        val delimiter: Char,
        override val range: SourceRange,
    ) : Token
}
