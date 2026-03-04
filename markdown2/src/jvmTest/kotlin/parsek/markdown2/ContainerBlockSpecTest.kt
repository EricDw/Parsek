package parsek.markdown2

import parsek.markdown2.parser.parseDocument
import org.junit.Test

/**
 * Runs CommonMark spec examples for container block sections.
 *
 * Phase 3 covers: block quotes, list items, and lists.
 */
class ContainerBlockSpecTest {

    private val containerSections = setOf(
        "Block quotes",
        "List items",
        "Lists",
    )

    data class SectionResult(
        val section: String,
        var pass: Int = 0,
        var fail: Int = 0,
        var error: Int = 0,
        val failedExamples: MutableList<Int> = mutableListOf(),
    )

    @Test
    fun containerBlockCompliance() {
        val examples = loadSpecExamples()
        val containerExamples = examples.filter { it.section in containerSections }

        val results = mutableMapOf<String, SectionResult>()

        for (ex in containerExamples) {
            val sr = results.getOrPut(ex.section) { SectionResult(ex.section) }

            try {
                val doc = parseDocument(ex.markdown, gfm = false)
                val actual = renderHtml(doc)

                if (actual == ex.html) {
                    sr.pass++
                } else {
                    sr.fail++
                    sr.failedExamples.add(ex.example)
                }
            } catch (e: Exception) {
                sr.error++
                sr.failedExamples.add(ex.example)
            }
        }

        // Print results
        println("\n=== markdown2 Container Block Spec Compliance ===\n")
        var totalPass = 0
        var totalFail = 0
        var totalError = 0

        for ((section, sr) in results.entries.sortedBy { it.key }) {
            val total = sr.pass + sr.fail + sr.error
            val pct = if (total > 0) "%.1f%%".format(sr.pass * 100.0 / total) else "N/A"
            println("  $section: ${sr.pass}/$total ($pct)")
            if (sr.failedExamples.isNotEmpty()) {
                println("    Failed: ${sr.failedExamples.joinToString(", ")}")
            }
            totalPass += sr.pass
            totalFail += sr.fail
            totalError += sr.error
        }

        val total = totalPass + totalFail + totalError
        val pct = if (total > 0) "%.1f%%".format(totalPass * 100.0 / total) else "N/A"
        println("\n  TOTAL: $totalPass/$total ($pct)")
        println("  Pass: $totalPass, Fail: $totalFail, Error: $totalError\n")
    }

    @Test
    fun containerBlockDiff() {
        val examples = loadSpecExamples()
        val containerExamples = examples.filter { it.section in containerSections }

        val failures = mutableListOf<String>()

        for (ex in containerExamples) {
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
                failures.add(buildString {
                    appendLine("--- Example ${ex.example} (${ex.section}) ERROR ---")
                    appendLine("Markdown: ${ex.markdown.replace("\n", "↵")}")
                    appendLine("Error: ${e.message}")
                })
            }
        }

        if (failures.isNotEmpty()) {
            println("\n=== markdown2 Container Block Failures (${failures.size}) ===\n")
            for (f in failures.take(30)) {
                println(f)
            }
            if (failures.size > 30) {
                println("... and ${failures.size - 30} more failures")
            }
        }
    }
}
