[![Deploy API Docs](https://github.com/EricDw/Parsek/actions/workflows/docs.yml/badge.svg)](https://github.com/EricDw/Parsek/actions/workflows/docs.yml)

# Parsek

A Kotlin Multiplatform parser combinator library — compose small parsers into
complex ones with type-safe, functional combinators.

Built on top: a **CommonMark 0.31.2** parser with all five **GitHub Flavoured
Markdown (GFM)** extensions, syntax highlighting, and a Compose Multiplatform
renderer.

## Installation

Clone the repository and publish to your local Maven repository:

```bash
git clone https://github.com/EricDw/Parsek.git
cd parsek
./gradlew publishToMavenLocal
```

Then add the dependencies you need in your `build.gradle.kts`:

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    // Parser combinators
    implementation("com.dewildte.parsek:parsek-core:0.1.0")

    // Character/string parsers (includes :core)
    implementation("com.dewildte.parsek:parsek-text:0.1.0")

    // CommonMark + GFM markdown parser (includes :core and :text)
    implementation("com.dewildte.parsek:parsek-markdown:0.1.0")
}
```

## Quick Start

### Parse with combinators

```kotlin
import parsek.*
import parsek.text.*

// Match a greeting like "Hello, World!"
val greeting = pString("Hello, ") + pMany1(pSatisfy { it.isLetter() }) + pChar('!')

val input = ParserInput.of("Hello, World!".toList(), Unit)
when (val result = greeting(input)) {
    is Success -> println(result.value) // Pair(Pair("Hello, ", [W, o, r, l, d]), !)
    is Failure -> println(result.message)
}
```

### Combine and transform

```kotlin
import parsek.*
import parsek.text.*

// Parse an integer and double it
val number = pInt<Unit>().map { it * 2 }

val input = ParserInput.of("42".toList(), Unit)
when (val result = number(input)) {
    is Success -> println(result.value) // 84
    is Failure -> println(result.message)
}
```

### Parse Markdown

```kotlin
import parsek.markdown.parser.parseDocument

val doc = parseDocument("# Hello **world**")
println(doc.blocks) // [Heading(level=1, inlines=[Text("Hello "), StrongEmphasis([Text("world")])])]
```

### Syntax highlighting

```kotlin
import parsek.markdown.highlight.scanDocument

val spans = scanDocument("# Hello **world**")
spans.forEach { println("${it.type} [${it.start}..${it.end})") }
// HeadingMarker [0..2)
// Heading1 [0..17)
// StrongDelimiter [8..10)
// StrongContent [10..15)
// StrongDelimiter [15..17)
```

## Modules

| Module | Artifact | Description |
|---|---|---|
| `:core` | `parsek-core` | Generic `Parser<I, O, U>` type and combinators |
| `:text` | `parsek-text` | Character/string parsers (`pChar`, `pString`, `pInt`) |
| `:markdown` | `parsek-markdown` | CommonMark 0.31.2 + GFM parser, AST, and syntax highlighting |
| `:compose-renderer` | — | Compose Multiplatform markdown renderer (JVM + Android) |
| `:benchmark` | — | JMH benchmarks (JVM-only) |

## Platforms

| Platform | Targets |
|---|---|
| JVM | `jvm` |
| JavaScript | `js` (browser + Node.js) |
| macOS | `macosArm64`, `macosX64` |
| iOS | `iosArm64`, `iosSimulatorArm64` |
| Linux | `linuxX64` |
| Windows | `mingwX64` |
| WASM | `wasmJs`, `wasmWasi` |

## Spec Compliance

- **CommonMark 0.31.2:** 652/652 (100%)
- **GFM extensions:** 24/24 (100%) — tables, strikethrough, task lists, autolinks, disallowed HTML

## API Docs

Full KDoc API documentation is available on [GitHub Pages](https://ericdw.github.io/Parsek/).

To generate locally:

```bash
./gradlew dokkaHtmlMultiModule
open build/dokka/htmlMultiModule/index.html
```

## Building

```bash
# Run tests (JVM — fast)
./gradlew :core:jvmTest
./gradlew :text:jvmTest
./gradlew :markdown:jvmTest

# Full multiplatform tests (slower — native, JS, WASM)
./gradlew allTests

# Run Compose desktop demo
./gradlew :compose-renderer:run
```

## License

[MIT](LICENSE)
