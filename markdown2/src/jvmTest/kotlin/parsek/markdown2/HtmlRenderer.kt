package parsek.markdown2

import parsek.markdown.ast.Block
import parsek.markdown.ast.Document
import parsek.markdown.ast.Inline
import parsek.markdown.filterDisallowedRawHtml

/**
 * Test-only HTML renderer for markdown2 AST.
 *
 * @param gfmTagFilter when `true`, applies the GFM disallowed raw HTML filter
 *   (§6.11) to HTML blocks and inline raw HTML.
 */
fun renderHtml(document: Document, gfmTagFilter: Boolean = false): String {
    val sb = StringBuilder()
    renderBlocks(sb, document.blocks, gfmTagFilter)
    return sb.toString()
}

private fun renderBlocks(sb: StringBuilder, blocks: List<Block>, gfmTagFilter: Boolean = false) {
    for (block in blocks) {
        renderBlock(sb, block, gfmTagFilter)
    }
}

private fun renderBlock(sb: StringBuilder, block: Block, gfmTagFilter: Boolean = false) {
    when (block) {
        is Block.ThematicBreak -> sb.append("<hr />\n")

        is Block.Heading -> {
            sb.append("<h${block.level}>")
            renderInlines(sb, block.inlines, gfmTagFilter)
            sb.append("</h${block.level}>\n")
        }

        is Block.IndentedCodeBlock -> {
            sb.append("<pre><code>")
            sb.append(escapeHtml(block.literal))
            sb.append("</code></pre>\n")
        }

        is Block.FencedCodeBlock -> {
            val info = block.info
            if (info.isNullOrEmpty()) {
                sb.append("<pre><code>")
            } else {
                val lang = info.split(' ', '\t').first()
                sb.append("<pre><code class=\"language-${escapeHtml(lang)}\">")
            }
            sb.append(escapeHtml(block.literal))
            sb.append("</code></pre>\n")
        }

        is Block.HtmlBlock -> {
            val literal = if (gfmTagFilter) filterDisallowedRawHtml(block.literal) else block.literal
            sb.append(literal)
        }

        is Block.Paragraph -> {
            sb.append("<p>")
            renderInlines(sb, block.inlines, gfmTagFilter)
            sb.append("</p>\n")
        }

        is Block.BlockQuote -> {
            sb.append("<blockquote>\n")
            renderBlocks(sb, block.blocks, gfmTagFilter)
            sb.append("</blockquote>\n")
        }

        is Block.BulletList -> {
            sb.append("<ul>\n")
            for (item in block.items) {
                renderListItem(sb, item, block.tight, gfmTagFilter)
            }
            sb.append("</ul>\n")
        }

        is Block.OrderedList -> {
            if (block.start == 1) sb.append("<ol>\n")
            else sb.append("<ol start=\"${block.start}\">\n")
            for (item in block.items) {
                renderListItem(sb, item, block.tight, gfmTagFilter)
            }
            sb.append("</ol>\n")
        }

        is Block.Table -> renderTable(sb, block, gfmTagFilter)

        is Block.BlankLine -> {}
        is Block.LinkReferenceDefinition -> {}
        is Block.ListItem -> {}
        is Block.TableRow -> {}
        is Block.TableCell -> {}
    }
}

