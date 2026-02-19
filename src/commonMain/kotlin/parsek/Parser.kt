package parsek

fun interface Parser<in I : Any, out O : Any> {
    operator fun invoke(input: ParserInput<@UnsafeVariance I>): ParseResult<@UnsafeVariance I, O>
}
