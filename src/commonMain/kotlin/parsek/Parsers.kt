package parsek

fun <I : Any> satisfy(predicate: (I) -> Boolean): Parser<I, I> =
    Parser { input ->
        when {
            input.isAtEnd -> Failure("Unexpected end of input", input.index, input)
            predicate(input.current()) -> Success(input.current(), input.index + 1, input)
            else -> Failure("Unexpected ${input.current()} at index ${input.index}", input.index, input)
        }
    }
