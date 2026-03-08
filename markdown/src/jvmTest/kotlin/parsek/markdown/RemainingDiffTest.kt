package parsek.markdown

import parsek.markdown.parser.parseDocument
import org.junit.Test

class RemainingDiffTest {
    @Test
    fun remainingDiff() {
        val examples = loadSpecExamples()
        val failures = mutableListOf<String>()
        for (ex in examples) {
            try {
                val doc = parseDocument(ex.markdown, gfm = false)
                val actual = renderHtml(doc)
                if (actual != ex.html) {
                    failures.add(buildString {
                        appendLine("--- Example ${ex.example} (${ex.section}) ---")
                        appendLine("Markdown: ${ex.markdown.replace("\n", "↵")}")
                        appendLine("Expected: ${ex.html.replace("\n", "↵")}")
                        appendLine("Actual:   ${actual.replace("\n", "↵")}")
                    })
                }
            } catch (e: Exception) {
                failures.add("--- Example ${ex.example} (${ex.section}) ERROR: ${e.message}")
            }
        }
        if (failures.isNotEmpty()) {
            println("\n=== Remaining Failures (${failures.size}) ===\n")
            for (f in failures) println(f)
        }
    }
}
