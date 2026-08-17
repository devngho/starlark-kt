package io.github.devngho.starlarkkt.eval

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger

sealed interface Value {
    data object Unit: Value
    interface Bool

    data object True: Value, Bool
    data object False: Value, Bool

    data class Int(val value: BigInteger): Value
    data class Decimal(val value: BigDecimal): Value
    data class String(val value: kotlin.String): Value

    interface IRunnable: Value {
        val implementation: (List<Value>) -> Value
    }

    data class Runnable(override val implementation: (List<Value>) -> Value): IRunnable
    data class Iterable(val implementation: () -> Iterator<Value>): Value

    data class BuiltinObject(val mappings: Map<kotlin.String, Any>, val type: Type): Value
    data class Type(val name: kotlin.String, val default: (List<Value>, Type) -> Value, val methods: Map<kotlin.String, Runnable>): IRunnable {
        override val implementation: (List<Value>) -> Value
            get() = { args -> default(args, this) }
    }

    fun toBoolean(): Boolean {
        return when (this) {
            is Unit -> false
            is Int -> value != BigInteger.ZERO
            is Decimal -> value != BigDecimal.ZERO
            is String -> value.isNotEmpty()
            is Bool -> this is True
            is Iterable -> true
            is IRunnable -> true
            is BuiltinObject -> true
        }
    }

    fun toStringValue(): kotlin.String {
        return when (this) {
            is Unit -> "<unit>"
            is Int -> value.toString()
            is Decimal -> value.toString()
            is String -> value
            is Bool -> if (this is True) "true" else "false"
            is Iterable -> "<builtin iterable>"
            is Type -> "<type $name>"
            is IRunnable -> "<runnable>"
            is BuiltinObject -> "<builtin object of type ${type.name}>"
        }
    }
}