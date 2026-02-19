package parsek

sealed class ParseResult<out I : Any, out O : Any>
data class Success<out I : Any, out O : Any>(val value: O, val nextIndex: Int, val input: ParserInput<I>) : ParseResult<I, O>()
data class Failure<out I : Any>(val message: String, val index: Int, val input: ParserInput<I>) : ParseResult<I, Nothing>()
