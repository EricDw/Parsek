package parsek.markdown2

import parsek.markdown2.parser.parseDocument
import org.junit.Test

/**
 * Runs the GFM 0.29 spec test suite against markdown2's [parseDocument].
 *
 * Focuses on the 24 GFM-specific extension examples (tables, strikethrough,
 * task list items, extended autolinks, disallowed raw HTML).
 */
class GfmSpecTest {

    @Test
    fun gfmExtensionCompliance() {
        val allExamples = loadGfmSpecExamples()
        require(allExamples.isNotEmpty()) { "No GFM spec examples loaded" }

        val extensionExamples = allExamples.filter { it.extensions.isNotBlank() }
        require(extensionExamples.isNotEmpty()) { "No GFM extension examples found" }

        var totalPass = 0
        var totalFail = 0
        var totalError = 0
        val sectionResults = linkedMapOf<String, SectionResult>()

        for (ex in extensionExamples) {
            val section = sectionResults.getOrPut(ex.section) { SectionResult() }
            try {
                val doc = parseDocument(ex.markdown)
                val actualHtml = renderHtml(doc, gfmTagFilter = true)

                if (actualHtml == ex.html) {
                    totalPass++
                    section.pass++
                } else {
                    totalFail++
                    section.fail++
                    section.failedExamples.add(ex.example)
                }
            } catch (e: Exception) {
                totalError++
                section.error++
                section.errorExamples.add(ex.example)
            }
        }

        val total = totalPass + totalFail + totalError
        println()
        println("=".repeat(70))
        println("GFM 0.29 Extension Compliance Report (markdown2)")
        println("=".repeat(70))
        println()
        println("Overall: $totalPass/$total passed  ($totalFail failed, $totalError errors)")
        println("Pass rate: ${"%.1f".format(totalPass * 100.0 / total)}%")
        println()
        println("-".repeat(70))
        println("%-40s %5s %5s %5s %5s".format("Section", "Total", "Pass", "Fail", "Err"))
        println("-".repeat(70))

        for ((section, result) in sectionResults) {
            val sTotal = result.pass + result.fail + result.error
            println(
                "%-40s %5d %5d %5d %5d".format(
                    section.take(40),
                    sTotal,
                    result.pass,
                    result.fail,
                    result.error,
                )
            )
        }

        println("-".repeat(70))
        println()

        for ((section, result) in sectionResults) {
            val failed = result.failedExamples + result.errorExamples
            if (failed.isNotEmpty()) {
                println("  $section: failed examples ${failed.joinToString(", ")}")
            }
        }
    }

    @Test
    fun gfmExtensionDiff() {
        val allExamples = loadGfmSpecExamples()
        val extensionExamples = allExamples.filter { it.extensions.isNotBlank() }

        for (ex in extensionExamples) {
            try {
                val doc = parseDocument(ex.markdown)
                val actualHtml = renderHtml(doc, gfmTagFilter = true)
                if (actualHtml != ex.html) {
                    println("--- Example ${ex.example} (${ex.section}) [${ex.extensions}] ---")
                    println("MARKDOWN: ${ex.markdown.replace("\n", "\\n")}")
                    println("EXPECTED: ${ex.html.replace("\n", "\\n")}")
                    println("ACTUAL:   ${actualHtml.replace("\n", "\\n")}")
                    println()
                }
            } catch (e: Exception) {
                println("--- Example ${ex.example} (${ex.section}) [${ex.extensions}] ERROR ---")
                println("MARKDOWN: ${ex.markdown.replace("\n", "\\n")}")
                println("ERROR:    ${e.message}")
                println()
            }
        }
    }

    private class SectionResult {
        var pass = 0
        var fail = 0
        var error = 0
        val failedExamples = mutableListOf<Int>()
        val errorExamples = mutableListOf<Int>()
    }
}
