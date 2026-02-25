# GitHub Flavoured Markdown (GFM) Plan

A plan for adding GFM extension support to the `:markdown` module. GFM is a
strict superset of CommonMark — it adds five extensions without modifying any
existing behaviour. This plan builds on top of the completed CommonMark parser
(Phase 6) and highlight system (Phase 7).

Reference spec: https://github.github.com/gfm/

---

## GFM Extensions Overview

| Extension | Spec section | Type | Complexity |
|-----------|-------------|------|------------|
| Tables | §4.10 | Leaf block | High |
| Strikethrough | §6.5 | Inline | Medium |
| Task list items | §5.3 | List item modifier | Low |
| Autolinks (extended) | §6.9 | Inline | High |
| Disallowed raw HTML | §6.11 | Inline (filter) | Low |

---

## 1. AST Extensions

### New block types (`Block.kt`)

```
Table(
    alignments: List<Alignment>,     // per-column alignment
    header: TableRow,                // the header row
    body: List<TableRow>,            // zero or more data rows
)

TableRow(cells: List<TableCell>)

TableCell(inlines: List<Inline>)

enum class Alignment { LEFT, CENTER, RIGHT, NONE }
```

### New inline types (`Inline.kt`)

```
Strikethrough(children: List<Inline>)

// Extended autolinks — bare URLs and emails without angle brackets
ExtendedAutolink(url: String)
```

### Modified types

```
// ListItem gains an optional checkbox field
ListItem(
    blocks: List<Block>,
    checked: Boolean? = null,   // null = not a task item, true = [x], false = [ ]
)
```

> `DisallowedRawHtml` does not need an AST change — it is a rendering concern
> that filters `<` to `&lt;` on specific tags. We handle it as a flag or
> post-processing step rather than a new node type.

---

## 2. TokenType Extensions (`TokenType.kt`)

New highlight token types:

```
// Table
TableDelimiter     // pipe characters `|` and delimiter row `---`/`:---:`
TableHeaderCell    // content of a header cell
TableCell          // content of a body cell

// Strikethrough
StrikethroughMarker  // the `~~` delimiter runs

// Task list
TaskMarker         // the `[ ]` or `[x]` checkbox marker

// Extended autolink
ExtendedAutolinkUrl  // a bare URL or email (no angle brackets)
```

---

## 3. Phases & PRs

Each PR targets a single concern, ships with tests, and leaves the build green.

---

### Phase 8 — GFM: Tables

> Module: `:markdown` — the most complex extension

Tables are a leaf block type. A table is recognised when a line of pipe-
separated cells is followed by a delimiter row of `---`/`:---:`/`:---`/`---:`
cells.

#### Rules

- **Header row**: cells separated by `|`, optional leading/trailing pipes.
  Spaces between pipes and content are trimmed.
- **Delimiter row**: cells containing only hyphens (`-`) with optional leading/
  trailing colons (`:`) for alignment. Must have the same number of cells as the
  header.
- **Body rows**: zero or more rows. Missing cells become empty; excess cells are
  ignored.
- **Cell content**: inline content is parsed inside each cell. Pipes can be
  escaped with `\|`, including inside other inline spans.
- **Termination**: a table ends at the first blank line or the start of another
  block-level structure.
- **Precedence**: tables cannot interrupt a paragraph (the delimiter row would
  look like a Setext heading underline or thematic break).

| PR | Contents | Notes |
|----|----------|-------|
| 8.1 | `Table`, `TableRow`, `TableCell`, `Alignment` AST types in `Block.kt` | Pure data; no parser yet |
| 8.2 | `TableParser.kt` — `pTable` parser in `parser/block/` | Parse header row, delimiter row, body rows. Wire into `pBlock` choice chain. Spec examples 198–205 |
| 8.3 | `TableHighlight.kt` — highlight wrapper in `highlight/block/` | Emit `TableDelimiter`, `TableHeaderCell`, `TableCell` spans |

---

### Phase 9 — GFM: Strikethrough

