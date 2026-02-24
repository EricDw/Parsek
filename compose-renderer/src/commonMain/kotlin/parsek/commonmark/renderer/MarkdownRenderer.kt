package parsek.commonmark.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import parsek.commonmark.ast.Block
import parsek.commonmark.ast.Document
import parsek.commonmark.ast.Inline
import parsek.commonmark.highlight.Span

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

/**
 * Renders a parsed CommonMark [Document] as Compose UI.
 *
 * @param document the parsed document AST.
 * @param spans highlight spans (currently unused — reserved for future
 *   source-level highlight overlay).
 * @param theme the highlight theme mapping token types to styles.
 * @param modifier optional Compose modifier.
 */
@Composable
fun MarkdownRenderer(
    document: Document,
    spans: List<Span> = emptyList(),
    theme: HighlightTheme = HighlightTheme.Default,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        for (block in document.blocks) {
            BlockRenderer(block, theme)
        }
    }
}

// ---------------------------------------------------------------------------
// Block rendering
// ---------------------------------------------------------------------------

@Composable
private fun BlockRenderer(block: Block, theme: HighlightTheme) {
    when (block) {
        is Block.Heading -> HeadingBlock(block, theme)
        is Block.Paragraph -> ParagraphBlock(block, theme)
        is Block.FencedCodeBlock -> CodeBlock(block.literal)
        is Block.IndentedCodeBlock -> CodeBlock(block.literal)
        is Block.BlockQuote -> BlockQuoteBlock(block, theme)
        is Block.BulletList -> BulletListBlock(block, theme)
        is Block.OrderedList -> OrderedListBlock(block, theme)
        is Block.ThematicBreak -> ThematicBreakBlock()
        is Block.HtmlBlock -> HtmlBlockBlock(block)
        is Block.BlankLine -> Spacer(modifier = Modifier.height(8.dp))
        is Block.ListItem -> ListItemBlocks(block.blocks, theme)
        is Block.LinkReferenceDefinition -> { /* not rendered */ }
    }
}

@Composable
private fun HeadingBlock(heading: Block.Heading, theme: HighlightTheme) {
    val fontSize = when (heading.level) {
        1 -> 32.sp
        2 -> 24.sp
        3 -> 20.sp
        4 -> 18.sp
        5 -> 16.sp
        else -> 14.sp
    }
    InlineContent(
        inlines = heading.inlines,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
        ),
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ParagraphBlock(paragraph: Block.Paragraph, theme: HighlightTheme) {
    InlineContent(
        inlines = paragraph.inlines,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun CodeBlock(literal: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(Color(0xFF1E1E1E))
            .padding(12.dp),
    ) {
        Text(
            text = literal,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFD4D4D4),
            ),
        )
    }
}

@Composable
private fun BlockQuoteBlock(blockQuote: Block.BlockQuote, theme: HighlightTheme) {
    Row(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(Color(0xFF608B4E)),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            for (block in blockQuote.blocks) {
                BlockRenderer(block, theme)
            }
        }
    }
}

@Composable
private fun BulletListBlock(list: Block.BulletList, theme: HighlightTheme) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        for (item in list.items) {
            Row(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = "\u2022  ",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(modifier = Modifier.weight(1f)) {
                    ListItemBlocks(item.blocks, theme)
                }
            }
        }
    }
}

@Composable
private fun OrderedListBlock(list: Block.OrderedList, theme: HighlightTheme) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        for ((index, item) in list.items.withIndex()) {
            Row(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = "${list.start + index}${list.delimiter} ",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(modifier = Modifier.weight(1f)) {
                    ListItemBlocks(item.blocks, theme)
                }
            }
        }
    }
}

@Composable
private fun ThematicBreakBlock() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun HtmlBlockBlock(block: Block.HtmlBlock) {
    Text(
        text = block.literal,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF808080),
        ),
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ListItemBlocks(blocks: List<Block>, theme: HighlightTheme) {
    for (block in blocks) {
        BlockRenderer(block, theme)
    }
}

// ---------------------------------------------------------------------------
// Inline content — splits inlines into text runs and images for composable layout
// ---------------------------------------------------------------------------

private sealed class InlineSegment {
    data class TextRun(val inlines: List<Inline>) : InlineSegment()
    data class ImageBlock(val image: Inline.Image) : InlineSegment()
}

private fun splitInlineSegments(inlines: List<Inline>): List<InlineSegment> {
    val segments = mutableListOf<InlineSegment>()
    val currentText = mutableListOf<Inline>()

    for (inline in inlines) {
        if (inline is Inline.Image) {
            if (currentText.isNotEmpty()) {
                segments.add(InlineSegment.TextRun(currentText.toList()))
                currentText.clear()
            }
            segments.add(InlineSegment.ImageBlock(inline))
        } else {
            currentText.add(inline)
        }
    }
    if (currentText.isNotEmpty()) {
        segments.add(InlineSegment.TextRun(currentText.toList()))
    }
    return segments
}

@Composable
private fun InlineContent(
    inlines: List<Inline>,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val segments = splitInlineSegments(inlines)
    val hasImages = segments.any { it is InlineSegment.ImageBlock }

    if (!hasImages) {
        Text(
            text = buildInlineAnnotatedString(inlines),
            style = style,
            modifier = modifier,
        )
    } else {
        Column(modifier = modifier) {
            for (segment in segments) {
                when (segment) {
                    is InlineSegment.TextRun -> {
                        Text(
                            text = buildInlineAnnotatedString(segment.inlines),
                            style = style,
                        )
                    }
                    is InlineSegment.ImageBlock -> {
                        AsyncImage(
                            model = segment.image.destination,
                            contentDescription = segment.image.alt,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Inline rendering — builds AnnotatedString from Inline AST
// ---------------------------------------------------------------------------

private val linkStyle = SpanStyle(
    color = Color(0xFF4EC9B0),
    textDecoration = TextDecoration.Underline,
)

private fun buildInlineAnnotatedString(inlines: List<Inline>): AnnotatedString =
    buildAnnotatedString {
        appendInlines(inlines)
    }

private fun AnnotatedString.Builder.appendInlines(inlines: List<Inline>) {
    for (inline in inlines) {
        when (inline) {
            is Inline.Text -> append(inline.literal)
            is Inline.SoftBreak -> append(" ")
            is Inline.HardBreak -> append("\n")
            is Inline.CodeSpan -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x30808080),
                    ),
                ) {
                    append(inline.literal)
                }
            }
            is Inline.Emphasis -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInlines(inline.children)
                }
            }
            is Inline.StrongEmphasis -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInlines(inline.children)
                }
            }
            is Inline.Link -> {
                withLink(LinkAnnotation.Url(inline.destination)) {
                    withStyle(linkStyle) {
                        appendInlines(inline.children)
                    }
                }
            }
            is Inline.Image -> {
                // Images are handled at the composable level via InlineContent.
                // This branch is only reached for images nested inside links etc.
                withStyle(SpanStyle(color = Color(0xFF808080))) {
                    append("[${inline.alt}]")
                }
            }
            is Inline.Autolink -> {
                withLink(LinkAnnotation.Url(inline.url)) {
                    withStyle(linkStyle) {
                        append(inline.url)
                    }
                }
            }
            is Inline.RawHtml -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF808080),
                    ),
                ) {
                    append(inline.literal)
                }
            }
            is Inline.HtmlEntity -> {
                append(inline.literal)
            }
        }
    }
}
