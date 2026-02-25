package parsek.benchmark

import org.openjdk.jmh.annotations.*
import parsek.ParserInput
import parsek.markdown.highlight.SpanSink
import parsek.markdown.parser.pDocument
import parsek.markdown.highlight.pDocumentHighlight
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class ParseBenchmark {

    private lateinit var specExamples: List<SpecExample>
    private lateinit var sampleDocument: String
    private lateinit var largeDocument: String

    @Setup
    fun setup() {
        specExamples = loadSpecExamples()

        sampleDocument = buildString {
            appendLine("# Sample Document")
            appendLine()
            appendLine("This is a **sample** document with _various_ CommonMark features.")
            appendLine()
            appendLine("## Lists")
            appendLine()
            appendLine("- Item 1")
            appendLine("- Item 2 with `code`")
            appendLine("- Item 3 with [a link](https://example.com)")
            appendLine()
            appendLine("## Code Block")
            appendLine()
            appendLine("```kotlin")
            appendLine("fun main() {")
            appendLine("    println(\"Hello, world!\")")
            appendLine("}")
            appendLine("```")
            appendLine()
            appendLine("> A blockquote with **bold** and *italic* text.")
            appendLine(">")
            appendLine("> Second paragraph in the quote.")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("1. First ordered item")
            appendLine("2. Second ordered item")
            appendLine("3. Third ordered item")
            appendLine()
            appendLine("Some text with a hard break  ")
            appendLine("and a continuation.")
            appendLine()
            repeat(10) { i ->
                appendLine("### Section $i")
                appendLine()
                appendLine("Paragraph $i with some content. " +
                    "This has **bold**, *italic*, and `code` spans. " +
                    "Also a [link](https://example.com/$i) for good measure.")
                appendLine()
            }
        }

        largeDocument = specExamples.joinToString("\n\n") { it.markdown }
    }

    @Benchmark
    fun parseAllSpecExamples(): Int {
        var count = 0
        for (ex in specExamples) {
            val input = ParserInput.of(ex.markdown.toList(), Unit)
            pDocument<Unit>()(input)
            count++
        }
        return count
    }

    @Benchmark
    fun parseSampleDocument(): Any? {
        val input = ParserInput.of(sampleDocument.toList(), Unit)
        return pDocument<Unit>()(input)
    }

    @Benchmark
    fun parseHighlightDocument(): Any? {
        val sink = SpanSink()
        val input = ParserInput.of(sampleDocument.toList(), sink)
        return pDocumentHighlight()(input)
    }

    @Benchmark
    fun parseLargeDocument(): Any? {
        val input = ParserInput.of(largeDocument.toList(), Unit)
        return pDocument<Unit>()(input)
    }
}
