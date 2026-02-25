# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Parsek is a **Kotlin Multiplatform parser combinator library**. It provides a
composable `Parser<I, O, U>` type and a growing set of combinators for building
parsers from small, reusable pieces.

Built on top of the library is a **CommonMark 0.31.2 parser** with all five
**GitHub Flavoured Markdown (GFM) extensions** (tables, strikethrough, task list
items, extended autolinks, disallowed raw HTML), a syntax highlighting system,
and a Compose Multiplatform renderer.

Spec compliance: 96.3% CommonMark (628/652), 100% GFM extensions (24/24).
See `COMMONMARK_PLAN.md` and `GFM_PLAN.md` for the roadmaps.

---

## Module Structure

```
:core              — generic Parser type, combinators, operator extensions
:text              — character/string parsers built on :core
:markdown          — CommonMark 0.31.2 + GFM extensions: AST, parsers, highlighting
:compose-renderer  — Compose Multiplatform markdown renderer with GFM support (JVM + Android)
:benchmark         — JMH benchmarks and profiling (JVM-only)
```

Dependencies: `:core` ← `:text` ← `:markdown` ← `:compose-renderer` / `:benchmark`

### Key files

| File | Description |
|------|-------------|
| `core/src/commonMain/kotlin/parsek/Parser.kt` | `Parser<I,O,U>` fun interface |
| `core/src/commonMain/kotlin/parsek/ParserInput.kt` | Immutable position-aware token view |
| `core/src/commonMain/kotlin/parsek/ParseResult.kt` | `Success` / `Failure` sealed types |
| `core/src/commonMain/kotlin/parsek/Parsers.kt` | All core combinators (`pSatisfy`, `pAnd`, `pOr`, …) |
| `core/src/commonMain/kotlin/parsek/ParserOps.kt` | Operator/infix/extension-property sugar |
| `text/src/commonMain/kotlin/parsek/text/TextParsers.kt` | `pChar`, `pString`, `pInt` |
| `markdown/src/commonMain/kotlin/parsek/markdown/ast/Block.kt` | 15 block-level AST types (incl. Table, TableRow, TableCell) |
| `markdown/src/commonMain/kotlin/parsek/markdown/ast/Inline.kt` | 12 inline-level AST types (incl. Strikethrough, ExtendedAutolink) |
| `markdown/src/commonMain/kotlin/parsek/markdown/ast/Document.kt` | Root `Document` type |
| `markdown/src/commonMain/kotlin/parsek/markdown/parser/DocumentParser.kt` | `pDocument` / `pBlock` entry points |
| `markdown/src/commonMain/kotlin/parsek/markdown/highlight/DocumentHighlight.kt` | `pDocumentHighlight` entry point |
| `markdown/src/commonMain/kotlin/parsek/markdown/highlight/TokenType.kt` | 30 semantic token types (incl. GFM extensions) |
| `compose-renderer/src/commonMain/kotlin/parsek/markdown/renderer/MarkdownRenderer.kt` | Compose rendering |

---

## Build

Gradle 8.11 + Kotlin Multiplatform 2.1.0.

```bash
# Run all tests (JVM fast path)
./gradlew :core:jvmTest
./gradlew :text:jvmTest
./gradlew :markdown:jvmTest

# Run a specific test class
./gradlew :core:jvmTest --tests "parsek.PEofTest"
./gradlew :markdown:jvmTest --tests "parsek.markdown.SpecTest"

# Full multiplatform test (slower — runs native, JS, wasm)
./gradlew allTests

# Run benchmarks
./gradlew :benchmark:jmh
```

---

## Core API

### Named combinators (`Parsers.kt`)

These are the stable, explicit API. Never remove them in favour of operators alone.

| Function | Behaviour |
|----------|-----------|
| `pSatisfy(pred)` | Consume one token if predicate holds |
| `pEof()` | Succeed only at end of input |
| `pAny()` | Consume any single token |
| `pAnd(a, b)` | Sequence — run `a` then `b`, return `Pair` |
| `pOr(a, b)` | Ordered choice — try `a`, fall back to `b` |
| `pMap(p) { }` | Transform success value |
| `pBind(p) { }` | Flat-map success value to a second parser |
| `pRepeat(n, p)` | Run `p` exactly `n` times |
| `pSequence(list)` | Run each parser in a list in order |
| `pMany(p)` | Zero-or-more |
| `pMany1(p)` | One-or-more |
| `pOptional(p)` | Zero-or-one (returns nullable) |
| `pLookAhead(p)` | Positive lookahead — match without consuming |
| `pNot(p)` | Negative lookahead — succeed only if `p` fails |
| `pLabel(p, msg)` | Replace failure message |
| `pSepBy(item, sep)` | Zero-or-more items separated by delimiter |
| `pSepBy1(item, sep)` | One-or-more items separated by delimiter |
| `pBetween(open, close, inner)` | Parse `inner` wrapped between `open` and `close` |

