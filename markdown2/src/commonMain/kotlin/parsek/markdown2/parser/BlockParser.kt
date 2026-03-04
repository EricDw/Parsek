package parsek.markdown2.parser

import parsek.markdown.ast.Block
import parsek.markdown.ast.Inline
import parsek.markdown2.lexer.lexemesToText
import parsek.markdown2.token.Token

/**
 * Block parser: converts a list of block-level [Token]s into [Block] AST nodes.
 *
 * Phase 2 handles leaf blocks only. Paragraphs and headings produce
 * stub inline content (`Inline.Text`) that will be replaced in the inline pass.
 */
fun parseBlocks(tokens: List<Token>): List<Block> {
    val blocks = mutableListOf<Block>()
    var i = 0

    while (i < tokens.size) {
        when (val token = tokens[i]) {
            is Token.BlankLine -> {
                blocks.add(Block.BlankLine)
                i++
            }

            is Token.ThematicBreakLine -> {
                // Check if this is actually a setext heading (--- after paragraph)
                // This is only handled in parseParagraphOrSetext. Standalone = thematic break.
                blocks.add(Block.ThematicBreak)
                i++
            }

            is Token.AtxHeadingMarker -> {
                val content = if (i + 1 < tokens.size && tokens[i + 1] is Token.AtxHeadingContent) {
                    val ct = tokens[i + 1] as Token.AtxHeadingContent
                    i += 2
                    val text = lexemesToText(ct.lexemes)
                    listOf(Inline.Text(text))
                } else {
                    i++
                    emptyList()
                }
                blocks.add(Block.Heading(token.level, content))
            }

            is Token.CodeFenceOpen -> {
                var info: String? = null
                val contentBuilder = StringBuilder()
                i++

                if (i < tokens.size && tokens[i] is Token.CodeFenceInfo) {
                    info = (tokens[i] as Token.CodeFenceInfo).info
                    i++
                }

                while (i < tokens.size) {
                    when (tokens[i]) {
                        is Token.CodeContent -> {
                            contentBuilder.append((tokens[i] as Token.CodeContent).literal)
                            i++
                        }
                        is Token.CodeFenceClose -> {
                            i++
                            break
                        }
                        else -> break
                    }
                }

                blocks.add(Block.FencedCodeBlock(info, contentBuilder.toString()))
            }

            is Token.IndentedCodeLine -> {
                // Collect all indented code lines (with intervening blanks)
                val allLines = mutableListOf<Pair<Boolean, String>>() // (isBlank, content)
                allLines.add(token.literal.isBlank() to token.literal)
                i++

                while (i < tokens.size) {
                    when (tokens[i]) {
                        is Token.IndentedCodeLine -> {
                            val lit = (tokens[i] as Token.IndentedCodeLine).literal
                            allLines.add(lit.isBlank() to lit)
                            i++
                        }
                        is Token.BlankLine -> {
                            allLines.add(true to "\n")
                            i++
                        }
                        else -> break
                    }
                }

                // Strip leading blank lines
                val firstNonBlank = allLines.indexOfFirst { !it.first }
                // Strip trailing blank lines
                val lastNonBlank = allLines.indexOfLast { !it.first }

                if (firstNonBlank == -1) {
                    // All blank — rewind everything, emit nothing useful
                    i -= allLines.size
                    i++ // skip at least the first token
                    blocks.add(Block.BlankLine)
                } else {
                    // Rewind trailing blanks
                    val trailingCount = allLines.size - lastNonBlank - 1
                    if (trailingCount > 0) {
                        i -= trailingCount
                    }

                    val contentBuilder = StringBuilder()
                    for (idx in firstNonBlank..lastNonBlank) {
                        contentBuilder.append(allLines[idx].second)
                    }
                    blocks.add(Block.IndentedCodeBlock(contentBuilder.toString()))
                }
            }

            is Token.HtmlBlockLine -> {
                val contentBuilder = StringBuilder()
                contentBuilder.append(token.literal)
                i++

                while (i < tokens.size && tokens[i] is Token.HtmlBlockLine) {
                    contentBuilder.append((tokens[i] as Token.HtmlBlockLine).literal)
                    i++
                }

                blocks.add(Block.HtmlBlock(contentBuilder.toString()))
            }

            is Token.ParagraphLine -> {
                i = parseParagraphOrSetext(tokens, i, blocks)
            }

            is Token.SetextUnderline -> {
                // Orphaned setext underline (no preceding paragraph) — treat as paragraph
                // It may be followed by more paragraph lines or setext underlines
                val orphanTexts = mutableListOf(token.text)
                i++
                while (i < tokens.size) {
                    when (val t = tokens[i]) {
                        is Token.ParagraphLine -> {
                            orphanTexts.add(lexemesToText(t.lexemes))
                            i++
                        }
                        is Token.SetextUnderline -> {
                            // Another orphaned setext (or could be a heading)
                            // If we have paragraph content before this, it becomes a heading
                            if (orphanTexts.isNotEmpty()) {
                                val text = orphanTexts.joinToString("\n")
                                blocks.add(Block.Heading(t.level, listOf(Inline.Text(text))))
                                orphanTexts.clear()
                                i++
                                break
                            } else {
                                orphanTexts.add(t.text)
                                i++
                            }
                        }
                        is Token.ThematicBreakLine -> {
                            if (t.marker == '-' && orphanTexts.isNotEmpty()) {
                                val text = orphanTexts.joinToString("\n")
                                blocks.add(Block.Heading(2, listOf(Inline.Text(text))))
                                orphanTexts.clear()
                                i++
                                break
                            } else {
                                break
                            }
                        }
                        else -> break
                    }
                }
                if (orphanTexts.isNotEmpty()) {
                    val text = orphanTexts.joinToString("\n")
                    blocks.add(Block.Paragraph(listOf(Inline.Text(text))))
                }
            }

            // Container tokens (Phase 3) — skip for now
            is Token.BlockQuoteMarker, is Token.BulletMarker, is Token.OrderedMarker -> {
                i++
            }

            // Tokens that shouldn't appear at top level in block parsing
            is Token.AtxHeadingContent, is Token.CodeFenceClose,
            is Token.CodeFenceInfo, is Token.CodeContent -> {
                i++
            }
        }
    }

    // Filter out blank lines from the final block list
    return blocks.filter { it !is Block.BlankLine }
}

