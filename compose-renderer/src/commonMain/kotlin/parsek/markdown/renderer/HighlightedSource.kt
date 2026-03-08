package parsek.markdown.renderer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import parsek.markdown.highlight.Span
import parsek.markdown.highlight.scanDocument

/**
 * Displays raw markdown source text with syntax highlighting applied
 * using spans from [scanDocument].
 *
 * Each [Span] is mapped to a [SpanStyle] via the [theme], producing a
 * color-coded view of the markdown source — similar to a code editor.
 */
@Composable
fun HighlightedSource(
    markdown: String,
    theme: HighlightTheme = HighlightTheme.Default,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(markdown, theme) {
        val spans = scanDocument(markdown)
        buildHighlightedString(markdown, spans, theme)
    }

    Text(
        text = annotated,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        modifier = modifier.horizontalScroll(rememberScrollState()),
    )
}

private fun buildHighlightedString(
    text: String,
    spans: List<Span>,
    theme: HighlightTheme,
): AnnotatedString = buildAnnotatedString {
    // Base style: dim gray for unhighlighted text
    append(text)
    addStyle(SpanStyle(color = Color(0xFF808080)), 0, text.length)

    // Apply each span's style on top
    for (span in spans) {
        val style = theme.styles[span.type] ?: continue
        val start = span.start.coerceIn(0, text.length)
        val end = span.end.coerceIn(0, text.length)
        if (end > start) {
            addStyle(style, start, end)
        }
    }
}
