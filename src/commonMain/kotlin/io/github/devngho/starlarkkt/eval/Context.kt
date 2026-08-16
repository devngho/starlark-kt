package io.github.devngho.starlarkkt.eval

import io.github.devngho.starlarkkt.expression.BindingId

data class Context(val declarations: MutableMap<BindingId, Value>, val parent: Context?) {
    fun getDeclaration(name: BindingId): Value? {
        return declarations[name] ?: parent?.getDeclaration(name)
    }

    fun declare(name: BindingId, value: Value) {
        declarations[name] = value
    }
}