package parsek.commonmark

import parsek.commonmark.ast.Block
import parsek.commonmark.ast.Inline

/**
 * Renders a list of [Block] nodes as a human-readable tree string.
 *
 * Each level of nesting is indented by two spaces. Multi-line literals
 * are shown with newlines replaced by `↵` and are truncated at 72 characters.
 */
fun printAst(blocks: List<Block>): String = buildString {
    append("Document [${blocks.size} block(s)]\n")
    for (block in blocks) appendBlock(this, block, "  ")
}

private fun appendBlock(sb: StringBuilder, block: Block, indent: String) {
    when (block) {
        Block.ThematicBreak ->
            sb.append("${indent}ThematicBreak\n")

        Block.BlankLine ->
            sb.append("${indent}BlankLine\n")

        is Block.Heading -> {
            sb.append("${indent}Heading(level=${block.level})\n")
            for (inline in block.inlines) appendInline(sb, inline, "$indent  ")
        }

        is Block.Paragraph -> {
            sb.append("${indent}Paragraph\n")
            for (inline in block.inlines) appendInline(sb, inline, "$indent  ")
        }

        is Block.IndentedCodeBlock -> {
            sb.append("${indent}IndentedCodeBlock\n")
            sb.append("$indent  ${compact(block.literal)}\n")
        }

        is Block.FencedCodeBlock -> {
            val info = if (block.info != null) " info=\"${block.info}\"" else ""
            sb.append("${indent}FencedCodeBlock$info\n")
            sb.append("$indent  ${compact(block.literal)}\n")
        }

        is Block.HtmlBlock -> {
            sb.append("${indent}HtmlBlock\n")
            sb.append("$indent  ${compact(block.literal)}\n")
        }

        is Block.LinkReferenceDefinition -> {
            sb.append("${indent}LinkReferenceDefinition\n")
            sb.append("$indent  label:       \"${block.label}\"\n")
            sb.append("$indent  destination: \"${block.destination}\"\n")
            if (block.title != null)
                sb.append("$indent  title:       \"${block.title}\"\n")
        }

        is Block.BlockQuote -> {
            sb.append("${indent}BlockQuote [${block.blocks.size} block(s)]\n")
            for (inner in block.blocks) appendBlock(sb, inner, "$indent  ")
        }

        is Block.BulletList -> {
            sb.append("${indent}BulletList(tight=${block.tight}, marker='${block.marker}')\n")
            for ((i, item) in block.items.withIndex()) {
                sb.append("$indent  Item[$i]\n")
                for (inner in item.blocks) appendBlock(sb, inner, "$indent    ")
            }
        }

        is Block.OrderedList -> {
            sb.append(
                "${indent}OrderedList(" +
                    "tight=${block.tight}, " +
                    "start=${block.start}, " +
                    "delimiter='${block.delimiter}')\n"
            )
            for ((i, item) in block.items.withIndex()) {
                sb.append("$indent  Item[$i]\n")
                for (inner in item.blocks) appendBlock(sb, inner, "$indent    ")
            }
        }

        is Block.ListItem -> {
            sb.append("${indent}ListItem\n")
            for (inner in block.blocks) appendBlock(sb, inner, "$indent  ")
        }
    }
}

private fun appendInline(sb: StringBuilder, inline: Inline, indent: String) {
    when (inline) {
        is Inline.Text ->
            sb.append("${indent}Text \"${compact(inline.literal)}\"\n")

        Inline.SoftBreak ->
            sb.append("${indent}SoftBreak\n")

        Inline.HardBreak ->
            sb.append("${indent}HardBreak\n")

        is Inline.CodeSpan ->
            sb.append("${indent}CodeSpan \"${inline.literal}\"\n")

        is Inline.HtmlEntity ->
            sb.append("${indent}HtmlEntity \"${inline.literal}\"\n")

        is Inline.Emphasis -> {
            sb.append("${indent}Emphasis\n")
            for (child in inline.children) appendInline(sb, child, "$indent  ")
        }

        is Inline.StrongEmphasis -> {
            sb.append("${indent}StrongEmphasis\n")
            for (child in inline.children) appendInline(sb, child, "$indent  ")
        }

        is Inline.Link -> {
            sb.append("${indent}Link(dest=\"${inline.destination}\")\n")
            for (child in inline.children) appendInline(sb, child, "$indent  ")
        }

        is Inline.Image ->
            sb.append("${indent}Image(dest=\"${inline.destination}\", alt=\"${inline.alt}\")\n")

        is Inline.Autolink ->
            sb.append("${indent}Autolink \"${inline.url}\"\n")

        is Inline.RawHtml ->
            sb.append("${indent}RawHtml \"${compact(inline.literal)}\"\n")
    }
}

/** Replaces newlines with `↵` and truncates to [max] characters. */
private fun compact(s: String, max: Int = 72): String {
    val flat = s.replace("\r\n", "↵").replace('\r', '↵').replace('\n', '↵')
    return if (flat.length > max) flat.substring(0, max) + "…" else flat
}
