package io.github.devngho.starlarkkt.eval

import io.github.devngho.starlarkkt.expression.BindingId

data class Context(val declarations: MutableMap<BindingId, Value>, val globalNames: Map<String, BindingId>, val parent: Context?) {
    fun getDeclaration(name: BindingId): Value? {
        return declarations[name] ?: parent?.getDeclaration(name)
    }

    fun getDeclarationByName(name: String): Value? {
        val bindingId = globalNames[name] ?: return null
        return getDeclaration(bindingId)
    }

    fun declare(name: BindingId, value: Value) {
        declarations[name] = value
    }
}