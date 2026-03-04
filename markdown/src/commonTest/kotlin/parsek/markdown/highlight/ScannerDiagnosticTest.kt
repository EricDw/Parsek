package parsek.markdown.highlight

import kotlin.test.Test

class ScannerDiagnosticTest {

    @Test
    fun diagnoseSampleFirstBlocks() {
        val md = "# Parsek Markdown Renderer\n" +
            "\n" +
            "A **Compose Multiplatform** renderer for [CommonMark](https://commonmark.org)\n" +
            "documents parsed by *Parsek* — a Kotlin Multiplatform parser combinator library.\n"

        val spans = scanDocument(md)
        println("Document (${md.length} chars):")
        println("---")
        for (span in spans.sortedBy { it.start }) {
            val excerpt = md.substring(
                span.start.coerceIn(0, md.length),
                span.end.coerceIn(0, md.length),
            )
            val escapedExcerpt = excerpt.replace("\n", "\\n").replace("\t", "\\t")
            println("  ${span.type}(${span.start}, ${span.end}) = \"$escapedExcerpt\"")
        }

        // Check overlaps
        val byType = spans.groupBy { it.type }
        for ((type, group) in byType) {
            val sorted = group.sortedBy { it.start }
            for (i in 0 until sorted.size - 1) {
                if (sorted[i].end > sorted[i + 1].start) {
                    println("OVERLAP: $type ${sorted[i]} and ${sorted[i + 1]}")
                }
            }
        }

        // Check out-of-bounds
        for (span in spans) {
            if (span.start < 0 || span.end > md.length) {
                println("OUT OF BOUNDS: ${span.type}(${span.start}, ${span.end}) doc length=${md.length}")
            }
        }
    }
}
