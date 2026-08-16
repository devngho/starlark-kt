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

    data class Runnable(val implementation: (List<Value>) -> Value): Value

    fun toBoolean(): Boolean {
        return when (this) {
            is Unit -> false
            is Runnable -> true
            is Int -> value != BigInteger.ZERO
            is Decimal -> value != BigDecimal.ZERO
            is String -> value.isNotEmpty()
            is Bool -> this is True
        }
    }

    fun toStringValue(): kotlin.String {
        return when (this) {
            is Unit -> "<unit>"
            is Runnable -> "<runnable>"
            is Int -> value.toString()
            is Decimal -> value.toString()
            is String -> value
            is Bool -> if (this is True) "true" else "false"
        }
    }
}