private fun renderTable(sb: StringBuilder, table: Block.Table, gfmTagFilter: Boolean) {
    sb.append("<table>\n")
    sb.append("<thead>\n")
    sb.append("<tr>\n")
    for (i in table.header.cells.indices) {
        val cell = table.header.cells[i]
        val align = table.alignments.getOrElse(i) { Block.Alignment.NONE }
        val alignAttr = when (align) {
            Block.Alignment.LEFT -> " align=\"left\""
            Block.Alignment.RIGHT -> " align=\"right\""
            Block.Alignment.CENTER -> " align=\"center\""
            Block.Alignment.NONE -> ""
        }
        sb.append("<th$alignAttr>")
        renderInlines(sb, cell.inlines, gfmTagFilter)
        sb.append("</th>\n")
    }
    sb.append("</tr>\n")
    sb.append("</thead>\n")
    if (table.body.isNotEmpty()) {
        sb.append("<tbody>\n")
        for (row in table.body) {
            sb.append("<tr>\n")
            for (i in row.cells.indices) {
                val cell = row.cells[i]
                val align = table.alignments.getOrElse(i) { Block.Alignment.NONE }
                val alignAttr = when (align) {
                    Block.Alignment.LEFT -> " align=\"left\""
                    Block.Alignment.RIGHT -> " align=\"right\""
                    Block.Alignment.CENTER -> " align=\"center\""
                    Block.Alignment.NONE -> ""
                }
                sb.append("<td$alignAttr>")
                renderInlines(sb, cell.inlines, gfmTagFilter)
                sb.append("</td>\n")
            }
            sb.append("</tr>\n")
        }
        sb.append("</tbody>\n")
    }
    sb.append("</table>\n")
}

private fun renderListItem(sb: StringBuilder, item: Block.ListItem, tight: Boolean, gfmTagFilter: Boolean = false) {
    val checkbox = when (item.checked) {
        true -> "<input checked=\"\" disabled=\"\" type=\"checkbox\"> "
        false -> "<input disabled=\"\" type=\"checkbox\"> "
        null -> ""
    }
    if (tight) {
        // In tight lists, unwrap paragraphs to inline content
        if (item.blocks.isEmpty()) {
            sb.append("<li>$checkbox")
        } else {
            val firstBlock = item.blocks.first()
            if (firstBlock is Block.Paragraph) {
                sb.append("<li>$checkbox")
                renderInlines(sb, firstBlock.inlines, gfmTagFilter)
                if (item.blocks.size > 1) sb.append("\n")
            } else {
                sb.append("<li>$checkbox\n")
                renderBlock(sb, firstBlock, gfmTagFilter)
            }
            for (block in item.blocks.drop(1)) {
                if (block is Block.Paragraph) {
                    renderInlines(sb, block.inlines, gfmTagFilter)
                } else {
                    renderBlock(sb, block, gfmTagFilter)
                }
            }
        }
        sb.append("</li>\n")
    } else {
        if (item.blocks.isEmpty()) {
            sb.append("<li>$checkbox")
        } else {
            sb.append("<li>\n")
            // For loose lists with checkboxes, insert checkbox at start of first paragraph
            if (checkbox.isNotEmpty()) {
                val first = item.blocks.first()
                if (first is Block.Paragraph) {
                    sb.append("<p>$checkbox")
                    renderInlines(sb, first.inlines, gfmTagFilter)
                    sb.append("</p>\n")
                    renderBlocks(sb, item.blocks.drop(1), gfmTagFilter)
                } else {
                    renderBlocks(sb, item.blocks, gfmTagFilter)
                }
            } else {
                renderBlocks(sb, item.blocks, gfmTagFilter)
            }
        }
        sb.append("</li>\n")
    }
}