> Module: `:markdown` — inline extension

Strikethrough uses `~~` delimiters, similar to emphasis. Text wrapped in `~~` on
each side produces a `<del>` element.

#### Rules

- A sequence of exactly **two tildes** (`~~`) opens a strikethrough span.
- A matching `~~` closes it.
- Strikethrough cannot span across paragraph boundaries (spec example 492).
- Delimiter flanking rules follow the same logic as emphasis (left-flanking /
  right-flanking based on surrounding characters).
- Strikethrough can nest with emphasis and other inlines.

| PR | Contents | Notes |
|----|----------|-------|
| 9.1 | `Strikethrough` AST type in `Inline.kt` | Pure data |
| 9.2 | `StrikethroughParser.kt` — `pStrikethrough` parser in `parser/inline/` | Integrate into `pInline` choice chain. Spec examples 491–492 |
| 9.3 | `StrikethroughHighlight.kt` — highlight wrapper in `highlight/inline/` | Emit `StrikethroughMarker` spans over `~~` delimiters |

---

### Phase 10 — GFM: Task List Items

> Module: `:markdown` — list item modifier

A task list item is a list item whose first paragraph begins with a checkbox
marker: `[ ]` (unchecked) or `[x]`/`[X]` (checked), followed by whitespace.

#### Rules

- **Marker syntax**: `[`, then a space (unchecked) or `x`/`X` (checked), then `]`.
- The marker must appear at the very start of the first paragraph in a list item.
- The marker is followed by at least one whitespace character before content.
- The marker is replaced with a semantic checkbox when rendered.
- Task list items nest arbitrarily inside bullet or ordered lists.

| PR | Contents | Notes |
|----|----------|-------|
| 10.1 | Add `checked: Boolean?` field to `Block.ListItem` | Default `null` preserves backward compatibility. Update existing tests if needed |
| 10.2 | Modify `ListParser.kt` — detect and parse task list markers | Parse `[ ]`/`[x]`/`[X]` at the start of the first paragraph. Spec examples 279–280 |
| 10.3 | `TaskListHighlight.kt` — highlight wrapper | Emit `TaskMarker` span over the `[ ]`/`[x]` marker |

---

### Phase 11 — GFM: Extended Autolinks

> Module: `:markdown` — inline extension

Extended autolinks recognise bare URLs and email addresses without requiring
angle brackets (`<`/`>`). This is the most complex inline extension.

#### Rules

**Context**: extended autolinks can only appear at the beginning of a line, after
whitespace, or after one of the delimiter characters `*`, `_`, `~`, `(`.

**www autolinks**:
- Triggered by `www.` followed by a valid domain.
- Valid domain: alphanumeric, `_`, `-`, `.` characters. At least one period
  required. No underscores in the last two domain segments.
- Zero or more non-space, non-`<` path characters follow the domain.
- Trailing punctuation (`?`, `!`, `.`, `,`, `:`, `*`, `_`, `~`) is excluded
  from the URL but allowed internally.
- Trailing `)` excluded if closing parens exceed opening parens in the URL.
- If ending in `;` preceded by `&` + alphanumerics (entity reference pattern),
  exclude the entity reference from the URL.
- `<` immediately terminates the autolink.
- The scheme `http://` is prepended automatically.

**URL autolinks**:
- Triggered by `http://`, `https://`, or `ftp://` followed by a valid domain.
- Same trailing-character rules as www autolinks.

**Email autolinks**:
- Local part: one or more of alphanumeric, `.`, `-`, `_`, `+`.
- `@` separator.
- Domain: one or more of alphanumeric, `.`, `-`, `_`. At least one period
  required. Last character cannot be `-` or `_`.
- Trailing `.` excluded from the autolink.
- `mailto:` scheme prepended automatically.

