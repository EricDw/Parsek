# Parsek

A Kotlin Multiplatform parser combinator library.

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

### `satisfy`

Consumes one element from the input if it matches a predicate.

```kotlin
val isDigit: Parser<Char, Char> = satisfy { it.isDigit() }

val input = ParserInput.of("123".toList())
when (val result = isDigit(input)) {
    is Success -> println(result.value)   // '1'
    is Failure -> println(result.message)
}
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
# All desktop targets (JVM + macOS native)
./gradlew jvmTest macosArm64Test macosX64Test

# Single target
./gradlew jvmTest
```

An IntelliJ **Desktop Tests** run configuration is included in the repo.
