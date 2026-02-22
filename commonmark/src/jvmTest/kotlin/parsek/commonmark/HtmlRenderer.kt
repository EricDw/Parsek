package parsek.commonmark

import parsek.commonmark.ast.Block
import parsek.commonmark.ast.Document
import parsek.commonmark.ast.Inline

/**
 * Test-only HTML renderer that converts a CommonMark AST into HTML
 * matching the expected output of the CommonMark 0.31.2 spec.
 */
fun renderHtml(document: Document): String {
    val sb = StringBuilder()
    renderBlocks(sb, document.blocks, tight = false)
    return sb.toString()
}

private fun renderBlocks(sb: StringBuilder, blocks: List<Block>, tight: Boolean) {
    for (block in blocks) {
        renderBlock(sb, block, tight)
    }
}

private fun renderBlock(sb: StringBuilder, block: Block, tight: Boolean) {
    when (block) {
        is Block.ThematicBreak -> sb.append("<hr />\n")

        is Block.Heading -> {
            sb.append("<h${block.level}>")
            renderInlines(sb, block.inlines)
            sb.append("</h${block.level}>\n")
        }

        is Block.IndentedCodeBlock -> {
            sb.append("<pre><code>")
            sb.append(escapeHtml(block.literal))
            sb.append("</code></pre>\n")
        }

        is Block.FencedCodeBlock -> {
            if (block.info.isNullOrEmpty()) {
                sb.append("<pre><code>")
            } else {
                // Info string: first word is the language class; resolve escapes and entities
                val lang = resolveEntitiesInString(resolveBackslashEscapes(block.info.split(' ', '\t').first()))
                sb.append("<pre><code class=\"language-${escapeHtml(lang)}\">")
            }
            sb.append(escapeHtml(block.literal))
            sb.append("</code></pre>\n")
        }

        is Block.HtmlBlock -> {
            sb.append(block.literal)
        }

        is Block.Paragraph -> {
            if (tight) {
                renderInlines(sb, block.inlines)
                sb.append("\n")
            } else {
                sb.append("<p>")
                renderInlines(sb, block.inlines)
                sb.append("</p>\n")
            }
        }

        is Block.BlockQuote -> {
            sb.append("<blockquote>\n")
            renderBlocks(sb, block.blocks, tight = false)
            sb.append("</blockquote>\n")
        }

        is Block.BulletList -> {
            sb.append("<ul>\n")
            for (item in block.items) {
                renderListItem(sb, item, block.tight)
            }
            sb.append("</ul>\n")
        }

        is Block.OrderedList -> {
            if (block.start == 1) {
                sb.append("<ol>\n")
            } else {
                sb.append("<ol start=\"${block.start}\">\n")
            }
            for (item in block.items) {
                renderListItem(sb, item, block.tight)
            }
            sb.append("</ol>\n")
        }

        is Block.ListItem -> {
            // ListItem should only appear inside a list; render standalone for safety
            renderListItem(sb, block, tight = false)
        }

        is Block.BlankLine -> { /* filtered out */ }
        is Block.LinkReferenceDefinition -> { /* filtered out */ }
    }
}

private fun renderListItem(sb: StringBuilder, item: Block.ListItem, tight: Boolean) {
    sb.append("<li>")
    if (tight) {
        // Tight: render content without <p> wrappers, inline
        val rendered = StringBuilder()
        renderBlocks(rendered, item.blocks, tight = true)
        val trimmed = rendered.toString().trimEnd('\n')
        if (trimmed.isNotEmpty()) {
            sb.append(trimmed)
        }
    } else {
        sb.append("\n")
        renderBlocks(sb, item.blocks, tight = false)
    }
    sb.append("</li>\n")
}

private fun renderInlines(sb: StringBuilder, inlines: List<Inline>) {
    for (inline in inlines) {
        renderInline(sb, inline)
    }
}

private fun renderInline(sb: StringBuilder, inline: Inline) {
    when (inline) {
        is Inline.Text -> sb.append(escapeHtml(inline.literal))
        is Inline.SoftBreak -> sb.append("\n")
        is Inline.HardBreak -> sb.append("<br />\n")

        is Inline.CodeSpan -> {
            sb.append("<code>")
            sb.append(escapeHtml(inline.literal))
            sb.append("</code>")
        }

        is Inline.Emphasis -> {
            sb.append("<em>")
            renderInlines(sb, inline.children)
            sb.append("</em>")
        }

        is Inline.StrongEmphasis -> {
            sb.append("<strong>")
            renderInlines(sb, inline.children)
            sb.append("</strong>")
        }

        is Inline.Link -> {
            val dest = resolveEntitiesInString(inline.destination)
            sb.append("<a href=\"${escapeHtml(percentEncodeUrl(dest))}\"")
            if (inline.title != null) {
                sb.append(" title=\"${escapeHtml(resolveEntitiesInString(inline.title))}\"")
            }
            sb.append(">")
            renderInlines(sb, inline.children)
            sb.append("</a>")
        }

        is Inline.Image -> {
            val dest = resolveEntitiesInString(inline.destination)
            sb.append("<img src=\"${escapeHtml(percentEncodeUrl(dest))}\"")
            val altText = if (inline.children.isNotEmpty()) {
                flattenInlinesToText(inline.children)
            } else {
                inline.alt
            }
            sb.append(" alt=\"${escapeHtml(altText)}\"")
            if (inline.title != null) {
                sb.append(" title=\"${escapeHtml(resolveEntitiesInString(inline.title))}\"")
            }
            sb.append(" />")
        }

        is Inline.Autolink -> {
            val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*:").containsMatchIn(inline.url)
            val href = if (hasScheme) inline.url else "mailto:${inline.url}"
            sb.append("<a href=\"${escapeHtml(percentEncodeUrl(href))}\">")
            sb.append(escapeHtml(inline.url))
            sb.append("</a>")
        }

        is Inline.RawHtml -> sb.append(inline.literal)

        is Inline.HtmlEntity -> {
            val resolved = resolveHtmlEntity(inline.literal)
            if (resolved != null) {
                sb.append(escapeHtml(resolved))
            } else {
                // Unknown entity: escape the '&' so it renders as literal text
                sb.append("&amp;")
                sb.append(escapeHtml(inline.literal.removePrefix("&")))
            }
        }
    }
}

