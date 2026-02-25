# Parsek

A Kotlin Multiplatform parser combinator library — with a CommonMark 0.31.2
parser and GitHub Flavoured Markdown (GFM) extensions built on top.

## Modules

| Module | Artifact | Description |
|---|---|---|
| `:core` | `com.dewildte.parsek:parsek-core` | Generic `Parser<I, O, U>` type and combinators |
| `:text` | `com.dewildte.parsek:parsek-text` | Character/string parsers; depends on `:core` |
| `:markdown` | `com.dewildte.parsek:parsek-markdown` | CommonMark 0.31.2 + GFM extensions parser (96.3% CommonMark, 100% GFM spec compliance) |
| `:compose-renderer` | — | Compose Multiplatform markdown renderer with GFM support (JVM + Android) |
| `:benchmark` | — | JMH benchmarks and profiling runner (JVM-only) |

## Overview

Parsek is built around three core types:

- **`ParserInput<I, U>`** — a position-aware, immutable wrapper around any list of tokens, with an optional user-context value of type `U`
- **`ParseResult<I, O, U>`** — a sealed result: `Success` (value + next index) or `Failure` (message + index)
- **`Parser<I, O, U>`** — a functional interface `(ParserInput<I, U>) -> ParseResult<I, O, U>`

The input type `I`, output type `O`, and user-context type `U` are all independent, so parsers can transform tokens into any type while threading arbitrary state through the parse.

## CommonMark + GFM

The `:markdown` module implements the [CommonMark 0.31.2](https://spec.commonmark.org/0.31.2/) specification with all five [GitHub Flavoured Markdown](https://github.github.com/gfm/) extensions, using a **two-pass design**:

1. **Block pass** — parses the input into block-level structure (headings, code blocks, lists, block quotes, tables, etc.) and collects link reference definitions.
2. **Inline pass** — re-parses inline content within paragraphs, headings, and table cells, resolving link references and applying GFM extensions (strikethrough, extended autolinks, task list markers).

### GFM Extensions

| Extension | Description |
|-----------|-------------|
| Tables | Pipe-separated tables with column alignment (`\|---\|:---:\|---:\|`) |
| Strikethrough | `~~deleted text~~` produces strikethrough formatting |
| Task list items | `- [x] done` / `- [ ] todo` checkbox markers in list items |
| Extended autolinks | Bare URLs (`https://...`, `www.…`) and emails auto-linked |
| Disallowed raw HTML | Filters `<script>`, `<style>`, etc. in rendered output |

### AST

The parser produces a `Document` containing a tree of `Block` and `Inline` nodes:

- **Blocks:** `Heading`, `Paragraph`, `FencedCodeBlock`, `IndentedCodeBlock`, `HtmlBlock`, `BlockQuote`, `BulletList`, `OrderedList`, `ListItem`, `ThematicBreak`, `LinkReferenceDefinition`, `BlankLine`, `Table`, `TableRow`, `TableCell`
- **Inlines:** `Text`, `Emphasis`, `StrongEmphasis`, `CodeSpan`, `Link`, `Image`, `Autolink`, `RawHtml`, `HtmlEntity`, `HardBreak`, `SoftBreak`, `Strikethrough`, `ExtendedAutolink`

### Syntax highlighting

The `parsek.markdown.highlight` package provides a `SpanSink` that collects token-level highlight spans during parsing:

```kotlin
import parsek.*
import parsek.markdown.highlight.*

val markdown = "# Hello **world**"
val sink = SpanSink()
val input = ParserInput.of(markdown.toList(), sink)

when (val result = pDocumentHighlight()(input)) {
    is Success -> {
        val document = result.value      // parsed AST
        val highlights = sink.spans      // List<Span> with TokenType, start, end
    }
    is Failure -> println(result.message)
}
```

There are 30 `TokenType` values covering block markers, inline delimiters, content regions, escapes, and GFM extensions.

## Targets

| Platform | Targets |
|---|---|
| JVM | `jvm` |
| JavaScript | `js` (browser + Node.js) |
| macOS | `macosArm64`, `macosX64` |
| Linux | `linuxX64` |
| Windows | `mingwX64` |
| WASM | `wasmJs`, `wasmWasi` |

The `:compose-renderer` module targets JVM and Android only. The `:benchmark` module targets JVM only.

## Usage

### `pSatisfy` (`:core`)

Consumes one element from the input if it matches a predicate.

```kotlin
import parsek.*

val isDigit: Parser<Char, Char, Unit> = pSatisfy { it.isDigit() }

val input = ParserInput.of("123".toList())
when (val result = isDigit(input)) {
    is Success -> println(result.value)   // '1'
    is Failure -> println(result.message)
}
```

### `pChar` (`:text`)

Matches a specific character.

```kotlin
import parsek.text.pChar

val excl: Parser<Char, Char, Unit> = pChar('!')
```

### `pDocument` (`:markdown`)

Parses a complete CommonMark document.

```kotlin
import parsek.*
import parsek.markdown.parser.pDocument

val markdown = """
    # Parsek

    A **parser combinator** library for Kotlin Multiplatform.

    - Composable
    - Type-safe
    - Cross-platform
""".trimIndent()

val input = ParserInput.of(markdown.toList())
when (val result = pDocument<Unit>()(input)) {
    is Success -> println(result.value) // Document(blocks=[Heading(...), Paragraph(...), BulletList(...)])
    is Failure -> println(result.message)
}
```

## Building

```bash
# Full build
./gradlew build

# Run tests per module (JVM fast path)
./gradlew :core:jvmTest
./gradlew :text:jvmTest
./gradlew :markdown:jvmTest

# Full multiplatform tests (slower — runs native, JS, WASM)
./gradlew allTests

# Run JMH benchmarks
./gradlew :benchmark:jmh

# Run profiling script
./gradlew :benchmark:run

# Run Compose desktop demo
./gradlew :compose-renderer:run
```
