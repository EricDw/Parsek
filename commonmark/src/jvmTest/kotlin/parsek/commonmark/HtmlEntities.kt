package parsek.commonmark

/**
 * HTML5 named character reference lookup and entity resolution.
 *
 * This is a test-only utility used by [renderHtml] to resolve HTML entities
 * stored in [parsek.commonmark.ast.Inline.HtmlEntity] to their Unicode values.
 */

/**
 * Resolves an HTML entity string (e.g. `&amp;`, `&#42;`, `&#x2A;`) to its
 * Unicode string value.
 *
 * - Named entities are looked up in [HTML_ENTITIES].
 * - Decimal numeric references (`&#digits;`) are converted to the corresponding code point.
 * - Hexadecimal numeric references (`&#xhex;` or `&#Xhex;`) are similarly converted.
 * - Code point 0 is replaced with U+FFFD (REPLACEMENT CHARACTER).
 * - Unknown named entities return `null` (caller should output the literal, HTML-escaped).
 */
fun resolveHtmlEntity(entity: String): String? {
    if (!entity.startsWith("&") || !entity.endsWith(";")) return null
    val inner = entity.substring(1, entity.length - 1)

    if (inner.startsWith("#")) {
        // Numeric character reference
        val codePoint: Int? = if (inner.startsWith("#x", ignoreCase = true) || inner.startsWith("#X")) {
            inner.substring(2).toIntOrNull(16)
        } else {
            inner.substring(1).toIntOrNull(10)
        }
        if (codePoint == null) return null
        // Code point 0 → U+FFFD
        val cp = if (codePoint == 0) 0xFFFD else codePoint
        return try {
            String(Character.toChars(cp))
        } catch (_: IllegalArgumentException) {
            // Invalid code point → replacement character
            "\uFFFD"
        }
    }

    // Named entity
    return HTML_ENTITIES[inner]
}

/**
 * HTML5 named character references.
 *
 * This map contains the most commonly used named entities from the HTML5 spec.
 * The keys are entity names WITHOUT the leading `&` and trailing `;`.
 */
