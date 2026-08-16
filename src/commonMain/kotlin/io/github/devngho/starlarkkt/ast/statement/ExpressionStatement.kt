package io.github.devngho.starlarkkt.ast.statement

import io.github.devngho.starlarkkt.expression.Expression

data class ExpressionStatement(
    val expression: Expression
): Statement {
    override fun toString(): String = expression.toString()
}