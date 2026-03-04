package parsek.markdown.highlight

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceMapTest {

    @Test
    fun simpleMapping() {
        val map = SourceMap.simple(10)
        assertEquals(10, map.toAbsolute(0))
        assertEquals(13, map.toAbsolute(3))
    }

    @Test
    fun multiLineMapping() {
        // Simulates a paragraph like:
        //   "  Hello\n  World\n"
        // Line 1: content "Hello" starts at doc offset 2, rawStart 0
        // Line 2: content "World" starts at doc offset 10, rawStart 6 (len("Hello") + 1)
        val map = SourceMap(
            listOf(
                SourceMap.LineMapping(rawStart = 0, docStart = 2),
                SourceMap.LineMapping(rawStart = 6, docStart = 10),
            ),
        )

        // Offsets within first line
        assertEquals(2, map.toAbsolute(0))  // 'H'
        assertEquals(6, map.toAbsolute(4))  // 'o'

        // The \n separator at rawOffset 5
        assertEquals(7, map.toAbsolute(5))

        // Offsets within second line
        assertEquals(10, map.toAbsolute(6)) // 'W'
        assertEquals(14, map.toAbsolute(10)) // 'd'
    }

    @Test
    fun toAbsoluteAtLineBoundary() {
        val map = SourceMap(
            listOf(
                SourceMap.LineMapping(rawStart = 0, docStart = 0),
                SourceMap.LineMapping(rawStart = 4, docStart = 5),
            ),
        )
        // Exactly at second line boundary
        assertEquals(5, map.toAbsolute(4))
    }

    @Test
    fun toAbsoluteWithOffsetZero() {
        val map = SourceMap(
            listOf(SourceMap.LineMapping(rawStart = 0, docStart = 5)),
        )
        assertEquals(5, map.toAbsolute(0))
    }

    @Test
    fun emptyEntriesFallback() {
        // No entries at all — falls back to identity
        val map = SourceMap(emptyList())
        assertEquals(3, map.toAbsolute(3))
    }

    @Test
    fun linesWithDifferentStrippedAmounts() {
        // Line 1: 1 leading space stripped, content at doc 1, raw 0
        // Line 2: 3 leading spaces stripped, content at doc 9, raw 5 (len("abcd") + 1)
        val map = SourceMap(
            listOf(
                SourceMap.LineMapping(rawStart = 0, docStart = 1),
                SourceMap.LineMapping(rawStart = 5, docStart = 11),
            ),
        )
        assertEquals(1, map.toAbsolute(0))  // Line 1 start
        assertEquals(4, map.toAbsolute(3))  // Line 1 offset 3
        assertEquals(11, map.toAbsolute(5)) // Line 2 start
        assertEquals(14, map.toAbsolute(8)) // Line 2 offset 3
    }
}