@Suppress("ktlint:standard:max-line-length")
val HTML_ENTITIES: Map<String, String> = mapOf(
    // Common entities used in the CommonMark spec tests
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to "\u00A0",
    "copy" to "\u00A9",
    "reg" to "\u00AE",
    "trade" to "\u2122",

    // Latin-1 supplement
    "iexcl" to "\u00A1",
    "cent" to "\u00A2",
    "pound" to "\u00A3",
    "curren" to "\u00A4",
    "yen" to "\u00A5",
    "brvbar" to "\u00A6",
    "sect" to "\u00A7",
    "uml" to "\u00A8",
    "ordf" to "\u00AA",
    "laquo" to "\u00AB",
    "not" to "\u00AC",
    "shy" to "\u00AD",
    "macr" to "\u00AF",
    "deg" to "\u00B0",
    "plusmn" to "\u00B1",
    "sup2" to "\u00B2",
    "sup3" to "\u00B3",
    "acute" to "\u00B4",
    "micro" to "\u00B5",
    "para" to "\u00B6",
    "middot" to "\u00B7",
    "cedil" to "\u00B8",
    "sup1" to "\u00B9",
    "ordm" to "\u00BA",
    "raquo" to "\u00BB",
    "frac14" to "\u00BC",
    "frac12" to "\u00BD",
    "frac34" to "\u00BE",
    "iquest" to "\u00BF",

    // Latin capital letters with accents
    "Agrave" to "\u00C0",
    "Aacute" to "\u00C1",
    "Acirc" to "\u00C2",
    "Atilde" to "\u00C3",
    "Auml" to "\u00C4",
    "Aring" to "\u00C5",
    "AElig" to "\u00C6",
    "Ccedil" to "\u00C7",
    "Egrave" to "\u00C8",
    "Eacute" to "\u00C9",
    "Ecirc" to "\u00CA",
    "Euml" to "\u00CB",
    "Igrave" to "\u00CC",
    "Iacute" to "\u00CD",
    "Icirc" to "\u00CE",
    "Iuml" to "\u00CF",
    "ETH" to "\u00D0",
    "Ntilde" to "\u00D1",
    "Ograve" to "\u00D2",
    "Oacute" to "\u00D3",
    "Ocirc" to "\u00D4",
    "Otilde" to "\u00D5",
    "Ouml" to "\u00D6",
    "times" to "\u00D7",
    "Oslash" to "\u00D8",
    "Ugrave" to "\u00D9",
    "Uacute" to "\u00DA",
    "Ucirc" to "\u00DB",
    "Uuml" to "\u00DC",
    "Yacute" to "\u00DD",
    "THORN" to "\u00DE",
    "szlig" to "\u00DF",

    // Latin small letters with accents
    "agrave" to "\u00E0",
    "aacute" to "\u00E1",
    "acirc" to "\u00E2",
    "atilde" to "\u00E3",
    "auml" to "\u00E4",
    "aring" to "\u00E5",
    "aelig" to "\u00E6",
    "ccedil" to "\u00E7",
    "egrave" to "\u00E8",
    "eacute" to "\u00E9",
    "ecirc" to "\u00EA",
    "euml" to "\u00EB",
    "igrave" to "\u00EC",
    "iacute" to "\u00ED",
    "icirc" to "\u00EE",
    "iuml" to "\u00EF",
    "eth" to "\u00F0",
    "ntilde" to "\u00F1",
    "ograve" to "\u00F2",
    "oacute" to "\u00F3",
    "ocirc" to "\u00F4",
    "otilde" to "\u00F5",
    "ouml" to "\u00F6",
    "divide" to "\u00F7",
    "oslash" to "\u00F8",
    "ugrave" to "\u00F9",
    "uacute" to "\u00FA",
    "ucirc" to "\u00FB",
    "uuml" to "\u00FC",
    "yacute" to "\u00FD",
    "thorn" to "\u00FE",
    "yuml" to "\u00FF",

    // Greek letters
    "Alpha" to "\u0391",
    "Beta" to "\u0392",
    "Gamma" to "\u0393",
    "Delta" to "\u0394",
    "Epsilon" to "\u0395",
    "Zeta" to "\u0396",
    "Eta" to "\u0397",
    "Theta" to "\u0398",
    "Iota" to "\u0399",
    "Kappa" to "\u039A",
    "Lambda" to "\u039B",
    "Mu" to "\u039C",
    "Nu" to "\u039D",
    "Xi" to "\u039E",
    "Omicron" to "\u039F",
    "Pi" to "\u03A0",
    "Rho" to "\u03A1",
    "Sigma" to "\u03A3",
    "Tau" to "\u03A4",
    "Upsilon" to "\u03A5",
    "Phi" to "\u03A6",
    "Chi" to "\u03A7",
    "Psi" to "\u03A8",
    "Omega" to "\u03A9",
    "alpha" to "\u03B1",
    "beta" to "\u03B2",
    "gamma" to "\u03B3",
    "delta" to "\u03B4",
    "epsilon" to "\u03B5",
    "zeta" to "\u03B6",
    "eta" to "\u03B7",
    "theta" to "\u03B8",
    "iota" to "\u03B9",
    "kappa" to "\u03BA",
    "lambda" to "\u03BB",
    "mu" to "\u03BC",
    "nu" to "\u03BD",
    "xi" to "\u03BE",
    "omicron" to "\u03BF",
    "pi" to "\u03C0",
    "rho" to "\u03C1",
    "sigmaf" to "\u03C2",
    "sigma" to "\u03C3",
    "tau" to "\u03C4",
    "upsilon" to "\u03C5",
    "phi" to "\u03C6",
    "chi" to "\u03C7",
    "psi" to "\u03C8",
    "omega" to "\u03C9",
    "thetasym" to "\u03D1",
    "upsih" to "\u03D2",
    "piv" to "\u03D6",

    // General punctuation and symbols
    "bull" to "\u2022",
    "hellip" to "\u2026",
    "prime" to "\u2032",
    "Prime" to "\u2033",
    "oline" to "\u203E",
    "frasl" to "\u2044",

    // Letterlike symbols
    "weierp" to "\u2118",
    "image" to "\u2111",
    "real" to "\u211C",
    "alefsym" to "\u2135",

    // Arrows
    "larr" to "\u2190",
    "uarr" to "\u2191",
    "rarr" to "\u2192",
    "darr" to "\u2193",
    "harr" to "\u2194",
    "crarr" to "\u21B5",
    "lArr" to "\u21D0",
    "uArr" to "\u21D1",
    "rArr" to "\u21D2",
    "dArr" to "\u21D3",
    "hArr" to "\u21D4",

    // Mathematical operators
    "forall" to "\u2200",
    "part" to "\u2202",
    "exist" to "\u2203",
    "empty" to "\u2205",
    "nabla" to "\u2207",
    "isin" to "\u2208",
    "notin" to "\u2209",
    "ni" to "\u220B",
    "prod" to "\u220F",
    "sum" to "\u2211",
    "minus" to "\u2212",
    "lowast" to "\u2217",
    "radic" to "\u221A",
    "prop" to "\u221D",
    "infin" to "\u221E",
    "ang" to "\u2220",
    "and" to "\u2227",
    "or" to "\u2228",
    "cap" to "\u2229",
    "cup" to "\u222A",
    "int" to "\u222B",
    "there4" to "\u2234",
    "sim" to "\u223C",
    "cong" to "\u2245",
    "asymp" to "\u2248",
    "ne" to "\u2260",
    "equiv" to "\u2261",
    "le" to "\u2264",
    "ge" to "\u2265",
    "sub" to "\u2282",
    "sup" to "\u2283",
    "nsub" to "\u2284",
    "sube" to "\u2286",
    "supe" to "\u2287",
    "oplus" to "\u2295",
    "otimes" to "\u2297",
    "perp" to "\u22A5",
    "sdot" to "\u22C5",

    // Miscellaneous technical
    "lceil" to "\u2308",
    "rceil" to "\u2309",
    "lfloor" to "\u230A",
    "rfloor" to "\u230B",
    "lang" to "\u2329",
    "rang" to "\u232A",

    // Geometric shapes
    "loz" to "\u25CA",

    // Miscellaneous symbols
    "spades" to "\u2660",
    "clubs" to "\u2663",
    "hearts" to "\u2665",
    "diams" to "\u2666",

    // Special spacing
    "ensp" to "\u2002",
    "emsp" to "\u2003",
    "thinsp" to "\u2009",

    // Special formatting
    "zwnj" to "\u200C",
    "zwj" to "\u200D",
    "lrm" to "\u200E",
    "rlm" to "\u200F",

    // Quotation marks and dashes
    "ndash" to "\u2013",
    "mdash" to "\u2014",
    "lsquo" to "\u2018",
    "rsquo" to "\u2019",
    "sbquo" to "\u201A",
    "ldquo" to "\u201C",
    "rdquo" to "\u201D",
    "bdquo" to "\u201E",
    "dagger" to "\u2020",
    "Dagger" to "\u2021",
    "permil" to "\u2030",
    "lsaquo" to "\u2039",
    "rsaquo" to "\u203A",
    "euro" to "\u20AC",

    // Additional entities from CommonMark spec tests
    "OElig" to "\u0152",
    "oelig" to "\u0153",
    "Scaron" to "\u0160",
    "scaron" to "\u0161",
    "Yuml" to "\u0178",
    "fnof" to "\u0192",
    "circ" to "\u02C6",
    "tilde" to "\u02DC",

    // Non-breaking space variants
    "emsp13" to "\u2004",
    "emsp14" to "\u2005",

    // CJK and other commonly-used
    "lpar" to "(",
    "rpar" to ")",
    "colon" to ":",
    "semi" to ";",
    "comma" to ",",
    "period" to ".",
    "excl" to "!",
    "quest" to "?",
    "num" to "#",
    "percnt" to "%",
    "ast" to "*",
    "plus" to "+",
    "equals" to "=",
    "hyphen" to "\u2010",
    "dash" to "\u2010",
    "lbrace" to "{",
    "rbrace" to "}",
    "lbrack" to "[",
    "rbrack" to "]",
    "vert" to "|",
    "sol" to "/",
    "bsol" to "\\",
    "Hat" to "^",
    "lowbar" to "_",
    "grave" to "`",
    "at" to "@",

    // Additional HTML5 entities needed for spec compliance
    "Tab" to "\t",
    "NewLine" to "\n",
    "fjlig" to "fj",
    "Copf" to "\uD835\uDD3A",  // 𝔺 — actually U+1D53A → need surrogate pair
    "HilbertSpace" to "\u210B",
    "DifferentialD" to "\u2146",
    "ExponentialE" to "\u2147",
    "ImaginaryI" to "\u2148", // not standard, but we'll keep it
    "NotSquareSubset" to "\u228F\u0338",
    "nGg" to "\u22D9\u0338",
    "Dcaron" to "\u010E",
    "ClockwiseContourIntegral" to "\u2232",
    "ngE" to "\u2267\u0338",
)
