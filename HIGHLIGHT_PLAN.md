# Highlight Plan

A plan for layering syntax-highlighting support on top of the CommonMark
parsers. This phase begins only after the CommonMark spec suite is green
(i.e. after Phase 6 of `COMMONMARK_PLAN.md` is complete).

---

## Design Decision

Highlighting is a **side-effect of parsing**, not a post-processing pass over
the AST. As a parser succeeds on a range of input characters, it emits a
`Span` into a `SpanSink` via `pTag`. This means:

- No second traversal of the document is needed.
- Highlighting is exact — spans map directly to the character positions the
  parser consumed.
- AST-producing and highlight-emitting parsers share the same combinator
  logic; only the user-context type `U` differs.

### Two-mode design

Block and inline parsers are generic over `U : Any`. Running without
highlighting passes any context (or `Unit`). Running with highlighting
specialises `U` to `SpanSink`.

```
// AST-only
val result: ParseResult<Char, Document, Unit> =
    pDocument<Unit>().invoke(ParserInput(chars, Unit))

// Highlighting
val sink = SpanSink()
val result: ParseResult<Char, Document, SpanSink> =
    pDocumentHighlight().invoke(ParserInput(chars, sink))
val spans: List<Span> = sink.spans
```

The highlighting variants live in a parallel `highlight/` directory alongside
each parser file. They are thin wrappers that compose the generic parser with
`pTag` calls — they do not duplicate any parsing logic.

---

## Existing Infrastructure

All of the following already exist in
`commonmark/src/commonMain/kotlin/parsek/commonmark/highlight/`:

| File | Purpose |
|------|---------|
| `Span.kt` | Half-open `[start, end)` range annotated with a `TokenType` |
| `TokenType.kt` | Sealed interface listing every semantic token type |
| `SpanSink.kt` | Mutable accumulator; used as the `U` type parameter |
| `PTag.kt` | `pTag(type, parser)` — wraps a parser and records a `Span` on success |

`TokenType` already covers all block and inline constructs defined in the
CommonMark spec. Extend it only if a new construct is added to the AST.

---

## Tagging Conventions

### Granularity

Tag **syntactic markers** and **content regions** separately. Do not emit a
single span for the entire construct when sub-regions carry distinct meaning.

```
# Hello          ← ATX heading
^^               HeadingMarker  (the `#` and the following space)
  ^^^^^          HeadingText    (the inline content)
```

```
```kotlin        ← fenced code block
^^^              CodeFence      (opening fence)
      ^^^^^^     CodeInfo       (info string)
...              CodeContent    (body lines)
```              CodeFence      (closing fence)
```

### Nesting

`pTag` records inner spans before outer spans (innermost-first ordering).
Consumers that need outermost-first ordering should reverse or sort by start
position.

### No span on failure

`pTag` emits nothing when its inner parser fails. This is correct — partial
matches must not produce spans.

---

## Phase 7 — Highlight Wrappers

> Module: `:commonmark` — depends on Phase 6

Each PR adds a `*Highlight.kt` file in a `highlight/` sub-directory next to
the corresponding parser file. The file exports a single entry-point function
(e.g. `pThematicBreakHighlight()`) that wraps the generic parser with `pTag`.
It also adds tests asserting the correct `TokenType`s and character ranges for
a representative set of inputs.

---

### Phase 7.0 — Shared Helper

| PR | Contents |
|----|----------|
| 7.0 | `HighlightHelpers.kt` — any shared utilities needed across highlight wrappers (e.g. a helper to tag a delimiter and its content separately). May be a no-op PR if no helpers are needed. |

---

### Phase 7.1 — Leaf Block Highlight Wrappers

> Mirrors Phase 3

| PR | Parser | Token types emitted |
|----|--------|---------------------|
| 7.1.1 | `pThematicBreakHighlight` | `ThematicBreak` over the marker characters |
| 7.1.2 | `pAtxHeadingHighlight` | `HeadingMarker` (`#` run + space), `HeadingText` (inline content) |
| 7.1.3 | `pSetextHeadingHighlight` | `HeadingMarker` (underline), `HeadingText` (paragraph text) |
| 7.1.4 | `pIndentedCodeBlockHighlight` | `CodeContent` over each content line |
| 7.1.5 | `pFencedCodeBlockHighlight` | `CodeFence` (open/close fences), `CodeInfo` (info string), `CodeContent` (body) |
| 7.1.6 | `pHtmlBlockHighlight` | `HtmlBlock` over the entire raw block |
| 7.1.7 | `pLinkReferenceDefinitionHighlight` | `LinkLabel`, `LinkDestination`, `LinkTitle` |
| 7.1.8 | `pParagraphHighlight` | No block-level tags; inline content is tagged by the inline highlight wrappers |

