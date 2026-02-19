# Parsek

A Kotlin Multiplatform parser combinator library.

## Modules

| Module | Artifact | Description |
|---|---|---|
| `:core` | `com.dewildte.parsek:parsek-core` | Generic combinators (`Parser`, `ParserInput`, `ParseResult`, `satisfy`) |
| `:text` | `com.dewildte.parsek:parsek-text` | Text/character parsers (`char`); depends on `:core` |

## Overview

Parsek is built around three core types:

- **`ParserInput<I>`** — a position-aware wrapper around any list of tokens
- **`ParseResult<I, O>`** — a sealed result that is either a `Success` (value + next index) or a `Failure` (message + index)
- **`Parser<I, O>`** — a functional interface `(ParserInput<I>) -> ParseResult<I, O>`

The input type `I` and output type `O` are independent, so parsers can transform tokens into any type as they consume input.

## Targets

| Platform | Targets |
|---|---|
| JVM | `jvm` |
| JavaScript | `js` (browser + Node.js) |
| macOS | `macosArm64`, `macosX64` |
| Linux | `linuxX64` |
| Windows | `mingwX64` |
| WASM | `wasmJs`, `wasmWasi` |

## Usage

### `pSatisfy` (`:core`)

Consumes one element from the input if it matches a predicate.

```kotlin
import parsek.*

val isDigit: Parser<Char, Char> = pSatisfy { it.isDigit() }

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

val excl: Parser<Char, Char> = pChar('!')
```

### Chaining parsers

`ParserInput` is immutable — each successful parse returns a `nextIndex` you use to construct the next input position:

```kotlin
val input = ParserInput.of("42".toList())
val first = isDigit(input)                                    // Success('4', nextIndex=1, ...)
val second = isDigit(ParserInput(input.input, first.nextIndex)) // Success('2', nextIndex=2, ...)
```

## Building

```bash
./gradlew build
```

### Running tests

```bash
# Core tests
./gradlew :core:jvmTest

# All desktop targets
./gradlew :core:jvmTest :core:macosArm64Test :core:macosX64Test

# Full multi-module build
./gradlew build
```

An IntelliJ **Desktop Tests** run configuration is included in the repo.
