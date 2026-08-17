package io.github.devngho.starlarkkt.eval

@DslMarker
annotation class ContextDSLMarker

@ContextDSLMarker
class ContextDeclarationsDSL {

    @ContextDSLMarker
    class ContextTypeDeclarationsDSL {
        private var default: (List<Value>, Value.Type) -> Value = { it, _ -> it[0] }
        private val methods = mutableMapOf<String, (List<Value>) -> Value>()

        operator fun String.invoke(implementation: (List<Value>) -> Value) {
            methods[this] = implementation
        }

        fun default(implementation: (List<Value>, Value.Type) -> Value) {
            default = implementation
        }

        fun build(name: String): Value.Type {
            return Value.Type(name, default, methods.mapValues { (_, impl) ->
                Value.Runnable(impl)
            })
        }
    }

    private val declarations = mutableMapOf<String, Value>()

    operator fun String.invoke(value: Value) {
        declarations[this] = value
    }

    fun function(name: String, implementation: (List<Value>) -> Value) {
        declarations[name] = Value.Runnable(implementation)
    }

    fun type(name: String, block: ContextTypeDeclarationsDSL.() -> Unit) {
        val dsl = ContextTypeDeclarationsDSL()
        dsl.block()
        declarations[name] = dsl.build(name)
    }

    fun context(other: ContextDeclarations): ContextDeclarations {
        declarations.putAll(other)
        return declarations
    }

    companion object {
        fun context(block: ContextDeclarationsDSL.() -> Unit): ContextDeclarations {
            val dsl = ContextDeclarationsDSL()
            dsl.block()

            return dsl.declarations
        }
    }
}