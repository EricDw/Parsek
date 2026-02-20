# CommonMark Parser Plan

A plan for implementing a CommonMark 0.31.2 parser on top of Parsek. The spec
defines block structure and inline content as two largely independent phases —
this plan mirrors that two-pass design.

---

## 1. Core Combinators to Add

The current core covers the basics. CommonMark parsing requires a handful of
additional combinators.

| Combinator   | Signature sketch                             | Purpose                                                                       |
|--------------|----------------------------------------------|-------------------------------------------------------------------------------|
| `pEof`       | `Parser<I, Unit, U>`                         | Succeed only at end of input                                                  |
| `pAny`       | `Parser<I, I, U>`                            | Consume any single token                                                      |
| `pNot`       | `(Parser<I,O,U>) → Parser<I,Unit,U>`         | Negative lookahead — succeed without consuming if the inner parser would fail |
| `pLookAhead` | `(Parser<I,O,U>) → Parser<I,O,U>`            | Positive lookahead — run parser without consuming input                       |
| `pSepBy`     | `(Parser<I,O,U>, sep) → Parser<I,List<O>,U>` | Zero-or-more items separated by a delimiter                                   |
| `pSepBy1`    | same, one-or-more                            | One-or-more items separated by a delimiter                                    |
| `pBetween`   | `(open, close, inner) → Parser<I,O,U>`       | Parse inner content between two delimiters                                    |
| `pChain`     | vararg overload for `pSequence`              | Convenience over `pSequence(listOf(...))`                                     |

---

## 2. Text Module Primitives

Low-level character parsers. Most are thin wrappers over `pSatisfy` or `pChar`/`pString`.

### Characters

| Parser                | Matches                                                                 |
|-----------------------|-------------------------------------------------------------------------|
| `pDigit`              | `0`–`9`                                                                 |
| `pHexDigit`           | `0`–`9`, `a`–`f`, `A`–`F`                                               |
| `pLetter`             | any Unicode letter                                                      |
| `pSpace`              | literal space `' '`                                                     |
| `pTab`                | literal tab `'\t'`                                                      |
| `pSpaceOrTab`         | `' '` or `'\t'`                                                         |
| `pLineEnding`         | `\n`, `\r\n`, or `\r` (not followed by `\n`) — yields a normalised `\n` |
| `pBlankLine`          | zero or more spaces/tabs then a line ending                             |
| `pAsciiPunctuation`   | any of ``!"#$%&'()*+,-./:;<=>?@[\]^_`{                                  |}~`` |
| `pUnicodePunctuation` | Unicode general categories P or S                                       |
| `pUnicodeWhitespace`  | Zs category + tab/LF/FF/CR                                              |

### Strings / Utilities

| Parser              | Purpose                                                                      |
|---------------------|------------------------------------------------------------------------------|
| `pTakeWhile(pred)`  | Consume characters while predicate holds (alias for `pMany(pSatisfy(pred))`) |
| `pTakeWhile1(pred)` | Same, requires at least one character                                        |
| `pRestOfLine`       | Consume all characters up to (but not including) the next line ending or EOF |
| `pIndent(n)`        | Consume exactly `n` spaces                                                   |
| `pUpTo3Spaces`      | Consume 0–3 leading spaces (very common in block parsing)                    |

---

## 3. AST Types

The `:commonmark` module owns the AST and all CommonMark-specific parsers.
It depends on `:text` (for character primitives) which in turn depends on `:core`.

### Document

```
Document(blocks: List<Block>)
```

### Block nodes

```
ThematicBreak
Heading(level: Int, inlines: List<Inline>)         // ATX or Setext
IndentedCodeBlock(literal: String)
FencedCodeBlock(info: String?, literal: String)
HtmlBlock(literal: String)
LinkReferenceDefinition(label: String, destination: String, title: String?)
Paragraph(inlines: List<Inline>)
BlankLine                                           // consumed, not usually emitted
BlockQuote(blocks: List<Block>)
ListItem(blocks: List<Block>)
BulletList(tight: Boolean, marker: Char, items: List<ListItem>)
OrderedList(tight: Boolean, start: Int, delimiter: Char, items: List<ListItem>)
```

### Inline nodes

```
Text(literal: String)
SoftBreak
HardBreak
CodeSpan(literal: String)
Emphasis(children: List<Inline>)
StrongEmphasis(children: List<Inline>)
Link(destination: String, title: String?, children: List<Inline>)
Image(destination: String, title: String?, alt: String)
Autolink(url: String)
RawHtml(literal: String)
HtmlEntity(literal: String)
```

