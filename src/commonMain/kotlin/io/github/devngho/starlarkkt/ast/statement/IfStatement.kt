package io.github.devngho.starlarkkt.ast.statement

import io.github.devngho.starlarkkt.expression.Expression

data class IfStatement(
    val condition: Expression,
    val ifThen: Block,
    val ifElse: Block? = null
): Statement {
    override fun toString(): String {
        return """
if ($condition) {
${ifThen.toString().prependIndent("  ")}
}${ifElse?.let { "\nelse {\n${it.toString().prependIndent("  ")}\n}" } ?: ""}
        """.trimIndent()
    }
}