/**
 * Parses a paragraph (potentially becoming a setext heading).
 *
 * Handles paragraph continuation rules:
 * - Indented code lines cannot interrupt a paragraph (absorbed as lazy continuation)
 * - SetextUnderline after paragraph lines → setext heading
 * - ThematicBreakLine with `-` chars after paragraph lines → setext heading (level 2)
 */
private fun parseParagraphOrSetext(tokens: List<Token>, startIdx: Int, blocks: MutableList<Block>): Int {
    val paragraphTexts = mutableListOf<String>()
    var i = startIdx

    // First line
    paragraphTexts.add(lexemesToText((tokens[i] as Token.ParagraphLine).lexemes))
    i++

    while (i < tokens.size) {
        when (val t = tokens[i]) {
            is Token.ParagraphLine -> {
                paragraphTexts.add(lexemesToText(t.lexemes))
                i++
            }
            is Token.SetextUnderline -> {
                // Setext heading
                i++
                val text = paragraphTexts.joinToString("\n")
                blocks.add(Block.Heading(t.level, listOf(Inline.Text(text))))
                return i
            }
            is Token.ThematicBreakLine -> {
                if (t.marker == '-') {
                    // A `---` after paragraph content = setext heading level 2
                    // (setext heading takes precedence over thematic break)
                    i++
                    val text = paragraphTexts.joinToString("\n")
                    blocks.add(Block.Heading(2, listOf(Inline.Text(text))))
                    return i
                } else {
                    // *** or ___ after paragraph = thematic break, not setext
                    break
                }
            }
            is Token.IndentedCodeLine -> {
                // Indented code cannot interrupt a paragraph — lazy continuation
                paragraphTexts.add(t.literal.trimEnd('\n'))
                i++
            }
            else -> break
        }
    }

    val text = paragraphTexts.joinToString("\n")
    blocks.add(Block.Paragraph(listOf(Inline.Text(text))))
    return i
}
