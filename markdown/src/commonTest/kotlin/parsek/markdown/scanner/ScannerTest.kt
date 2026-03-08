package parsek.markdown.scanner

import parsek.markdown.lexeme.Lexeme
import parsek.markdown.lexeme.SourceRange
import parsek.markdown.lexeme.toSourceText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScannerTest {

    // ── Round-trip tests ────────────────────────────────────────────────

    @Test
    fun roundTripEmptyString() {
        val text = ""
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripPlainText() {
        val text = "Hello, world!"
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripHeading() {
        val text = "## Hello World\n"
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripEmphasis() {
        val text = "This is **bold** and *italic* text."
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripCodeFence() {
        val text = "```kotlin\nfun main() {}\n```\n"
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripLinks() {
        val text = "[link](https://example.com \"title\")\n"
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripTable() {
        val text = "| a | b |\n|---|---|\n| 1 | 2 |\n"
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripBlockQuote() {
        val text = "> This is a quote\n> with two lines\n"
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripOrderedList() {
        val text = "1. First\n2. Second\n3. Third\n"
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripBulletList() {
        val text = "- one\n- two\n+ three\n"
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripBackslashEscapes() {
        val text = "\\*not bold\\* and \\[not a link\\]\n"
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripHtmlEntity() {
        val text = "&amp; &#123; &#x1F600;\n"
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripStrikethrough() {
        val text = "~~deleted~~\n"
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripTaskList() {
        val text = "- [x] done\n- [ ] todo\n"
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripMixedWhitespace() {
        val text = "  \t  \t\n    code\n"
        assertEquals(text, scanDocument(text).toSourceText())
    }

    @Test
    fun roundTripCrLf() {
        // \r\n is normalized to \n in toSourceText
        val text = "line1\r\nline2\r\n"
        val lexemes = scanDocument(text)
        // Round trip produces \n instead of \r\n, which is correct normalization
        assertEquals("line1\nline2\n", lexemes.toSourceText())
    }

    @Test
    fun roundTripAllStructuralChars() {
        val text = "#*_`~[]()<>&;\\|=-+!:.\"\'"
        assertEquals(text, scanDocument(text).toSourceText())
    }

    // ── Structural character lexeme tests ────────────────────────────────

    @Test
    fun singleHash() {
        val lexemes = scanDocument("#")
        assertEquals(1, lexemes.size)
        assertTrue(lexemes[0] is Lexeme.Hash)
        assertEquals(SourceRange(0, 1), lexemes[0].range)
    }

    @Test
    fun hashRun() {
        val lexemes = scanDocument("###")
        assertEquals(1, lexemes.size)
        val run = lexemes[0] as Lexeme.HashRun
        assertEquals(3, run.count)
        assertEquals(SourceRange(0, 3), run.range)
    }

    @Test
    fun singleAsterisk() {
        val lexemes = scanDocument("*")
        assertEquals(1, lexemes.size)
        assertTrue(lexemes[0] is Lexeme.Asterisk)
    }

    @Test
    fun asteriskRun() {
        val lexemes = scanDocument("**")
        assertEquals(1, lexemes.size)
        val run = lexemes[0] as Lexeme.AsteriskRun
        assertEquals(2, run.count)
        assertEquals(SourceRange(0, 2), run.range)
    }

    @Test
    fun underscoreRun() {
        val lexemes = scanDocument("___")
        assertEquals(1, lexemes.size)
        val run = lexemes[0] as Lexeme.UnderscoreRun
        assertEquals(3, run.count)
    }

    @Test
    fun backtickRun() {
        val lexemes = scanDocument("```")
        assertEquals(1, lexemes.size)
        val run = lexemes[0] as Lexeme.BacktickRun
        assertEquals(3, run.count)
    }

    @Test
    fun tildeRun() {
        val lexemes = scanDocument("~~")
        assertEquals(1, lexemes.size)
        val run = lexemes[0] as Lexeme.TildeRun
        assertEquals(2, run.count)
    }

    @Test
    fun hyphenRun() {
        val lexemes = scanDocument("---")
        assertEquals(1, lexemes.size)
        val run = lexemes[0] as Lexeme.HyphenRun
        assertEquals(3, run.count)
    }

    @Test
    fun equalsRun() {
        val lexemes = scanDocument("===")
        assertEquals(1, lexemes.size)
        val run = lexemes[0] as Lexeme.EqualsRun
        assertEquals(3, run.count)
    }

    // ── Whitespace tests ────────────────────────────────────────────────

    @Test
    fun singleSpace() {
        val lexemes = scanDocument(" ")
        assertEquals(1, lexemes.size)
        assertTrue(lexemes[0] is Lexeme.Space)
    }

    @Test
    fun spaceRun() {
        val lexemes = scanDocument("    ")
        assertEquals(1, lexemes.size)
        val run = lexemes[0] as Lexeme.SpaceRun
        assertEquals(4, run.count)
    }

    @Test
    fun tab() {
        val lexemes = scanDocument("\t")
        assertEquals(1, lexemes.size)
        assertTrue(lexemes[0] is Lexeme.Tab)
    }

    @Test
    fun newline() {
        val lexemes = scanDocument("\n")
        assertEquals(1, lexemes.size)
        assertTrue(lexemes[0] is Lexeme.Newline)
        assertEquals(SourceRange(0, 1), lexemes[0].range)
    }

    @Test
    fun crlfNewline() {
        val lexemes = scanDocument("\r\n")
        assertEquals(1, lexemes.size)
        assertTrue(lexemes[0] is Lexeme.Newline)
        assertEquals(SourceRange(0, 2), lexemes[0].range)
    }

    @Test
    fun crAlone() {
        val lexemes = scanDocument("\r")
        assertEquals(1, lexemes.size)
        assertTrue(lexemes[0] is Lexeme.Newline)
        assertEquals(SourceRange(0, 1), lexemes[0].range)
    }

    // ── Non-runnable structural characters ───────────────────────────────

    @Test
    fun brackets() {
        val lexemes = scanDocument("[]")
        assertEquals(2, lexemes.size)
        assertTrue(lexemes[0] is Lexeme.BracketOpen)
        assertTrue(lexemes[1] is Lexeme.BracketClose)
    }

    @Test
    fun parens() {
        val lexemes = scanDocument("()")
        assertEquals(2, lexemes.size)
        assertTrue(lexemes[0] is Lexeme.ParenOpen)
        assertTrue(lexemes[1] is Lexeme.ParenClose)
    }

    @Test
    fun angles() {
        val lexemes = scanDocument("<>")
        assertEquals(2, lexemes.size)
        assertTrue(lexemes[0] is Lexeme.AngleOpen)
        assertTrue(lexemes[1] is Lexeme.AngleClose)
    }

    @Test
    fun pipe() {
        val lexemes = scanDocument("|")
        assertEquals(1, lexemes.size)
        assertTrue(lexemes[0] is Lexeme.Pipe)
    }

    @Test
    fun backslash() {
        val lexemes = scanDocument("\\")
        assertEquals(1, lexemes.size)
        assertTrue(lexemes[0] is Lexeme.Backslash)
    }

    @Test
    fun ampersandAndSemicolon() {
        val lexemes = scanDocument("&;")
        assertEquals(2, lexemes.size)
        assertTrue(lexemes[0] is Lexeme.Ampersand)
        assertTrue(lexemes[1] is Lexeme.Semicolon)
    }

    @Test
    fun quotes() {
        val lexemes = scanDocument("\"'")
        assertEquals(2, lexemes.size)
        val q1 = lexemes[0] as Lexeme.Quote
        val q2 = lexemes[1] as Lexeme.Quote
        assertEquals('"', q1.char)
        assertEquals('\'', q2.char)
    }

    // ── Text and digit runs ─────────────────────────────────────────────

    @Test
    fun textRun() {
        val lexemes = scanDocument("hello")
        assertEquals(1, lexemes.size)
        val run = lexemes[0] as Lexeme.TextRun
        assertEquals("hello", run.text)
        assertEquals(SourceRange(0, 5), run.range)
    }

    @Test
    fun digitRun() {
        val lexemes = scanDocument("42")
        assertEquals(1, lexemes.size)
        val run = lexemes[0] as Lexeme.DigitRun
        assertEquals("42", run.text)
        assertEquals(SourceRange(0, 2), run.range)
    }

    @Test
    fun textAndDigitsSeparated() {
        val lexemes = scanDocument("abc123def")
        assertEquals(3, lexemes.size)
        assertTrue(lexemes[0] is Lexeme.TextRun)
        assertTrue(lexemes[1] is Lexeme.DigitRun)
        assertTrue(lexemes[2] is Lexeme.TextRun)
        assertEquals("abc", (lexemes[0] as Lexeme.TextRun).text)
        assertEquals("123", (lexemes[1] as Lexeme.DigitRun).text)
        assertEquals("def", (lexemes[2] as Lexeme.TextRun).text)
    }

    // ── Source range continuity ─────────────────────────────────────────

    @Test
    fun sourceRangesAreContinuous() {
        val text = "## Hello\n"
        val lexemes = scanDocument(text)
        // Verify ranges cover the entire input without gaps or overlaps
        var expectedStart = 0
        for (lexeme in lexemes) {
            assertEquals(expectedStart, lexeme.range.start, "Gap before ${lexeme::class.simpleName}")
            assertTrue(lexeme.range.end > lexeme.range.start, "Empty range for ${lexeme::class.simpleName}")
            expectedStart = lexeme.range.end
        }
        assertEquals(text.length, expectedStart, "Ranges don't cover entire input")
    }

    @Test
    fun sourceRangesAreContinuousComplex() {
        val text = "```kotlin\nval x = 42\n```\n"
        val lexemes = scanDocument(text)
        var expectedStart = 0
        for (lexeme in lexemes) {
            assertEquals(expectedStart, lexeme.range.start)
            expectedStart = lexeme.range.end
        }
        assertEquals(text.length, expectedStart)
    }

    // ── ATX heading sequence ────────────────────────────────────────────

    @Test
    fun atxHeadingLexemes() {
        val lexemes = scanDocument("## Hello\n")
        // ## → HashRun(2), ' ' → Space, Hello → TextRun, \n → Newline
        assertEquals(4, lexemes.size)
        val hashRun = lexemes[0] as Lexeme.HashRun
        assertEquals(2, hashRun.count)
        assertTrue(lexemes[1] is Lexeme.Space)
        val text = lexemes[2] as Lexeme.TextRun
        assertEquals("Hello", text.text)
        assertTrue(lexemes[3] is Lexeme.Newline)
    }

    // ── Full document scan ──────────────────────────────────────────────

    @Test
    fun fullDocumentScan() {
        val text = """
            |# Title
            |
            |Some **bold** text.
            |
            |```
            |code
            |```
        """.trimMargin() + "\n"

        val lexemes = scanDocument(text)
        // Verify round-trip
        assertEquals(text, lexemes.toSourceText())
        // Verify ranges cover everything
        var pos = 0
        for (lexeme in lexemes) {
            assertEquals(pos, lexeme.range.start)
            pos = lexeme.range.end
        }
        assertEquals(text.length, pos)
    }
}
