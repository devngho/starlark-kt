package io.github.devngho.starlarkkt.expression

import kotlin.jvm.JvmInline

@JvmInline
value class BindingId(val id: Int) {
    override fun toString(): String = "BindingId($id)"
}

data class Binding(val id: BindingId, val scope: BindingScope)

enum class BindingScope {
    GLOBAL,
    PREDECLARED,
    LOCAL,
    FREE,
    CELL,
}
