package parsek

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ParserOpsTest {

    private val input = ParserInput.of("abc123".toList(), Unit)
    private val letter = pSatisfy<Char, Unit> { it.isLetter() }
    private val digit = pSatisfy<Char, Unit> { it.isDigit() }

    // --- plus (pAnd) ---

    @Test
    fun plusSequencesTwoParsers() {
        val result = (letter + digit)(ParserInput.of("a1".toList(), Unit))
        assertIs<Success<Char, Pair<Char, Char>, Unit>>(result)
        assertEquals('a' to '1', result.value)
        assertEquals(2, result.nextIndex)
    }

    @Test
    fun plusFailsIfFirstFails() {
        val result = (digit + letter)(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }

    @Test
    fun plusFailsIfSecondFails() {
        val result = (letter + digit)(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(1, result.index)
    }

    // --- times (pRepeat) ---

    @Test
    fun timesRepeatsParserNTimes() {
        val result = (letter * 3)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('a', 'b', 'c'), result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun timesZeroAlwaysSucceeds() {
        val result = (letter * 0)(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(emptyList(), result.value)
        assertEquals(0, result.nextIndex)
    }

    // --- or (pOr) ---

    @Test
    fun orTriesFirstThenSecond() {
        val result = (digit or letter)(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('a', result.value)
    }

    @Test
    fun orPrecedenceLowerThanPlus() {
        // a + b or c should be (a + b) or c, not a + (b or c)
        val ab = letter + letter
        val expr = ab or (digit + digit)
        val result = expr(input)
        assertIs<Success<Char, Pair<Char, Char>, Unit>>(result)
        assertEquals('a' to 'b', result.value)
    }

    // --- not (pNot) ---

    @Test
    fun notSucceedsWhenParserFails() {
        val result = (!digit)(input)
        assertIs<Success<Char, Unit, Unit>>(result)
        assertEquals(0, result.nextIndex)
    }

    @Test
    fun notFailsWhenParserSucceeds() {
        val result = (!letter)(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals(0, result.index)
    }

    // --- label ---

    @Test
    fun labelReplacesFailureMessage() {
        val result = (digit label "a digit")(input)
        assertIs<Failure<Char, Unit>>(result)
        assertEquals("a digit", result.message)
    }

    @Test
    fun labelPassesThroughSuccess() {
        val result = (letter label "a letter")(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('a', result.value)
    }

    // --- .optional ---

    @Test
    fun optionalSucceedsWithValueWhenParserMatches() {
        val result = letter.optional(input)
        assertIs<Success<Char, Char?, Unit>>(result)
        assertEquals('a', result.value)
        assertEquals(1, result.nextIndex)
    }

    @Test
    fun optionalSucceedsWithNullWhenParserFails() {
        val result = digit.optional(input)
        assertIs<Success<Char, Char?, Unit>>(result)
        assertEquals(null, result.value)
        assertEquals(0, result.nextIndex)
    }

    // --- .many ---

    @Test
    fun manyCollectsZeroOrMore() {
        val result = letter.many(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('a', 'b', 'c'), result.value)
        assertEquals(3, result.nextIndex)
    }

    @Test
    fun manySucceedsWithEmptyListOnNoMatch() {
        val result = digit.many(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(emptyList(), result.value)
        assertEquals(0, result.nextIndex)
    }

    // --- .many1 ---

    @Test
    fun many1CollectsOneOrMore() {
        val result = letter.many1(input)
        assertIs<Success<Char, List<Char>, Unit>>(result)
        assertEquals(listOf('a', 'b', 'c'), result.value)
    }

    @Test
    fun many1FailsOnNoMatch() {
        val result = digit.many1(input)
        assertIs<Failure<Char, Unit>>(result)
    }

    // --- .lookAhead ---

    @Test
    fun lookAheadDoesNotConsumeInput() {
        val result = letter.lookAhead(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('a', result.value)
        assertEquals(0, result.nextIndex)
    }

    // --- .map ---

    @Test
    fun mapTransformsSuccessValue() {
        val result = letter.map { it.uppercaseChar() }(input)
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('A', result.value)
    }

    // --- .bind ---

    @Test
    fun bindChainsSecondParser() {
        val result = letter.bind { pSatisfy { c -> c.isDigit() } }(
            ParserInput.of("a1".toList(), Unit)
        )
        assertIs<Success<Char, Char, Unit>>(result)
        assertEquals('1', result.value)
        assertEquals(2, result.nextIndex)
    }

    // --- end-to-end grammar fragment ---

    @Test
    fun identifierGrammarFragment() {
        // identifier = letter (letter | digit)*
        //            → returns the identifier string
        val identStart = letter
        val identRest = (letter or digit).many
        val identifier = (identStart + identRest).map { (first, rest) ->
            (listOf(first) + rest).joinToString("")
        } label "identifier"

        val id = ParserInput.of("abc123!".toList(), Unit)
        val result = identifier(id)
        assertIs<Success<Char, String, Unit>>(result)
        assertEquals("abc123", result.value)
        assertEquals(6, result.nextIndex)

        val bad = ParserInput.of("123".toList(), Unit)
        val failed = identifier(bad)
        assertIs<Failure<Char, Unit>>(failed)
        assertEquals("identifier", failed.message)
    }

    @Test
    fun optionalSignGrammarFragment() {
        // signed-digits = ('+' | '-')? digit+  → returns sign char (or null) and digit list
        val sign = (pSatisfy<Char, Unit> { it == '+' } or pSatisfy { it == '-' }).optional
        val digits = digit.many1
        val signed = sign + digits

        val pos = ParserInput.of("+42".toList(), Unit)
        val r1 = signed(pos)
        assertIs<Success<Char, Pair<Char?, List<Char>>, Unit>>(r1)
        assertEquals('+' to listOf('4', '2'), r1.value)

        val nosign = ParserInput.of("99".toList(), Unit)
        val r2 = signed(nosign)
        assertIs<Success<Char, Pair<Char?, List<Char>>, Unit>>(r2)
        assertEquals(null to listOf('9', '9'), r2.value)
    }
}
