# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Parsek is a **Kotlin Multiplatform parser combinator library**. It provides a
composable `Parser<I, O, U>` type and a growing set of combinators for building
parsers from small, reusable pieces.

The long-term goal is a fully spec-compliant CommonMark parser built on top of
the library. See `COMMONMARK_PLAN.md` for the roadmap.

---

## Module Structure

```
:core        — generic Parser type, combinators, operator extensions
:text        — character/string parsers built on :core
:commonmark  — (planned) CommonMark AST + parsers built on :text
```

Dependencies: `:core` ← `:text` ← `:commonmark`

### Key files

| File | Description |
|------|-------------|
| `core/src/commonMain/kotlin/parsek/Parser.kt` | `Parser<I,O,U>` fun interface |
| `core/src/commonMain/kotlin/parsek/ParserInput.kt` | Immutable position-aware token view |
| `core/src/commonMain/kotlin/parsek/ParseResult.kt` | `Success` / `Failure` sealed types |
| `core/src/commonMain/kotlin/parsek/Parsers.kt` | All core combinators (`pSatisfy`, `pAnd`, `pOr`, …) |
| `core/src/commonMain/kotlin/parsek/ParserOps.kt` | Operator/infix/extension-property sugar |
| `text/src/commonMain/kotlin/parsek/text/TextParsers.kt` | `pChar`, `pString`, `pInt` |

---

## Build

Gradle 8.11 + Kotlin Multiplatform 2.1.0.

```bash
# Run all tests (JVM fast path)
./gradlew :core:jvmTest
./gradlew :text:jvmTest

# Run a specific test class
./gradlew :core:jvmTest --tests "parsek.PEofTest"

# Full multiplatform test (slower — runs native, JS, wasm)
./gradlew allTests
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

Operator precedence (high → low): `!` · `*` · `+` · infix (`or`, `label`, …) · extension functions.
This means sequence (`+`) binds tighter than choice (`or`) without extra parentheses.

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
