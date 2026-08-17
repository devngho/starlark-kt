package io.github.devngho.starlarkkt.builtin

import io.github.devngho.starlarkkt.eval.ContextDeclarationsDSL.Companion.context
import io.github.devngho.starlarkkt.eval.Value

val types = context {
    "False"(Value.False)
    "True"(Value.True)

    type("bool") {
        default { it, _ ->
            check(it.size == 1) { "bool() takes exactly one argument (${it.size} given)" }
            if (it[0].toBoolean()) Value.True else Value.False
        }
    }

    "None"(Value.Unit)

    type("list") {
        default { it, listType ->
            check(it.size == 1) { "list() takes exactly one argument (${it.size} given)" }

            Value.BuiltinObject(mapOf("value" to (it[0] as Value.Iterable).implementation().asSequence().toMutableList()), listType)
        }

        "__eq__" block@{
            check(it.size == 2) { "list.__eq__() takes exactly two arguments (${it.size} given)" }
            val list1 = it[0]
            val list2 = it[1]

            if (list1 is Value.BuiltinObject && list1.type.name == "list" &&
                list2 is Value.BuiltinObject && list2.type.name == "list") {
                val listValue1 = list1.mappings["value"]
                val listValue2 = list2.mappings["value"]

                if (listValue1 is List<*> && listValue2 is List<*>) {
                    return@block if (listValue1 == listValue2) Value.True else Value.False
                } else {
                    throw IllegalArgumentException("list.__eq__() called on a non-list object")
                }
            } else {
                throw IllegalArgumentException("list.__eq__() called on a non-list object")
            }
        }

        "append" block@{
            check(it.size == 2) { "list.append() takes exactly two arguments (${it.size} given)" }
            val list = it[0]
            val value = it[1]

            if (list is Value.BuiltinObject && list.type.name == "list") {
                val listValue = list.mappings["value"]
                if (listValue is MutableList<*>) {
                    (listValue as MutableList<Value>).add(value)
                    return@block Value.Unit
                } else {
                    throw IllegalArgumentException("list.append() called on a non-list object")
                }
            } else {
                throw IllegalArgumentException("list.append() called on a non-list object")
            }
        }
    }
}

val typeNames = types.keys.toSet()