---

### Phase 7.2 — Container Block Highlight Wrappers

> Mirrors Phase 4

| PR | Parser | Token types emitted |
|----|--------|---------------------|
| 7.2.1 | `pBlockQuoteHighlight` | `BlockQuoteMarker` (`>` + optional space per line); inner blocks tagged recursively |
| 7.2.2 | `pListItemHighlight` / `pListHighlight` | `ListMarker` (bullet or ordered marker); inner blocks tagged recursively |

---

### Phase 7.3 — Inline Highlight Wrappers

> Mirrors Phase 5

| PR | Parser(s) | Token types emitted |
|----|-----------|---------------------|
| 7.3.1 | `pBackslashEscapeHighlight` | `EscapeSequence` over `\` + punctuation |
| 7.3.2 | `pHtmlEntityHighlight` | `EntityRef` over `&…;` |
| 7.3.3 | `pCodeSpanHighlight` | `CodeSpanDelimiter` (backtick runs), `CodeSpanContent` (body) |
| 7.3.4 | `pLineBreakHighlight` | `HardBreak` or `SoftBreak` over the triggering whitespace/newline |
| 7.3.5 | `pAutolinkHighlight` | `AutolinkUrl` over the URI/email (excluding `<`/`>`) |
| 7.3.6 | `pRawHtmlHighlight` | `HtmlInline` over the entire tag |
| 7.3.7 | `pLinkHighlight` / `pImageHighlight` | `LinkBracket` (`[`,`]`), `LinkParen` (`(`,`)`), `ImageMarker` (`!`), `LinkDestination`, `LinkTitle` |
| 7.3.8 | `pEmphasisOrStrongHighlight` | `EmphasisMarker` or `StrongMarker` (delimiter runs) |
| 7.3.9 | `pTextHighlight` | `Text` over plain-text runs |

---

### Phase 7.4 — Document Highlight Entry Point

| PR | Contents |
|----|----------|
| 7.4 | `pDocumentHighlight()` — wires all highlight wrappers into a single entry point. Accepts an existing `SpanSink` (or creates one internally). Returns `ParseResult<Char, Document, SpanSink>`. Includes an end-to-end test that parses a multi-construct document and asserts the full ordered span list. |

---

## Testing Strategy

Each `*Highlight.kt` wrapper has a corresponding `*HighlightTest.kt` that:

1. Parses a minimal but representative input string.
2. Asserts the exact `List<Span>` — both `TokenType` and `[start, end)` range.
3. Includes at least one negative case (input that fails the parser emits no
   spans).

Use the same test structure as `PTagTest.kt` in
`commonmark/src/commonTest/kotlin/parsek/commonmark/highlight/`.

---

## Key Implementation Notes

- **Do not duplicate parsing logic.** Each highlight wrapper calls the
  corresponding generic parser for the actual match; `pTag` is applied only
  around sub-parsers at the boundary of what you want to tag.
- **Thread `SpanSink` consistently.** All parsers in a highlighting run must
  share the same `SpanSink` instance. Pass it via `ParserInput`'s `U`
  parameter; never construct a second sink mid-parse.
- **Extend `TokenType` conservatively.** The current set covers the full
  CommonMark spec. Add a new `data object` only when a genuinely new construct
  is added to the AST (e.g. a GitHub Flavoured Markdown extension).
- **Span ordering.** `pTag` emits in innermost-first order. The final
  `List<Span>` from `SpanSink.spans` is therefore not necessarily sorted by
  `start`. Consumers should sort if they need a linear sweep.