package io.github.devngho.starlarkkt.expression

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.github.devngho.starlarkkt.token.RawToken
import io.github.devngho.starlarkkt.token.Token

sealed interface Expression {
    data class IntLiteral(val value: BigInteger) : Expression
    data class DecimalLiteral(val value: BigDecimal) : Expression
    data class StringLiteral(val value: String) : Expression

    data class Identifier(val name: String, val binding: Binding?) : Expression
    data class BinaryOp(val left: Expression, val operator: Token<*>, val right: Expression) : Expression {
        init {
            require((operator.type is RawToken.Punctuation && operator.type.value in RawToken.Punctuation.validBinaryOps) || (
                operator.type is RawToken.Keyword && operator.type.value in RawToken.Keyword.validBinaryKeywords
            )) { "Invalid binary operator: ${operator.type}" }
        }

        override fun toString(): String = "($left ${operator.type} $right)"
    }

    data class UnaryOp(val operator: Token<*>, val operand: Expression) : Expression {
        init {
            require((operator.type is RawToken.Punctuation && operator.type.value in RawToken.Punctuation.validUnaryOps) ||
                    (operator.type is RawToken.Keyword && operator.type.value in RawToken.Keyword.validUnaryKeywords)) { "Invalid unary operator: ${operator.type}" }
        }

        override fun toString(): String = "${operator.type}($operand)"
    }

    data class Invoke(val callee: Expression, val arguments: List<Expression>) : Expression {
        override fun toString(): String = "($callee)(${arguments.joinToString(", ")})"
    }

    companion object {
        fun fromToken(token: Token<*>): Result<Expression> = when (val type = token.type) {
            is RawToken.IntLiteral -> Result.success(IntLiteral(type.value))
            is RawToken.DecimalLiteral -> Result.success(DecimalLiteral(type.value))
            is RawToken.StringLiteral -> Result.success(StringLiteral(type.value))
            is RawToken.Identifier -> Result.success(Identifier(type.value, binding = null))

            else -> Result.failure(IllegalArgumentException("Cannot convert token of type ${token.type} to an expression"))
        }
    }
}