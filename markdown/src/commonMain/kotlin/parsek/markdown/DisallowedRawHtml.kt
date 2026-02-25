package parsek.markdown

/**
 * GFM disallowed raw HTML tags (§6.11).
 *
 * These tags are filtered when rendering by replacing the leading `<` with
 * `&lt;`. Both opening and closing forms are matched (e.g. `<script>` and
 * `</script>`). Matching is case-insensitive.
 */
private val DISALLOWED_TAG_NAMES = setOf(
    "title", "textarea", "style", "xmp", "iframe",
    "noembed", "noframes", "script", "plaintext",
)

private val DISALLOWED_PATTERN = Regex(
    "<(/?)(" + DISALLOWED_TAG_NAMES.joinToString("|") + ")(\\s|>|/>)",
    RegexOption.IGNORE_CASE,
)

/**
 * Filters disallowed raw HTML tags per GFM §6.11.
 *
 * Replaces the leading `<` of any disallowed tag with `&lt;`, leaving the rest
 * of the tag intact. This prevents the browser from interpreting the tag while
 * preserving the tag text for display.
 *
 * @param html the raw HTML string to filter.
 * @return the filtered string.
 */
fun filterDisallowedRawHtml(html: String): String =
    DISALLOWED_PATTERN.replace(html) { match ->
        "&lt;" + match.value.substring(1)
    }
