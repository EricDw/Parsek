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
import parsek.markdown.highlight.scanDocument
import parsek.markdown.parser.parseDocument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val markdown = assets.open("raw/sample.md").bufferedReader().readText()
        val doc = parseDocument(markdown)
        val spans = scanDocument(markdown)

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
                        spans = spans,
                        modifier = Modifier
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}
