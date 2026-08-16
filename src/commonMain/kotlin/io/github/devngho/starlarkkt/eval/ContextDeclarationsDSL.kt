package io.github.devngho.starlarkkt.eval

class ContextDeclarationsDSL {
    private val declarations = mutableMapOf<String, Value>()

    operator fun String.invoke(value: Value) {
        declarations[this] = value
    }

    fun function(name: String, implementation: (List<Value>) -> Value) {
        declarations[name] = Value.Runnable(implementation)
    }

    companion object {
        fun context(block: ContextDeclarationsDSL.() -> Unit): ContextDeclarations {
            val dsl = ContextDeclarationsDSL()
            dsl.block()

            return dsl.declarations
        }
    }
}