private fun renderInlines(sb: StringBuilder, inlines: List<Inline>, gfmTagFilter: Boolean = false) {
    for (inline in inlines) {
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
                renderInlines(sb, inline.children, gfmTagFilter)
                sb.append("</em>")
            }
            is Inline.StrongEmphasis -> {
                sb.append("<strong>")
                renderInlines(sb, inline.children, gfmTagFilter)
                sb.append("</strong>")
            }
            is Inline.Link -> {
                sb.append("<a href=\"${escapeHtml(percentEncode(inline.destination))}\"")
                val linkTitle = inline.title
                if (linkTitle != null) {
                    sb.append(" title=\"${escapeHtml(linkTitle)}\"")
                }
                sb.append(">")
                renderInlines(sb, inline.children, gfmTagFilter)
                sb.append("</a>")
            }
            is Inline.Image -> {
                val alt = renderInlinesPlainText(inline.children)
                sb.append("<img src=\"${escapeHtml(percentEncode(inline.destination))}\" alt=\"${escapeHtml(alt)}\"")
                val imgTitle = inline.title
                if (imgTitle != null) {
                    sb.append(" title=\"${escapeHtml(imgTitle)}\"")
                }
                sb.append(" />")
            }
            is Inline.Autolink -> {
                sb.append("<a href=\"${escapeHtml(percentEncode(inline.url))}\">")
                sb.append(escapeHtml(if (inline.url.startsWith("mailto:")) inline.url.removePrefix("mailto:") else inline.url))
                sb.append("</a>")
            }
            is Inline.RawHtml -> {
                val literal = if (gfmTagFilter) filterDisallowedRawHtml(inline.literal) else inline.literal
                sb.append(literal)
            }
            is Inline.HtmlEntity -> sb.append(inline.literal)
            is Inline.Strikethrough -> {
                sb.append("<del>")
                renderInlines(sb, inline.children, gfmTagFilter)
                sb.append("</del>")
            }
            is Inline.ExtendedAutolink -> {
                val displayUrl = when {
                    inline.url.startsWith("mailto:") -> inline.url.removePrefix("mailto:")
                    inline.url.startsWith("http://www.") -> inline.url.removePrefix("http://")
                    else -> inline.url
                }
                sb.append("<a href=\"${escapeHtml(inline.url)}\">")
                sb.append(escapeHtml(displayUrl))
                sb.append("</a>")
            }
        }
    }
}

/**
 * Renders inline content to plain text (used for image alt text).
 */
private fun renderInlinesPlainText(inlines: List<Inline>): String = buildString {
    for (inline in inlines) {
        when (inline) {
            is Inline.Text -> append(inline.literal)
            is Inline.SoftBreak -> append("\n")
            is Inline.HardBreak -> append("\n")
            is Inline.CodeSpan -> append(inline.literal)
            is Inline.Emphasis -> append(renderInlinesPlainText(inline.children))
            is Inline.StrongEmphasis -> append(renderInlinesPlainText(inline.children))
            is Inline.Link -> append(renderInlinesPlainText(inline.children))
            is Inline.Image -> append(renderInlinesPlainText(inline.children))
            is Inline.Autolink -> {
                val url = inline.url
                append(if (url.startsWith("mailto:")) url.removePrefix("mailto:") else url)
            }
            is Inline.RawHtml -> {}
            is Inline.HtmlEntity -> append(inline.literal)
            is Inline.Strikethrough -> append(renderInlinesPlainText(inline.children))
            is Inline.ExtendedAutolink -> append(inline.url)
        }
    }
}

/**
 * Percent-encodes characters in a URL that need encoding per CommonMark spec.
 * Preserves existing percent-encoded sequences and ASCII safe characters.
 */
private fun percentEncode(url: String): String = buildString {
    var i = 0
    while (i < url.length) {
        val ch = url[i]
        when {
            // Preserve existing percent-encoded sequences
            ch == '%' && i + 2 < url.length &&
                isHexChar(url[i + 1]) && isHexChar(url[i + 2]) -> {
                append(ch)
                append(url[i + 1])
                append(url[i + 2])
                i += 3
            }
            // Safe ASCII characters — no encoding needed
            (ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9') || ch in "-._~:/?#@!$&'()*+,;=" -> {
                append(ch)
                i++
            }
            // Percent-encode everything else
            else -> {
                val bytes = ch.toString().encodeToByteArray()
                for (b in bytes) {
                    append('%')
                    append(HEX_CHARS[(b.toInt() shr 4) and 0xF])
                    append(HEX_CHARS[b.toInt() and 0xF])
                }
                i++
            }
        }
    }
}

private fun isHexChar(ch: Char): Boolean =
    ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'

private val HEX_CHARS = "0123456789ABCDEF"

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
