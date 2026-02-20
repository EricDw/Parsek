# Infix & Operator API Plan

A plan for adding an ergonomic operator/infix surface to `Parser` via
extension functions in `:core`. The underlying combinators (`pAnd`, `pOr`,
etc.) remain unchanged — this layer is pure syntax.

---

## Design Goals

- Common compositions read left-to-right like prose
- Operator precedence mirrors parser-combinator convention: sequence binds
  tighter than choice
- Lambda-taking operations use regular extension functions (trailing-lambda
  syntax), not `infix`, to avoid visual ambiguity
- Unary/postfix transforms are extension properties so they chain without
  parentheses
- Nothing is removed; the `p*` functions stay as the explicit, named API

---

## Proposed API

### Binary operators — higher precedence

| Expression | Desugars to | Meaning |
|---|---|---|
| `a + b` | `pAnd(a, b)` | Sequence — run `a` then `b`, collect both |
| `a * n` | `pRepeat(n, a)` | Repeat `a` exactly `n` times |

`+` maps to `operator fun plus` and `*` maps to `operator fun times`.
Both have arithmetic precedence, so they bind tighter than any infix call.

### Binary infix — lower precedence

| Expression | Desugars to | Meaning |
|---|---|---|
| `a or b` | `pOr(a, b)` | Ordered choice — try `a`, fall back to `b` |
| `a sepBy sep` | `pSepBy(a, sep)` | Zero-or-more `a` separated by `sep` |
| `a sepBy1 sep` | `pSepBy1(a, sep)` | One-or-more `a` separated by `sep` |
| `a label s` | `pLabel(a, s)` | Replace failure message with `s` |

All `infix` functions have lower precedence than operators, so sequence
naturally dominates choice without any extra parentheses:

```kotlin
a + b or c + d   // (a + b) or (c + d) ✓
```

### Unary prefix operator

| Expression | Desugars to | Meaning |
|---|---|---|
| `!a` | `pNot(a)` | Negative lookahead — succeed only if `a` would fail |

Maps to `operator fun not()`.

### Extension properties — postfix style

| Expression | Desugars to | Meaning |
|---|---|---|
| `a.optional` | `pOptional(a)` | Zero-or-one |
| `a.many` | `pMany(a)` | Zero-or-more |
| `a.many1` | `pMany1(a)` | One-or-more |
| `a.lookAhead` | `pLookAhead(a)` | Positive lookahead without consuming |

Properties chain naturally after any expression:

```kotlin
digit.many1           // pMany1(digit)
(digit or letter).many  // pMany(pOr(digit, letter))
```

### Extension functions — lambda operations

`map` and `bind` take lambdas. Using `infix` here would create visual
ambiguity between infix syntax and trailing-lambda syntax, so these are
plain extension functions that use Kotlin's conventional trailing-lambda
calling style instead.

| Call | Desugars to | Meaning |
|---|---|---|
| `a.map { … }` | `pMap(a) { … }` | Transform success value |
| `a.bind { … }` | `pBind(a) { … }` | Flat-map to a second parser |

---

## Precedence Summary

From highest to lowest:

```
!a                   unary not  (pNot)
a * n                times      (pRepeat)
a + b                plus       (pAnd)
a or b               infix      (pOr)
a sepBy sep          infix      (pSepBy / pSepBy1)
a label "msg"        infix      (pLabel)
a.map { }            extension function
a.bind { }           extension function
```

A realistic grammar fragment showing precedence at work:

```kotlin
val sign    = pChar('+') or pChar('-')
val digits  = digit.many1
val integer = sign.optional + digits label "integer"
val csv     = integer sepBy comma
```

Desugars to:

```kotlin
val sign    = pOr(pChar('+'), pChar('-'))
val digits  = pMany1(digit)
val integer = pLabel(pAnd(pOptional(sign), digits), "integer")
val csv     = pSepBy(integer, comma)
```

---

## Implementation Notes

**File:** `core/src/commonMain/kotlin/parsek/ParserOps.kt`

All declarations are extension functions/properties on `Parser<I, O, U>`.
No changes to `Parser.kt`, `Parsers.kt`, or any existing file.

**Variance:** `Parser<in I, out O, U>`. Some operators pair two parsers
with potentially different output types (e.g. `+`), requiring unconstrained
type parameters on the extension. `or` requires both sides share the same
`O`, mirroring `pOr`. Use `@UnsafeVariance` only where Kotlin's variance
checker requires it and the safety is guaranteed by the combinator semantics.

**`sepBy` / `sepBy1`:** These are Phase 0 PR 0.3 items — the operator
extensions for them land in the same PR as the combinators themselves, not
in `ParserOps.kt` independently.

---

## What Stays as Named Functions

Some combinators do not map cleanly to operators and are left as-is:

| Combinator | Reason |
|---|---|
| `pSatisfy` | Factory function, not a combinator over existing parsers |
| `pChar`, `pString`, etc. | Domain-specific factories in `:text` |
| `pSequence` | Takes a `List` — no natural operator form |
| `pBetween` | Three-argument combinator — consider a dedicated infix once added |
| `pEof`, `pAny` | Zero-argument factories — no receiver to extend |

---

## PR Plan

A single PR: **"Add operator and infix extensions to Parser"**

1. Add `ParserOps.kt` with all operators, infix functions, and properties
   listed above
2. Update existing tests that can be made more readable with the new API
3. Add `ParserOpsTest.kt` demonstrating real grammar fragments written in
   the new style end-to-end (a simple expression parser or similar)
