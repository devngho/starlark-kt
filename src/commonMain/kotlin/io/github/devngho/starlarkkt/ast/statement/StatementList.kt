package io.github.devngho.starlarkkt.ast.statement

sealed class StatementList: Statement {
    abstract val statements: List<Statement>

    override fun toString(): String {
        return "  # ${this::class.simpleName}\n" + statements.joinToString("\n") {
            it.toString().prependIndent("  ")
        }
    }
}