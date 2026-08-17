package io.github.devngho.starlarkkt.expression

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.github.devngho.starlarkkt.token.RawToken
import io.github.devngho.starlarkkt.token.Token

sealed interface Expression {
    val isBindable: Boolean
        get() = false

    data class IntLiteral(val value: BigInteger) : Expression {
        override fun toString(): String = "$value"
    }
    data class DecimalLiteral(val value: BigDecimal) : Expression {
        override fun toString(): String = "$value"
    }
    data class StringLiteral(val value: String) : Expression {
        override fun toString(): String = "\"$value\""
    }

    data class ListLiteral(val elements: List<Expression>) : Expression {
        override fun toString(): String = "[${elements.joinToString(", ")}]"
    }

    data class LambdaLiteral(val parameters: List<Identifier>, val body: Expression) : Expression {
        override fun toString(): String = "Lambda(${parameters.joinToString(", ")}) -> $body"
    }

    data class BuiltinObject(val mappings: Map<String, Expression>) : Expression {
        override fun toString(): String = "Builtin(${mappings.entries.joinToString(", ") { "${it.key}: ${it.value}" }})"
    }

    data class Identifier(val name: String, val binding: Binding?) : Expression {
        override val isBindable = true

        override fun toString(): String = "Id($name)"
    }
    data class BinaryOp(val left: Expression, val operator: Token<*>, val right: Expression) : Expression {
        init {
            require((operator.type is RawToken.Punctuation && operator.type.value in RawToken.Punctuation.validBinaryOps) || (
                operator.type is RawToken.Keyword && operator.type.value in RawToken.Keyword.validBinaryKeywords
            )) { "Invalid binary operator: ${operator.type}" }
        }

        override val isBindable: Boolean
            get() = operator.type is RawToken.Punctuation && operator.type.value in listOf(".")

        override fun toString(): String = "($left ${operator.type} $right)"

        companion object {
            val opLeftPrecedence = mapOf(
                "if" to 0,
                "or" to 1,
                "and" to 3,
                "==" to 5, "!=" to 5, "<" to 5, ">" to 5, "<=" to 5, ">=" to 5, "in" to 5, "not in" to 5,
                "|" to 7,
                "^" to 9,
                "&" to 11,
                "<<" to 13, ">>" to 13,
                "+" to 15, "-" to 15,
                "*" to 17, "/" to 17, "//" to 17, "%" to 17,
                "." to 200
            )

            val opRightPrecedence = mapOf(
                "if" to -1,
                "or" to 2,
                "and" to 4,
                "==" to 6, "!=" to 6, "<" to 6, ">" to 6, "<=" to 6, ">=" to 6, "in" to 6, "not in" to 6,
                "|" to 8,
                "^" to 10,
                "&" to 12,
                "<<" to 14, ">>" to 14,
                "+" to 16, "-" to 16,
                "*" to 18, "/" to 18, "//" to 18, "%" to 18,
                "." to 200
            )
        }
    }

    data class UnaryOp(val operator: Token<*>, val operand: Expression) : Expression {
        init {
            require((operator.type is RawToken.Punctuation && operator.type.value in RawToken.Punctuation.validUnaryOps) ||
                    (operator.type is RawToken.Keyword && operator.type.value in RawToken.Keyword.validUnaryKeywords)) { "Invalid unary operator: ${operator.type}" }
        }

        override fun toString(): String = "${operator.type}($operand)"

        companion object {
            // assuming unary operators have the highest precedence
            val opPrecedence = mapOf(
                "+" to 100, "-" to 100, "~" to 100, "not" to 100
            )
        }
    }

    data class ConditionalOp(val condition: Expression, val trueBranch: Expression, val falseBranch: Expression) : Expression {
        override fun toString(): String = "($condition ? $trueBranch : $falseBranch)"

        companion object {
            const val PRECEDENCE = 20
        }
    }

    data class SubscriptOp(val expression: Expression, val index: Expression) : Expression {
        override fun toString(): String = "Subscript($expression)[$index]"

        override val isBindable: Boolean
            get() = true

        companion object {
            const val PRECEDENCE = 50
        }
    }

    data class SliceOp(val expression: Expression, val start: Expression?, val end: Expression?, val stride: Expression?) : Expression {
        override fun toString(): String = "Slice($expression)[${start ?: ""}:${end ?: ""}${stride?.let { ":$it" } ?: ""}]"

        override val isBindable: Boolean
            get() = true

        companion object {
            const val PRECEDENCE = 50
        }
    }

    data class InvokeOp(val callee: Expression, val arguments: List<Expression>) : Expression {
        override fun toString(): String = "Invoke($callee)(${arguments.joinToString(", ")})"

        companion object {
            const val PRECEDENCE = 50
        }
    }

    companion object {
        fun fromToken(token: Token<*>): Result<Expression> = when (val type = token.type) {
            is RawToken.IntLiteral -> Result.success(IntLiteral(type.literal))
            is RawToken.DecimalLiteral -> Result.success(DecimalLiteral(type.literal))
            is RawToken.StringLiteral -> Result.success(StringLiteral(type.literal))
            is RawToken.Identifier -> Result.success(Identifier(type.value, binding = null))

            else -> Result.failure(IllegalArgumentException("Cannot convert token of type ${token.type} to an expression"))
        }
    }
}