---

## 4. Block Parsers

Block parsing is a single top-down pass over lines. Each parser returns a
`Block` node or fails, allowing `pOr` chains to try alternatives in precedence
order.

### Leaf Blocks

#### `pThematicBreak`

- Match 0–3 leading spaces via `pUpTo3Spaces`
- Match 3+ of the same character (`-`, `_`, or `*`) optionally interspersed
  with spaces/tabs using `pMany1`
- Assert no other non-whitespace content before the line ending
- Return `ThematicBreak`

#### `pAtxHeading`

- `pUpTo3Spaces`
- `pMany1(pChar('#'))` — length gives heading level (1–6); fail if > 6
- `pSpaceOrTab` or end of line
- `pRestOfLine` for the inline content; strip optional trailing `#` sequence
- Return `Heading(level, inlines = parseInlines(content))`

#### `pSetextHeading`

- One or more non-blank continuation lines (the paragraph-in-progress)
- An underline line: all `=` (level 1) or all `-` (level 2), `pUpTo3Spaces`,
  then `pMany1(pChar('=') or pChar('-'))`
- Lower priority than `pThematicBreak` for `---` lines
- Return `Heading(level, inlines = parseInlines(content))`

#### `pIndentedCodeBlock`

- Each line starts with 4+ spaces (or 1 tab)
- Collect lines, strip 4-space prefix, join with `\n`
- Blank lines within the block are included; trailing blank lines stripped
- Cannot interrupt a paragraph
- Return `IndentedCodeBlock(literal)`

#### `pFencedCodeBlock`

- Opening fence: `pUpTo3Spaces`, then `pMany1(pChar('`'))` (≥ 3) or
  `pMany1(pChar('~'))` (≥ 3), then optional info string
- Content lines until closing fence (same character, same or greater length)
  or EOF
- Info string: no backticks allowed for backtick fences; trim whitespace
- Return `FencedCodeBlock(info, literal)`

#### `pHtmlBlock`

Seven types with distinct start/end conditions — each its own sub-parser:

| Type | Start condition                                             | End condition                           |
|------|-------------------------------------------------------------|-----------------------------------------|
| 1    | `<pre`, `<script`, `<style`, `<textarea` (case-insensitive) | Corresponding closing tag               |
| 2    | `<!--`                                                      | `-->`                                   |
| 3    | `<?`                                                        | `?>`                                    |
| 4    | `<!` + ASCII letter                                         | `>`                                     |
| 5    | `<![CDATA[`                                                 | `]]>`                                   |
| 6    | Block-level tag name (open or close)                        | Blank line                              |
| 7    | Any complete open/close tag not in type 6 set               | Blank line (cannot interrupt paragraph) |

- `pOr` over types 1–7 in order
- Return `HtmlBlock(literal)`

#### `pLinkReferenceDefinition`

- `pUpTo3Spaces`, `pChar('[')`, label (no unescaped `]`, ≤ 999 chars),
  `pString("]:")`, optional whitespace including one line ending,
  destination (`pLinkDestination`), optional title (`pLinkTitle`)
- Store in a reference map keyed by normalised label (fold case, collapse
  whitespace) — must be done before inline resolution
- Return `LinkReferenceDefinition`

#### `pParagraph`

- One or more non-blank lines not matching any higher-priority block
- Trim leading spaces (up to 3) on continuation lines
- Inline content parsed in a second pass
- Return `Paragraph(inlines)`

### Container Blocks

#### `pBlockQuote`

- Each line: `pUpTo3Spaces`, `pChar('>')`, optional single space
- Lazy continuation: paragraph content may omit `>`
- Recursively parse the stripped content as blocks
- Return `BlockQuote(blocks)`

#### `pListItem`

- Bullet: `pUpTo3Spaces`, bullet marker (`-`, `+`, `*`), 1–4 spaces
- Ordered: `pUpTo3Spaces`, 1–9 digits, `.` or `)`, 1–4 spaces
- Record indentation width (marker width + space width) for continuation
- First block: rest of first line; subsequent blocks: lines indented by the
  recorded width
- Return `ListItem(blocks)`

#### `pList` (wraps `pListItem`)

- Collect consecutive `pListItem`s with compatible marker type and character
- Determine tight vs. loose: loose if any item contains a blank line, or if
  blank lines appear between items
- Return `BulletList` or `OrderedList`

---

## 5. Inline Parsers

