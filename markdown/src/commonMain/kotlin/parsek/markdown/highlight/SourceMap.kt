package parsek.markdown.highlight

/**
 * Maps offsets in raw extracted content (paragraph/heading text after
 * whitespace stripping) back to absolute document positions.
 *
 * Multi-line content (e.g. paragraphs) has per-line entries because each
 * line may have different amounts of leading whitespace stripped.
 */
class SourceMap(private val entries: List<LineMapping>) {

    data class LineMapping(val rawStart: Int, val docStart: Int)

    /** Converts a raw-content offset to an absolute document offset. */
    fun toAbsolute(rawOffset: Int): Int {
        val entry = entries.lastOrNull { it.rawStart <= rawOffset } ?: return rawOffset
        return entry.docStart + (rawOffset - entry.rawStart)
    }

    companion object {
        /** Single-line content starting at [docOffset]. */
        fun simple(docOffset: Int) = SourceMap(listOf(LineMapping(0, docOffset)))
    }
}
