package parsek.commonmark.renderer

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.highlight.pDocumentHighlight

private fun loadResource(path: String): String =
    Thread.currentThread().contextClassLoader
        .getResourceAsStream(path)!!
        .bufferedReader()
        .readText()

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Parsek Markdown Viewer",
    ) {
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .crossfade(true)
                .build()
        }

        val markdown = loadResource("raw/sample.md")
        val sink = SpanSink()
        val input = ParserInput.of(markdown.toList(), sink)
        val doc = (pDocumentHighlight()(input) as Success).value

        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface {
                MarkdownRenderer(
                    document = doc,
                    spans = sink.spans,
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}