Inline parsing runs over the plain-text content collected during the block
pass. The entry point is `pInlines`, which repeatedly applies `pInline` via
`pMany1`.

### `pInline` (ordered choice)

```
pOr(
    pBackslashEscape,
    pHtmlEntity,
    pCodeSpan,
    pAutolink,
    pRawHtml,
    pLineBreak,        // hard or soft
    pEmphasisOrStrong, // delimiter-run based
    pLink,
    pImage,
    pText,             // fallback: consume one character as literal text
)
```

#### `pBackslashEscape`

- `pChar('\\')` followed by `pAsciiPunctuation`
- Return `Text(literal = char.toString())`

#### `pHtmlEntity`

- Named: `pChar('&')`, entity name (`pTakeWhile1(isLetter or isDigit)`),
  `pChar(';')`
- Decimal: `&#` + digits + `;`
- Hex: `&#x` + hex digits + `;`
- Return `HtmlEntity(literal)` — renderer resolves to Unicode code point

#### `pCodeSpan`

- Opening backtick run: `pMany1(pChar('`'))` — record length N
- Content: any characters (including line endings normalised to spaces) until
  a closing run of exactly N backticks
- Strip one leading/trailing space if content starts and ends with a space
  and is not all spaces
- Return `CodeSpan(literal)`

#### `pAutolink`

- `pChar('<')`, URI or email address, `pChar('>')`
- URI: scheme + `:` + non-whitespace/control chars (up to 32 chars after `<`)
- Email: local part + `@` + domain
- Return `Autolink(url)`

#### `pRawHtml`

- Open tag, closing tag, HTML comment, processing instruction, declaration,
  or CDATA section
- Inline variant only (no block-level check here)
- Return `RawHtml(literal)`

#### `pLineBreak`

- Hard break: `pMany1(pSpaceOrTab)` (2+) then `pLineEnding`, or
  `pChar('\\')` then `pLineEnding`
- Soft break: `pOptional(pSpaceOrTab)` then `pLineEnding` (not hard)
- Return `HardBreak` or `SoftBreak`

#### `pEmphasisOrStrong`

Emphasis is the most complex part of the spec (rules 1–17). The approach:

1. **Scan pass** — collect all delimiter runs (`*` or `_`), tagging each as
   left-flanking, right-flanking, or both, per the spec rules.
2. **Match pass** — walk the delimiter stack to pair openers with closers;
   prefer the innermost match; handle length-3 rule for mixed `**`/`*`.
3. Emit `Emphasis` (1 delimiter) or `StrongEmphasis` (2 delimiters) wrapping
   the parsed inlines between matched runs.

Helper parsers:
- `pDelimiterRun` — `pMany1(pChar('*'))` or `pMany1(pChar('_'))`
- `pLeftFlankingDelimiter` / `pRightFlankingDelimiter` — use lookahead and
  lookbehind on surrounding characters

#### `pLink`

- `pChar('[')`, inline content (`pInlines` — recursive), `pChar(']')`
- Followed by:
  - Inline: `(destination title?)` — `pLinkDestination`, optional
    `pLinkTitle`
  - Full reference: `[label]`
  - Collapsed reference: `[]`
  - Shortcut reference: nothing (uses the link text as label)
- Resolve reference against the link-reference map built in the block pass
- Return `Link(destination, title, children)`

#### `pImage`

- `pString("![")` then same suffix as `pLink`
- Alt text is the plain-text rendering of the inline children
- Return `Image(destination, title, alt)`

#### `pText`

- Fallback: consume one character that was not claimed by any other parser
- Optimisation: `pTakeWhile1` over "safe" characters (not `\`, `` ` ``, `*`,
  `_`, `[`, `!`, `<`, `&`, line endings) to batch runs of plain text
- Return `Text(literal)`

### Shared inline helpers

| Parser             | Purpose                                                                       |
|--------------------|-------------------------------------------------------------------------------|
| `pLinkDestination` | Angle-bracket form `<...>` or bare form (balanced parens, no spaces/controls) |
| `pLinkTitle`       | `"..."`, `'...'`, or `(...)` with backslash escapes inside                    |
| `pLinkLabel`       | `[...]` — case-fold and normalise whitespace for lookup key                   |

---

## 6. Top-Level Document Parser

