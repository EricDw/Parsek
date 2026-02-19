package parsek

class ParserInput<out I : Any>(val input: List<I>, val index: Int) {
    val isAtEnd: Boolean get() = index >= input.size
    fun current(): I = input[index]
    fun advance(): ParserInput<I> = ParserInput(input, index + 1)

    companion object {
        fun <I : Any> of(input: Collection<I>, index: Int = 0) = ParserInput(input.toList(), index)
    }
}
