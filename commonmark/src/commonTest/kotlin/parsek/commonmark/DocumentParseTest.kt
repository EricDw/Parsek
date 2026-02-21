package parsek.commonmark

import parsek.Parser
import parsek.ParserInput
import parsek.Success
import parsek.commonmark.ast.Block
import parsek.commonmark.parser.block.pAtxHeading
import parsek.commonmark.parser.block.pBlockQuote
import parsek.commonmark.parser.block.pFencedCodeBlock
import parsek.commonmark.parser.block.pHtmlBlock
import parsek.commonmark.parser.block.pIndentedCodeBlock
import parsek.commonmark.parser.block.pLinkReferenceDefinition
import parsek.commonmark.parser.block.pList
import parsek.commonmark.parser.block.pParagraph
import parsek.commonmark.parser.block.pSetextHeading
import parsek.commonmark.parser.block.pThematicBreak
import parsek.pMany
import parsek.pMap
import parsek.pOr
import parsek.text.pBlankLine
import kotlin.test.Test
import kotlin.test.assertIs

// ---------------------------------------------------------------------------
// Temporary block parser wiring (Phase 6.1 will formalise this)
// ---------------------------------------------------------------------------

/**
 * A temporary `pBlock` that chains all currently implemented block parsers.
 * Used only for the document litmus test; Phase 6.1 will wire this properly.
 */
private fun pBlock(): Parser<Char, Block, Unit> = pOr(
    pMap(pBlankLine<Unit>()) { Block.BlankLine },
    pOr(
        pThematicBreak(),
        pOr(
            pAtxHeading(),
            pOr(
                pFencedCodeBlock(),
                pOr(
                    pIndentedCodeBlock(),
                    pOr(
                        pHtmlBlock(),
                        pOr(
                            pLinkReferenceDefinition(),
                            pOr(
                                pBlockQuote { pBlock() },
                                pOr(
                                    pList { pBlock() },
                                    pOr(
                                        pSetextHeading(),
                                        pParagraph(),
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    )
)

// ---------------------------------------------------------------------------
// Litmus test document
// ---------------------------------------------------------------------------

/**
 * A sample CommonMark document that exercises every block type implemented
 * so far. Parsed in [DocumentParseTest.parseAndPrintDocument].
 *
 * Inline content is stored as raw [parsek.commonmark.ast.Inline.Text] for now;
 * the inline pass (Phase 5) will enrich these nodes in a later PR.
 */
private val SAMPLE_DOCUMENT = """
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
""".trimIndent() + "\n"

// ---------------------------------------------------------------------------
// Test
// ---------------------------------------------------------------------------

class DocumentParseTest {

    @Test
    fun parseAndPrintDocument() {
        val input = ParserInput.of(SAMPLE_DOCUMENT.toList(), Unit)
        val result = pMany(pBlock())(input)
        assertIs<Success<Char, List<Block>, Unit>>(result)

        val ast = printAst(result.value)
        println()
        println("=".repeat(72))
        println("  Parsed AST")
        println("=".repeat(72))
        println(ast)
        println("Consumed ${result.nextIndex} / ${SAMPLE_DOCUMENT.length} characters")
        println("=".repeat(72))
    }
}