```
pDocument = pMap(pMany(pBlock)) { blocks -> Document(blocks) }

pBlock = pOr(
    pBlankLine,             // consumed silently
    pThematicBreak,
    pAtxHeading,
    pFencedCodeBlock,
    pHtmlBlock,
    pLinkReferenceDefinition,
    pBlockQuote,
    pList,
    pSetextHeading,         // after pList (avoids `---` ambiguity)
    pIndentedCodeBlock,     // after paragraph check
    pParagraph,             // fallback
)
```

---

## 7. Module Layout

Three Gradle subprojects with a strict dependency chain:

```
:core  ←  :text  ←  :commonmark
```

```
core/
  src/commonMain/kotlin/parsek/
    Parser.kt
    ParserInput.kt
    ParseResult.kt
    Parsers.kt              // pSatisfy, pAnd, pOr, pMap, pBind,
                            // pRepeat, pSequence, pMany, pMany1,
                            // pOptional, pEof, pAny, pNot,
                            // pLookAhead, pSepBy, pSepBy1, pBetween

text/
  src/commonMain/kotlin/parsek/text/
    TextParsers.kt          // pChar, pString, pDigit, pHexDigit,
                            // pLetter, pSpace, pTab, pSpaceOrTab,
                            // pLineEnding, pBlankLine,
                            // pAsciiPunctuation, pUnicodePunctuation,
                            // pUnicodeWhitespace, pTakeWhile,
                            // pTakeWhile1, pRestOfLine,
                            // pIndent, pUpTo3Spaces

commonmark/
  src/commonMain/kotlin/parsek/commonmark/
    ast/
      Block.kt
      Inline.kt
      Document.kt
    parser/
      block/
        ThematicBreakParser.kt
        HeadingParser.kt
        CodeBlockParser.kt
        HtmlBlockParser.kt
        LinkReferenceDefinitionParser.kt
        ParagraphParser.kt
        BlockQuoteParser.kt
        ListParser.kt
      inline/
        CodeSpanParser.kt
        EmphasisParser.kt
        LinkParser.kt
        AutolinkParser.kt
        RawHtmlParser.kt
        EntityParser.kt
        LineBreakParser.kt
        TextParser.kt
      DocumentParser.kt     // pDocument entry point
```

---

## 8. Key Implementation Notes

- **Two-pass design** — collect link reference definitions during the block
  pass; resolve link references during the inline pass. Thread the reference
  map through via the `U` (user context) type parameter in `ParserInput`.
- **Tab expansion** — tabs in block-structure contexts are expanded to the
  next 4-column tab stop before the block parsers run; tabs inside code
  content are passed through literally.
- **Lazy continuation** — block-quote and list-item parsers must handle lazy
  paragraph continuation lines (lines that omit the `>` or indentation prefix
  but are still part of a paragraph inside the container).
- **Setext vs. thematic break** — a `---` line is a thematic break when it
  stands alone, but a Setext heading underline when preceded by paragraph
  text. The `pSetextHeading` parser must be tried while building a paragraph,
  not as a top-level alternative.
- **Emphasis delimiter stack** — do not try to parse emphasis purely with
  combinators. The spec's 17 rules are best handled with a dedicated
  delimiter-stack algorithm operating on a pre-tokenised run of inlines.
- **Tight vs. loose lists** — determined retroactively after all list items
  are parsed by inspecting whether blank lines appear within or between items.
- **Syntax highlighting** — the highlight infrastructure (`Span`, `TokenType`,
  `SpanSink`, `pTag`) exists in `parsek.commonmark.highlight` and is intentionally
  **not** a requirement of this plan. Block and inline parsers are kept generic
  over the user-context type `U` so they can run with or without a `SpanSink`.
  A dedicated `pTag` wrapping layer is planned as a follow-on phase once the
  CommonMark spec suite is green. See `HIGHLIGHT_PLAN.md`.

---

## 9. Phases & PRs

Each PR targets a single module, ships with tests, and leaves the build green.
PRs within a phase can be worked in parallel; phases must be completed in order.

---

### Phase 0 — Core Combinators

> Module: `:core`

| PR | Combinators | Notes |
|----|-------------|-------|
| 0.1 | `pEof`, `pAny` | Trivial primitives; unblock everything downstream |
| 0.2 | `pNot`, `pLookAhead` | Lookahead pair; needed for flanking-delimiter detection and negative guards |
| 0.3 | `pSepBy`, `pSepBy1` | Separated-list pair; needed for link labels and attribute lists |
| 0.4 | `pBetween` | Sugar over open/inner/close sequences; used throughout inline parsing |

---

### Phase 1 — Text Primitives

> Module: `:text` — depends on Phase 0

