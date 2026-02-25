# Parsek CommonMark Test Document

A paragraph with a `code span`, a backslash escape \*, and an entity &amp;.

---

## Block Structures

Setext Level One
================

Setext Level Two
----------------

### Block Quote

> This is a block quote.
> It spans two lines.

### Tight Bullet List

- Alpha
- Beta
- Gamma

### Tight Ordered List

1. First
2. Second
3. Third

### Loose Bullet List

- Loose first

- Loose second

### Fenced Code Block

~~~
val x = 42
println(x)
~~~

### Indented Code Block

Paragraph before the indented block.

    val y = 100
    println(y)

### HTML Block

<!-- HTML comment block -->

### Link Reference Definition

[parsek]: https://github.com/EricDw/Parsek "Parsek on GitHub"

A final paragraph referencing [parsek].