/**
 * Resolves backslash escapes in a string (only ASCII punctuation per §2.4).
 */
private fun resolveBackslashEscapes(s: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
        if (s[i] == '\\' && i + 1 < s.length && isRendererAsciiPunctuation(s[i + 1])) {
            sb.append(s[i + 1])
            i += 2
        } else {
            sb.append(s[i])
            i++
        }
    }
    return sb.toString()
}

private fun isRendererAsciiPunctuation(c: Char): Boolean =
    c in '!'..'/' || c in ':'..'@' || c in '['..'`' || c in '{'..'~'

/**
 * Flattens inline nodes to plain text for use in image alt attributes.
 * Recursively extracts text content from all inline children.
 */
private fun flattenInlinesToText(inlines: List<Inline>): String {
    val sb = StringBuilder()
    for (inline in inlines) {
        when (inline) {
            is Inline.Text -> sb.append(inline.literal)
            is Inline.CodeSpan -> sb.append(inline.literal)
            is Inline.SoftBreak -> sb.append(" ")
            is Inline.HardBreak -> sb.append(" ")
            is Inline.Emphasis -> sb.append(flattenInlinesToText(inline.children))
            is Inline.StrongEmphasis -> sb.append(flattenInlinesToText(inline.children))
            is Inline.Link -> sb.append(flattenInlinesToText(inline.children))
            is Inline.Image -> {
                if (inline.children.isNotEmpty()) {
                    sb.append(flattenInlinesToText(inline.children))
                } else {
                    sb.append(inline.alt)
                }
            }
            is Inline.Autolink -> sb.append(inline.url)
            is Inline.RawHtml -> {} // skip
            is Inline.HtmlEntity -> {
                val resolved = resolveHtmlEntity(inline.literal)
                if (resolved != null) sb.append(resolved)
            }
        }
    }
    return sb.toString()
}

/**
 * Resolves HTML entities within a plain string (e.g. in fenced code info strings).
 * Entities that don't resolve are kept as-is.
 */
private fun resolveEntitiesInString(s: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
        if (s[i] == '&') {
            val semi = s.indexOf(';', i + 1)
            if (semi != -1 && semi - i <= 32) {
                val entity = s.substring(i, semi + 1)
                val resolved = resolveHtmlEntity(entity)
                if (resolved != null) {
                    sb.append(resolved)
                    i = semi + 1
                    continue
                }
            }
        }
        sb.append(s[i])
        i++
    }
    return sb.toString()
}

/**
 * Percent-encodes a URL per the CommonMark spec.
 *
 * - Preserves existing `%XX` sequences (no double-encoding).
 * - Encodes non-ASCII characters as UTF-8 percent-encoded bytes.
 * - Encodes unsafe ASCII characters (spaces, quotes, backslash, etc.).
 * - Preserves safe ASCII: unreserved chars, sub-delimiters, and `:/?#[]@!$&'()*+,;=`.
 */
private fun percentEncodeUrl(url: String): String {
    val sb = StringBuilder()
    val bytes = url.toByteArray(Charsets.UTF_8)
    var i = 0
    while (i < bytes.size) {
        val b = bytes[i].toInt() and 0xFF
        val c = b.toChar()
        if (c == '%' && i + 2 < bytes.size) {
            val h1 = bytes[i + 1].toInt().toChar()
            val h2 = bytes[i + 2].toInt().toChar()
            if (isHexDigit(h1) && isHexDigit(h2)) {
                // Preserve existing percent-encoded sequence
                sb.append('%')
                sb.append(h1)
                sb.append(h2)
                i += 3
                continue
            }
        }
        if (isSafeUrlChar(c)) {
            sb.append(c)
        } else {
            sb.append('%')
            sb.append(HEX_DIGITS[b shr 4])
            sb.append(HEX_DIGITS[b and 0x0F])
        }
        i++
    }
    return sb.toString()
}

private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()

private fun isHexDigit(c: Char): Boolean =
    c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

/** Characters that should NOT be percent-encoded in URLs (matches cmark). */
private fun isSafeUrlChar(c: Char): Boolean =
    c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' ||
        c == '-' || c == '_' || c == '.' || c == '+' ||
        c == '!' || c == '*' || c == '\'' || c == '(' || c == ')' ||
        c == ',' || c == '%' || c == '#' || c == '@' ||
        c == '?' || c == '=' || c == ';' || c == ':' ||
        c == '/' || c == '&' || c == '$' || c == '~'

private fun escapeHtml(s: String): String {
    val sb = StringBuilder(s.length)
    for (ch in s) {
        when (ch) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}
