package parsek.commonmark.renderer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import parsek.commonmark.highlight.TokenType

/**
 * Maps [TokenType] values to [SpanStyle] for rendering highlighted CommonMark.
 *
 * @property styles the mapping from token type to Compose text style.
 */
data class HighlightTheme(
    val styles: Map<TokenType, SpanStyle>,
) {
    companion object {
        val Default: HighlightTheme = HighlightTheme(
            mapOf(
                // Block — headings
                TokenType.HeadingMarker to SpanStyle(color = Color(0xFF569CD6)),
                TokenType.HeadingText to SpanStyle(color = Color(0xFFD4D4D4)),

                // Block — thematic break
                TokenType.ThematicBreak to SpanStyle(color = Color(0xFF808080)),

                // Block — code blocks
                TokenType.CodeFence to SpanStyle(color = Color(0xFF608B4E)),
                TokenType.CodeInfo to SpanStyle(color = Color(0xFFCE9178)),
                TokenType.CodeContent to SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x20808080),
                ),

                // Block — containers
                TokenType.BlockQuoteMarker to SpanStyle(color = Color(0xFF608B4E)),
                TokenType.ListMarker to SpanStyle(color = Color(0xFFD7BA7D)),

                // Block — HTML and link refs
                TokenType.HtmlBlock to SpanStyle(color = Color(0xFF808080)),
                TokenType.LinkLabel to SpanStyle(color = Color(0xFF9CDCFE)),
                TokenType.LinkDestination to SpanStyle(color = Color(0xFFCE9178)),
                TokenType.LinkTitle to SpanStyle(color = Color(0xFFCE9178)),

                // Inline — emphasis
                TokenType.EmphasisMarker to SpanStyle(
                    color = Color(0xFF569CD6),
                    fontStyle = FontStyle.Italic,
                ),
                TokenType.StrongMarker to SpanStyle(color = Color(0xFF569CD6)),

                // Inline — code spans
                TokenType.CodeSpanDelimiter to SpanStyle(color = Color(0xFF608B4E)),
                TokenType.CodeSpanContent to SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x20808080),
                ),

                // Inline — links and images
                TokenType.LinkBracket to SpanStyle(color = Color(0xFF569CD6)),
                TokenType.LinkParen to SpanStyle(color = Color(0xFF569CD6)),
                TokenType.ImageMarker to SpanStyle(color = Color(0xFF569CD6)),

                // Inline — autolinks and raw HTML
                TokenType.AutolinkUrl to SpanStyle(color = Color(0xFF4EC9B0)),
                TokenType.HtmlInline to SpanStyle(color = Color(0xFF808080)),

                // Inline — escapes and entities
                TokenType.EscapeSequence to SpanStyle(color = Color(0xFFD7BA7D)),
                TokenType.EntityRef to SpanStyle(color = Color(0xFFD7BA7D)),

                // Inline — breaks and text
                TokenType.HardBreak to SpanStyle(color = Color(0xFF808080)),
                TokenType.SoftBreak to SpanStyle(color = Color(0xFF808080)),
                TokenType.Text to SpanStyle(color = Color(0xFFD4D4D4)),
            ),
        )
    }
}
