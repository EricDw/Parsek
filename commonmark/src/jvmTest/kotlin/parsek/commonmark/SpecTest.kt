package parsek.commonmark

import parsek.Failure
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.parser.pDocument
import java.io.File
import kotlin.test.Test

/**
 * Runs the official CommonMark 0.31.2 spec test suite against [pDocument].
 *
 * For each example, the markdown is parsed to an AST and rendered back to HTML
 * via [renderHtml]. The rendered HTML is compared against the spec's expected HTML.
 *
 * This test establishes a compliance baseline. Many failures are expected initially;
 * subsequent phases will improve compliance iteratively.
 */
class SpecTest {

    @Test
    fun specCompliance() {
        val examples = loadSpecExamples()
        require(examples.isNotEmpty()) { "No spec examples loaded" }

        var totalPass = 0
        var totalFail = 0
        var totalError = 0
        val sectionResults = linkedMapOf<String, SectionResult>()

        for (ex in examples) {
            val section = sectionResults.getOrPut(ex.section) { SectionResult() }
            try {
                val input = ParserInput.of(ex.markdown.toList(), Unit)
                val result = pDocument<Unit>()(input)

                if (result is Success) {
                    val actualHtml = renderHtml(result.value)
                    if (actualHtml == ex.html) {
                        totalPass++
                        section.pass++
                    } else {
                        totalFail++
                        section.fail++
                        section.failedExamples.add(ex.example)
                    }
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

        // Print summary
        val total = totalPass + totalFail + totalError
        println()
        println("=" .repeat(70))
        println("CommonMark 0.31.2 Spec Compliance Report")
        println("=" .repeat(70))
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

        // Print first few failures per section for debugging
        for ((section, result) in sectionResults) {
            val failed = result.failedExamples + result.errorExamples
            if (failed.isNotEmpty()) {
                val shown = failed.take(5)
                val more = if (failed.size > 5) " (and ${failed.size - 5} more)" else ""
                println("  $section: failed examples ${shown.joinToString(", ")}$more")
            }
        }
    }

    @Test
    fun writeDiffFiles() {
        val examples = loadSpecExamples()
        require(examples.isNotEmpty()) { "No spec examples loaded" }

        val outDir = File("build/spec-diff")
        outDir.mkdirs()

        val expected = StringBuilder()
        val actual = StringBuilder()

        for (ex in examples) {
            val header = "### Example ${ex.example} (${ex.section}) ###\n"
            expected.append(header)
            actual.append(header)

            expected.append(ex.html)
            if (!ex.html.endsWith("\n")) expected.append("\n")

            try {
                val input = ParserInput.of(ex.markdown.toList(), Unit)
                val result = pDocument<Unit>()(input)
                when (result) {
                    is Success -> {
                        val html = renderHtml(result.value)
                        actual.append(html)
                        if (!html.endsWith("\n")) actual.append("\n")
                    }
                    is Failure -> actual.append("<<PARSE FAILURE: ${result.message}>>\n")
                }
            } catch (e: Exception) {
                actual.append("<<ERROR: ${e.message}>>\n")
            }

            expected.append("\n")
            actual.append("\n")
        }

        File(outDir, "expected.html").writeText(expected.toString())
        File(outDir, "actual.html").writeText(actual.toString())
        println("Diff files written to: ${outDir.absolutePath}/")
        println("  expected.html  — spec expected output")
        println("  actual.html    — parser + renderer output")
        println("Run: diff ${outDir.absolutePath}/expected.html ${outDir.absolutePath}/actual.html")
    }

    private class SectionResult {
        var pass = 0
        var fail = 0
        var error = 0
        val failedExamples = mutableListOf<Int>()
        val errorExamples = mutableListOf<Int>()
    }
}
