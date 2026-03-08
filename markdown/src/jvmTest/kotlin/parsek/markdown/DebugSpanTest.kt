package parsek.markdown

import parsek.markdown.highlight.scanDocument
import org.junit.Test

class DebugSpanTest {
    @Test
    fun debugSampleMd() {
        val md = """# Parsek Markdown Renderer

A **Compose Multiplatform** renderer for [CommonMark](https://commonmark.org)
documents parsed by *Parsek* — a Kotlin Multiplatform parser combinator library.

## Inline Formatting

CommonMark supports several inline constructs:

- **Bold text** is wrapped in double asterisks or underscores.
- *Italic text* uses single asterisks or underscores.
- ***Bold and italic*** can be combined with triple markers.
- Inline `code spans` use backtick delimiters.
- Multi-backtick code spans: `` `backticks` inside code ``.
- You can escape special characters with a backslash: \*not emphasis\*.
- More escapes: \# not a heading, \> not a quote, \[not a link\].

## Links and Autolinks

- Inline links: [Parsek on GitHub](https://github.com/parsek "Parsek repo")
- Reference-style links: [CommonMark] is a spec for Markdown.
- Autolinks use angle brackets: <https://commonmark.org>
- Email autolinks: <user@example.com>

[CommonMark]: https://commonmark.org
"""
        val spans = scanDocument(md)
        println("\n=== All spans around [CommonMark] ===\n")
        // Show spans that overlap with the region containing [CommonMark]
        for (span in spans) {
            val slice = if (span.start >= 0 && span.end <= md.length && span.start < span.end) {
                md.substring(span.start, span.end).replace("\n", "↵")
            } else {
                "OUT_OF_BOUNDS(${span.start}..${span.end}, len=${md.length})"
            }
            // Show spans in the first paragraph and the links section
            if ((span.start in 0..200) || (span.start in 500..750)) {
                println("  ${span.type} [${span.start}..${span.end}] = \"$slice\"")
            }
        }

        // Also check for any out-of-bounds spans
        println("\n=== Out of bounds or suspicious spans ===")
        for (span in spans) {
            if (span.start < 0 || span.end > md.length || span.start >= span.end) {
                println("  BAD: ${span.type} [${span.start}..${span.end}]")
            }
        }

        // Verify specific characters at key positions
        println("\n=== Character verification ===")
        val firstBracket = md.indexOf("[CommonMark]")
        println("First [CommonMark] at index $firstBracket: '${md.substring(firstBracket, firstBracket + 14)}'")
        val secondBracket = md.indexOf("[CommonMark]", firstBracket + 1)
        println("Second [CommonMark] at index $secondBracket: '${md.substring(secondBracket, secondBracket + 14)}'")

        // Show spans around each [CommonMark] occurrence
        for (pos in listOf(firstBracket, secondBracket)) {
            println("\nSpans covering [$pos..${pos+14}]:")
            for (span in spans) {
                if (span.end > pos && span.start < pos + 14) {
                    val slice = if (span.start >= 0 && span.end <= md.length) {
                        md.substring(span.start, span.end).replace("\n", "↵")
                    } else "OOB"
                    println("  ${span.type} [${span.start}..${span.end}] = \"$slice\"")
                }
            }
        }
    }
}
