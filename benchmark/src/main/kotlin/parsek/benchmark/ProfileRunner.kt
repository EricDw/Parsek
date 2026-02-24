package parsek.benchmark

import parsek.ParserInput
import parsek.Success
import parsek.commonmark.highlight.SpanSink
import parsek.commonmark.parser.pDocument
import parsek.commonmark.highlight.pDocumentHighlight

fun main() {
    println("=== Parsek CommonMark Profiler ===")
    println()

    val examples = loadSpecExamples()
    println("Loaded ${examples.size} spec examples")
    println()

    // --- Warmup ---
    val warmupIterations = 100
    print("Warming up ($warmupIterations iterations)...")
    repeat(warmupIterations) {
        for (ex in examples) {
            val input = ParserInput.of(ex.markdown.toList(), Unit)
            pDocument<Unit>()(input)
        }
    }
    println(" done")
    println()

    // --- Measure pDocument ---
    val measureIterations = 100
    println("Measuring pDocument ($measureIterations iterations)...")

    val perExampleTimes = LongArray(examples.size)
    val iterationTimes = LongArray(measureIterations)

    repeat(measureIterations) { iter ->
        val iterStart = System.nanoTime()
        for ((idx, ex) in examples.withIndex()) {
            val start = System.nanoTime()
            val input = ParserInput.of(ex.markdown.toList(), Unit)
            pDocument<Unit>()(input)
            perExampleTimes[idx] += System.nanoTime() - start
        }
        iterationTimes[iter] = System.nanoTime() - iterStart
    }

    val totalNs = iterationTimes.sum()
    val avgIterMs = totalNs / measureIterations / 1_000_000.0
    val throughput = examples.size.toLong() * measureIterations * 1_000_000_000L / totalNs.toDouble()

    println()
    println("--- pDocument Results ---")
    println("Total time:       %.2f ms".format(totalNs / 1_000_000.0))
    println("Avg per iteration: %.2f ms (all ${examples.size} examples)".format(avgIterMs))
    println("Throughput:        %.0f examples/sec".format(throughput))
    println()

    // --- Slowest examples ---
    println("Top 10 slowest spec examples (cumulative across $measureIterations iterations):")
    val indexed = perExampleTimes.mapIndexed { i, ns -> i to ns }
        .sortedByDescending { it.second }
        .take(10)

    for ((i, ns) in indexed) {
        val ex = examples[i]
        val avgUs = ns / measureIterations / 1_000.0
        println(
            "  #%-4d %-30s %8.1f us/parse".format(
                ex.example,
                ex.section.take(30),
                avgUs,
            )
        )
    }
    println()

    // --- Measure pDocumentHighlight ---
    println("Measuring pDocumentHighlight ($measureIterations iterations)...")

    val highlightIterationTimes = LongArray(measureIterations)
    repeat(measureIterations) { iter ->
        val iterStart = System.nanoTime()
        for (ex in examples) {
            val sink = SpanSink()
            val input = ParserInput.of(ex.markdown.toList(), sink)
            pDocumentHighlight()(input)
        }
        highlightIterationTimes[iter] = System.nanoTime() - iterStart
    }

    val highlightTotalNs = highlightIterationTimes.sum()
    val highlightAvgMs = highlightTotalNs / measureIterations / 1_000_000.0
    val highlightThroughput =
        examples.size.toLong() * measureIterations * 1_000_000_000L / highlightTotalNs.toDouble()

    println()
    println("--- pDocumentHighlight Results ---")
    println("Total time:       %.2f ms".format(highlightTotalNs / 1_000_000.0))
    println("Avg per iteration: %.2f ms (all ${examples.size} examples)".format(highlightAvgMs))
    println("Throughput:        %.0f examples/sec".format(highlightThroughput))
    println()

    // --- Comparison ---
    val overhead = (highlightAvgMs - avgIterMs) / avgIterMs * 100
    println("--- Comparison ---")
    println("Highlight overhead: %.1f%%".format(overhead))
    println()

    // --- Large document benchmark ---
    println("Measuring large document (all spec examples concatenated)...")
    val largeDoc = examples.joinToString("\n\n") { it.markdown }
    println("Large document size: ${largeDoc.length} chars")

    // Warmup
    repeat(10) {
        val input = ParserInput.of(largeDoc.toList(), Unit)
        pDocument<Unit>()(input)
    }

    val largeIterations = 20
    val largeTimes = LongArray(largeIterations)
    repeat(largeIterations) { iter ->
        val start = System.nanoTime()
        val input = ParserInput.of(largeDoc.toList(), Unit)
        pDocument<Unit>()(input)
        largeTimes[iter] = System.nanoTime() - start
    }

    val largeAvgMs = largeTimes.average() / 1_000_000.0
    val largeMinMs = largeTimes.min() / 1_000_000.0
    val largeMaxMs = largeTimes.max() / 1_000_000.0
    println("Avg: %.2f ms  Min: %.2f ms  Max: %.2f ms".format(largeAvgMs, largeMinMs, largeMaxMs))
    println("Throughput: %.0f chars/sec".format(largeDoc.length / (largeAvgMs / 1000.0)))
    println()

    // --- Parse success rate ---
    var successes = 0
    for (ex in examples) {
        val input = ParserInput.of(ex.markdown.toList(), Unit)
        if (pDocument<Unit>()(input) is Success) successes++
    }
    println("Parse success rate: $successes/${examples.size}")
}