| PR | Contents | Notes |
|----|----------|-------|
| 11.1 | `ExtendedAutolink` AST type in `Inline.kt` | Separate from existing `Autolink` (which is the angle-bracket form) |
| 11.2 | `ExtendedAutolinkParser.kt` — www + URL autolink parsing | Context-sensitive recognition, domain validation, trailing-character trimming. Spec examples 621–628 |
| 11.3 | `ExtendedAutolinkParser.kt` — add email autolink parsing | Local-part + `@` + domain rules. Spec examples 629–631 |
| 11.4 | Integrate into `pInline` choice chain | Extended autolinks must be tried before `pText` but after most other inlines |
| 11.5 | `ExtendedAutolinkHighlight.kt` — highlight wrapper | Emit `ExtendedAutolinkUrl` spans |

---

### Phase 12 — GFM: Disallowed Raw HTML

> Module: `:markdown` — inline filter

GFM filters specific HTML tags by replacing their leading `<` with `&lt;`. This
is a rendering concern rather than a parsing change.

#### Disallowed tags

`<title>`, `<textarea>`, `<style>`, `<xmp>`, `<iframe>`, `<noembed>`,
`<noframes>`, `<script>`, `<plaintext>`

Both opening and closing forms are filtered (e.g. `<script>` and `</script>`).

#### Rules

- Only the `<` is replaced; the rest of the tag is left intact.
- This applies to inline raw HTML only, not HTML blocks.
- The filter is case-insensitive.

| PR | Contents | Notes |
|----|----------|-------|
| 12.1 | Add disallowed tag filter to inline `RawHtml` handling | This can be a post-parse filter on `Inline.RawHtml` nodes, or integrated into the renderer. Either way, no new AST type needed |

---

### Phase 13 — GFM Spec Test Suite

> Module: `:markdown`

| PR | Contents | Notes |
|----|----------|-------|
| 13.1 | Import the GFM spec test JSON fixtures | The GFM spec has its own example set (examples 1–671). CommonMark examples overlap; GFM-specific examples are in the extensions sections |
| 13.2 | Run GFM spec tests against the parser | Establish compliance baseline; expect most extension examples to pass from Phases 8–12 |
| 13.3 | Fix failing GFM spec tests (iterative) | One PR per failing category until the suite is green |

---

### Phase 14 — Compose Renderer Updates

> Module: `:compose-renderer`

| PR | Contents | Notes |
|----|----------|-------|
| 14.1 | Render `Table` blocks | Column alignment, header styling, cell borders |
| 14.2 | Render `Strikethrough` inline | Apply `<del>`-style text decoration |
| 14.3 | Render task list checkboxes | Render `checked` field as a visual checkbox |
| 14.4 | Render `ExtendedAutolink` | Clickable link styling, same as existing `Autolink` |
| 14.5 | Apply disallowed raw HTML filter | Filter tags before rendering HTML content |

---

## 4. Key Implementation Notes

- **Strict superset**: all existing CommonMark tests must continue to pass after
  every PR. GFM adds to — but never modifies — CommonMark behaviour.
- **Extension toggle**: consider making GFM extensions opt-in via a configuration
  object or builder pattern on `pDocument`. This keeps the base CommonMark parser
  clean and allows users to pick which extensions they want.
- **Table cell inline parsing**: table cells contain inline content. Pipe
  characters inside inline spans (code spans, emphasis, etc.) must not be
  treated as cell separators. Use the existing inline parser with escaped-pipe
  awareness.
- **Extended autolink context sensitivity**: the recognition rule ("after
  whitespace or `*`, `_`, `~`, `(`") requires the inline parser to track the
  preceding character. Thread this through the parser state or use lookbehind.
- **Strikethrough + emphasis interaction**: `~~` delimiters interact with `*`/`_`
  emphasis delimiters. The delimiter-stack algorithm may need to be extended to
  handle tilde runs alongside asterisk/underscore runs.
- **Backward compatibility**: the `checked: Boolean? = null` default on
  `ListItem` ensures existing code that pattern-matches on `ListItem` continues
  to work without changes.
- **Highlight wrappers**: follow the same pattern established in Phase 7 —
  thin wrappers that compose `pTag` around the generic parser. Do not duplicate
  parsing logic.
