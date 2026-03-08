package parsek.markdown

import parsek.markdown.parser.parseDocument
import org.junit.Test

/**
 * Full CommonMark spec compliance test across ALL sections.
 */
class FullSpecTest {

    data class SectionResult(
        val section: String,
        var pass: Int = 0,
        var fail: Int = 0,
        var error: Int = 0,
        val failedExamples: MutableList<Int> = mutableListOf(),
    )

    @Test
    fun fullCompliance() {
        val examples = loadSpecExamples()

        val results = mutableMapOf<String, SectionResult>()

        for (ex in examples) {
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

        println("\n=== markdown Full CommonMark Spec Compliance ===\n")
        var totalPass = 0
        var totalFail = 0
        var totalError = 0

        for ((section, sr) in results.entries.sortedBy { it.key }) {
            val total = sr.pass + sr.fail + sr.error
            val pct = if (total > 0) "%.1f%%".format(sr.pass * 100.0 / total) else "N/A"
            println("  $section: ${sr.pass}/$total ($pct)")
            if (sr.failedExamples.isNotEmpty() && sr.failedExamples.size <= 10) {
                println("    Failed: ${sr.failedExamples.joinToString(", ")}")
            } else if (sr.failedExamples.size > 10) {
                println("    Failed: ${sr.failedExamples.take(10).joinToString(", ")}... (+${sr.failedExamples.size - 10})")
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
}