| PR | Parsers | Notes |
|----|---------|-------|
| 1.1 | `pDigit`, `pHexDigit`, `pLetter`, `pSpace`, `pTab`, `pSpaceOrTab` | Basic character classifiers |
| 1.2 | `pLineEnding`, `pBlankLine`, `pRestOfLine` | Line-level primitives; normalise CR/CRLF/LF to `\n` |
| 1.3 | `pAsciiPunctuation`, `pUnicodePunctuation`, `pUnicodeWhitespace` | Character-category parsers; needed for emphasis flanking rules |
| 1.4 | `pTakeWhile`, `pTakeWhile1`, `pIndent`, `pUpTo3Spaces` | Utility/combinator wrappers; `pUpTo3Spaces` used in nearly every block parser |

---

### Phase 2 — CommonMark Module Scaffold + AST

> Module: `:commonmark` (new) — depends on Phase 1

| PR | Contents | Notes |
|----|----------|-------|
| 2.1 | Gradle subproject wiring (`build.gradle.kts`, `settings.gradle.kts`) + empty source set | Gets the module building before any real code lands |
| 2.2 | `Block.kt`, `Inline.kt`, `Document.kt` AST types | Pure data; no parsers yet. Establishes the types all subsequent PRs return |

---

### Phase 3 — Leaf Block Parsers

> Module: `:commonmark` — depends on Phase 2

Each PR adds one parser file under `parser/block/` plus its tests.

| PR | Parser | Key complexity |
|----|--------|---------------|
| 3.1 | `pThematicBreak` | Disambiguate from `---` Setext underline via ordering |
| 3.2 | `pAtxHeading` | Strip trailing `#` run; delegate inline content to a stub |
| 3.3 | `pIndentedCodeBlock` | 4-space rule; cannot interrupt paragraph; strip trailing blank lines |
| 3.4 | `pFencedCodeBlock` | Match fence character and length; parse optional info string |
| 3.5 | `pHtmlBlock` | All 7 type sub-parsers; types 1–6 can interrupt paragraphs, type 7 cannot |
| 3.6 | `pLinkReferenceDefinition` | Normalise label (case-fold, collapse whitespace); store in reference map |
| 3.7 | `pSetextHeading` + `pParagraph` | Intertwined — Setext underline is detected while accumulating paragraph lines |

---

### Phase 4 — Container Block Parsers

> Module: `:commonmark` — depends on Phase 3

| PR | Parser | Key complexity |
|----|--------|---------------|
| 4.1 | `pBlockQuote` | Lazy continuation lines; recursive block parsing on stripped content |
| 4.2 | `pListItem` + `pList` | Track marker indentation for continuation; retroactive tight/loose determination |

---

### Phase 5 — Inline Parsers

> Module: `:commonmark` — depends on Phase 4 (needs `pParagraph` to feed inline content)

| PR | Parser(s) | Key complexity |
|----|-----------|---------------|
| 5.1 | `pBackslashEscape`, `pHtmlEntity` | Simple; good warm-up for inline pass |
| 5.2 | `pCodeSpan` | Variable-length backtick fence; strip leading/trailing space rule |
| 5.3 | `pLineBreak` | Distinguish hard (2+ spaces or `\`) from soft (plain line ending) |
| 5.4 | `pAutolink`, `pRawHtml` | Angle-bracket disambiguation; URI vs. email heuristics |
| 5.5 | `pLinkDestination`, `pLinkTitle`, `pLinkLabel` | Shared helpers used by both `pLink` and `pImage` |
| 5.6 | `pLink`, `pImage` | Inline, full-reference, collapsed-reference, and shortcut forms; resolve against reference map |
| 5.7 | `pEmphasisOrStrong` | Delimiter-stack algorithm; most complex PR in the project |
| 5.8 | `pText`, `pInlines` | Fallback text + top-level inline entry point wiring all of Phase 5 together |

---

### Phase 6 — Document Parser & Integration

> Module: `:commonmark` — depends on Phase 5

| PR | Contents | Notes |
|----|----------|-------|
| 6.1 | `pDocument`, `pBlock` — wire all block parsers; thread reference map via `U` context | First end-to-end parse of a complete document |
| 6.2 | CommonMark spec test suite — pull in the official JSON test fixtures and run them against `pDocument` | Compliance baseline; expected to fail many cases initially |
| 6.3 | Fix failing spec tests (iterative) | One PR per failing category (e.g. all emphasis tests, all list tests) until the suite passes |