### Operator/infix sugar (`ParserOps.kt`)

These desugar to the named functions above. Add new sugar here; keep `Parsers.kt` clean.

| Expression | Desugars to |
|------------|-------------|
| `a + b` | `pAnd(a, b)` |
| `a * n` | `pRepeat(n, a)` |
| `!a` | `pNot(a)` |
| `a or b` | `pOr(a, b)` |
| `a label "msg"` | `pLabel(a, "msg")` |
| `a.optional` | `pOptional(a)` |
| `a.many` | `pMany(a)` |
| `a.many1` | `pMany1(a)` |
| `a.lookAhead` | `pLookAhead(a)` |
| `a.map { }` | `pMap(a) { }` |
| `a.bind { }` | `pBind(a) { }` |
| `a sepBy b` | `pSepBy(a, b)` |
| `a sepBy1 b` | `pSepBy1(a, b)` |

Operator precedence (high → low): `!` · `*` · `+` · infix (`or`, `label`, `sepBy`, …) · extension functions.
This means sequence (`+`) binds tighter than choice (`or`) without extra parentheses.

---

## Markdown Module

### AST (`parsek.markdown.ast`)

- **`Document(blocks: List<Block>)`** — root node
- **`Block`** sealed interface — 15 types: `ThematicBreak`, `Heading`, `IndentedCodeBlock`, `FencedCodeBlock`, `HtmlBlock`, `LinkReferenceDefinition`, `Paragraph`, `BlankLine`, `BlockQuote`, `ListItem` (with `checked: Boolean?` for task lists), `BulletList`, `OrderedList`, `Table`, `TableRow`, `TableCell`
- **`Inline`** sealed interface — 12 types: `Text`, `SoftBreak`, `HardBreak`, `CodeSpan`, `Emphasis`, `StrongEmphasis`, `Link`, `Image`, `Autolink`, `RawHtml`, `HtmlEntity`, `Strikethrough`, `ExtendedAutolink`

### Parsers (`parsek.markdown.parser`)

Block parsers in `parser/block/`, inline parsers in `parser/inline/`.
Entry point: `DocumentParser.pDocument()`.

### Syntax Highlighting (`parsek.markdown.highlight`)

The highlight system produces flat `Span` annotations without building an AST:

- **`TokenType`** — 30 sealed types for semantic tokens (headings, code, emphasis, GFM tables/strikethrough/task markers/autolinks, etc.)
- **`Span(type, start, end)`** — half-open range annotation
- **`SpanSink`** — mutable accumulator used as the `U` (user context) parameter
- **`pTag(type, parser)`** — wraps a parser to record a `Span` on success
- **`pDocumentHighlight()`** — entry point wiring all highlight wrappers

Block highlights in `highlight/block/`, inline highlights in `highlight/inline/`.

---

## Implementation Patterns

### `pLabel` scoping

Apply `pLabel` **only to the syntactic parsing step**, not to a surrounding
conversion or validation step. If the label wraps the whole parser, it will
silently replace domain-specific failure messages (e.g. "Integer out of range")
with the label string.

```kotlin
// WRONG — clobbers "Integer out of range: …" with "integer"
return pLabel(Parser { input ->
    when (val r = raw(input)) {
        is Success -> { val n = str.toIntOrNull() ?: return@Parser Failure("Integer out of range: …") }
    }
}, "integer")

// CORRECT — label only the token-matching step
val raw = pLabel(pAnd(sign, digits), "integer")
return Parser { input ->
    when (val r = raw(input)) {
        is Success -> { val n = str.toIntOrNull() ?: return@Parser Failure("Integer out of range: …") }
    }
}
```

### Adding a new text parser

1. Add the function to `text/src/commonMain/kotlin/parsek/text/TextParsers.kt`
2. Import only what you use from `parsek.*`
3. Add tests in `text/src/commonTest/kotlin/parsek/text/`
4. Follow the `PCharTest` / `PIntTest` test structure

### Adding a new core combinator

1. Add the function to `core/src/commonMain/kotlin/parsek/Parsers.kt`
2. Add operator/infix sugar to `ParserOps.kt` if appropriate
3. Add tests in `core/src/commonTest/kotlin/parsek/`
4. Follow the `PSatisfyTest` / `PEofTest` test structure

---

## KMP Notes

- `wasmJs`/`wasmWasi` targets require `@OptIn(ExperimentalWasmDsl::class)` per target block
- Both wasm targets need an explicit environment (e.g. `nodejs()`)
- Always include `repositories { mavenCentral() }` in every subproject's `build.gradle.kts`
- The root `build.gradle.kts` also needs `repositories { mavenCentral() }` for the
  `commonizeNativeDistribution` task
