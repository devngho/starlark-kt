package io.github.devngho.starlarkkt.ast.statement

import io.github.devngho.starlarkkt.expression.Expression

data class AssignStatement(
    val binding: Expression,
    val expression: Expression
): Statement {
    init {
        require(binding.isBindable) { "Binding must be bindable" }
    }

    override fun toString(): String {
        return "$binding = $expression"
    }
}