# Parsek Markdown Renderer

A **Compose Multiplatform** renderer for [CommonMark](https://commonmark.org)
documents parsed by *Parsek* — a Kotlin Multiplatform parser combinator library.

## Inline Formatting

CommonMark supports several inline constructs:

- **Bold text** is wrapped in double asterisks or underscores.
- *Italic text* uses single asterisks or underscores.
- ***Bold and italic*** can be combined with triple markers.
- Inline `code spans` use backtick delimiters.
- Multi-backtick code spans: `` `backticks` inside code ``.
- You can escape special characters with a backslash: \*not emphasis\*.
- More escapes: \# not a heading, \> not a quote, \[not a link\].

## Links and Autolinks

- Inline links: [Parsek on GitHub](https://github.com/parsek "Parsek repo")
- Reference-style links: [CommonMark] is a spec for Markdown.
- Autolinks use angle brackets: <https://commonmark.org>
- Email autolinks: <user@example.com>

[CommonMark]: https://commonmark.org

## Images

Images are loaded asynchronously via Coil:

![Placeholder image](https://placehold.co/400x200/png "A placeholder image")

![Google logo](https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png "The Google logo")

Linked image (clickable):

[![Click me](https://placehold.co/200x80/4EC9B0/FFFFFF/png?text=Linked+Image)](https://commonmark.org)

## Headings

All six ATX heading levels are supported:

# Heading 1
## Heading 2
### Heading 3
#### Heading 4
##### Heading 5
###### Heading 6

Setext Heading Level 1
======================

Setext Heading Level 2
----------------------

## Block Quotes

> Block quotes can contain **rich inline content** and `code`.
>
> They can span multiple paragraphs.
>
> > Nested block quotes are supported too.
> >
> > *With their own formatting.*

> A block quote with a fenced code block inside:
>
> ```
> val x = 42
> ```
>
> And a list:
>
> 1. First
> 2. Second

## Unordered Lists

Tight list (no blank lines between items):

- Apple
- Banana
- Cherry

Loose list (blank lines between items):

- Item one

- Item two with **bold** and *italic*

- Item three

Nested lists:

- Item one
- Item two
    - Nested item A
    - Nested item B
        - Deeply nested
- Item three

## Ordered Lists

1. First step
2. Second step with `inline code`
3. Third step
4. Fourth step

Starting from an arbitrary number:

3. Third
4. Fourth
5. Fifth

Nested ordered list:

1. Outer item one
    1. Inner item one
    2. Inner item two
2. Outer item two

## Fenced Code Blocks

```kotlin
fun main() {
    val parser = pDocumentHighlight()
    val sink = SpanSink()
    val input = ParserInput.of(markdown.toList(), sink)
    val doc = (parser(input) as Success).value
    println("Parsed ${doc.blocks.size} blocks")
}
```

A block with a different fence style:

~~~
Plain text in a tilde-fenced block.
No info string here.
~~~

And a block with an info string:

```json
{
  "name": "parsek",
  "version": "0.1.0",
  "modules": ["core", "text", "commonmark"]
}
```

## Indented Code Blocks

    This is an indented code block.
    Each line is indented by four spaces.
    No fence characters needed.

## Thematic Breaks

Three different thematic break styles:

---

***

___

## HTML Blocks

<div>
  <p>Raw HTML blocks are preserved as-is.</p>
</div>

## Inline HTML and Entities

Inline raw HTML: This has a <br/> line break and <em>manual emphasis</em>.

HTML entities: &copy; 2025 Parsek &mdash; built with &hearts; &amp; Kotlin. Snowman: &#9731;

## Hard and Soft Breaks

This line has a hard break at the end\
so this continues on a new line.

This line has a soft break
which renders as a space in the output.

## Combining Constructs

> Here is a block quote containing a list:
>
> 1. First **bold** item
> 2. Second *italic* item
> 3. Third `code` item
>
> And a paragraph with a [link](https://example.com).

---

*That's the full tour of CommonMark constructs rendered by Parsek!*
