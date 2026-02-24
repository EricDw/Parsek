package parsek.benchmark

data class SpecExample(
    val markdown: String,
    val html: String,
    val example: Int,
    val section: String,
)

fun loadSpecExamples(): List<SpecExample> {
    val json = SpecExample::class.java.getResourceAsStream("/spec.json")
        ?.bufferedReader()
        ?.readText()
        ?: error("spec.json not found on classpath")
    return parseSpecJson(json)
}

internal fun parseSpecJson(json: String): List<SpecExample> {
    val examples = mutableListOf<SpecExample>()
    var i = 0
    val len = json.length

    fun skipWhitespace() {
        while (i < len && json[i].isWhitespace()) i++
    }

    fun expect(ch: Char) {
        skipWhitespace()
        require(i < len && json[i] == ch) { "Expected '$ch' at position $i" }
        i++
    }

    fun parseJsonString(): String {
        skipWhitespace()
        require(i < len && json[i] == '"') { "Expected '\"' at position $i" }
        i++
        val sb = StringBuilder()
        while (i < len) {
            val ch = json[i]
            if (ch == '"') { i++; return sb.toString() }
            if (ch == '\\') {
                i++
                require(i < len)
                when (json[i]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'u' -> {
                        i++
                        require(i + 4 <= len)
                        val hex = json.substring(i, i + 4)
                        sb.append(hex.toInt(16).toChar())
                        i += 3
                    }
                    else -> { sb.append('\\'); sb.append(json[i]) }
                }
            } else {
                sb.append(ch)
            }
            i++
        }
        error("Unterminated string")
    }

    fun parseJsonInt(): Int {
        skipWhitespace()
        val start = i
        if (i < len && json[i] == '-') i++
        while (i < len && json[i].isDigit()) i++
        return json.substring(start, i).toInt()
    }

    skipWhitespace()
    expect('[')
    skipWhitespace()

    while (i < len && json[i] != ']') {
        expect('{')
        var markdown = ""
        var html = ""
        var example = 0
        var section = ""

        skipWhitespace()
        while (i < len && json[i] != '}') {
            val key = parseJsonString()
            expect(':')
            when (key) {
                "markdown" -> markdown = parseJsonString()
                "html" -> html = parseJsonString()
                "example" -> example = parseJsonInt()
                "section" -> section = parseJsonString()
                else -> {
                    skipWhitespace()
                    if (i < len && json[i] == '"') parseJsonString() else parseJsonInt()
                }
            }
            skipWhitespace()
            if (i < len && json[i] == ',') i++
            skipWhitespace()
        }

        expect('}')
        examples.add(SpecExample(markdown, html, example, section))
        skipWhitespace()
        if (i < len && json[i] == ',') i++
        skipWhitespace()
    }

    expect(']')
    return examples
}
