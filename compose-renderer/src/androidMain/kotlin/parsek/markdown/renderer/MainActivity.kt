package parsek.markdown.renderer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import parsek.ParserInput
import parsek.Success
import parsek.markdown.highlight.SpanSink
import parsek.markdown.highlight.pDocumentHighlight

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val markdown = assets.open("raw/sample.md").bufferedReader().readText()
        val sink = SpanSink()
        val input = ParserInput.of(markdown.toList(), sink)
        val doc = (pDocumentHighlight()(input) as Success).value

        setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context)
                    .crossfade(true)
                    .build()
            }

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
}
