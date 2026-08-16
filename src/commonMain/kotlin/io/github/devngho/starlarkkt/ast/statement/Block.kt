package io.github.devngho.starlarkkt.ast.statement

data class Block(
    override val statements: List<Statement>
) : StatementList() {
    override fun toString(): String {
        return super.toString()
    }
}