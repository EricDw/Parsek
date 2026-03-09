package parsek.prose.parser

/**
 * Known abbreviations that end with a period but do not terminate a sentence.
 *
 * Each entry is stored **without** the trailing dot (e.g. `"Mr"` not `"Mr."`).
 */
internal val ABBREVIATIONS: Set<String> = setOf(
    // Titles
    "Mr", "Mrs", "Ms", "Dr", "Prof", "Rev", "Sr", "Jr", "St",
    // Academic / professional
    "Ph", "Ed", "Gen", "Gov", "Sgt", "Cpl", "Pvt", "Capt", "Lt", "Col", "Maj",
    // Latin
    "etc", "vs", "approx", "dept", "est", "vol",
    // Common two-part abbreviations (the individual letters)
    "e", "g", "i",  // e.g., i.e.
    "a", "p",        // a.m., p.m.
    // Units / misc
    "ft", "in", "lb", "oz", "no", "fig", "ch", "sec",
)

/**
 * Returns `true` if [word] is a known abbreviation that uses a trailing period
 * without ending a sentence.
 */
internal fun isAbbreviation(word: String): Boolean {
    return word in ABBREVIATIONS
}

/**
 * Returns `true` if [word] looks like an initialism (single uppercase letter,
 * or a sequence of single uppercase letters each followed by a period, e.g.
 * `"U"`, `"U.S"`, `"U.S.A"`).
 */
internal fun isInitialism(word: String): Boolean {
    if (word.length == 1 && word[0].isUpperCase()) return true
    if (word.length < 3) return false
    // Pattern: X.X or X.X.X etc.
    var i = 0
    while (i < word.length) {
        if (!word[i].isUpperCase()) return false
        i++
        if (i < word.length) {
            if (word[i] != '.') return false
            i++
        }
    }
    return true
}
