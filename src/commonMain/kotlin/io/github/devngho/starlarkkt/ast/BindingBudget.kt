package io.github.devngho.starlarkkt.ast

internal class BindingBudget {
    private var visited = 0
    private var depth = 0

    // Limits remain above ordinary source while bounding recursive calls well below stack exhaustion.
    fun <T> visit(action: () -> Result<T>): Result<T> {
        if (visited == MAX_VISITED_NODES || depth == MAX_DEPTH) {
            return Result.failure(BindingLimitExceededException())
        }
        visited += 1
        depth += 1
        return try {
            action()
        } finally {
            depth -= 1
        }
    }

    private companion object {
        const val MAX_DEPTH = 128
        const val MAX_VISITED_NODES = 8_192
    }
}
