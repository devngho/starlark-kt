package io.github.devngho.starlarkkt.token

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger

sealed interface RawToken {
    val isIgnored: Boolean
        get() = false

    data class Whitespace(val value: Char) : RawToken {
        override val isIgnored: Boolean = true

        companion object {
            val validWhitespaces = listOf('\u0020', '\u0009', '\u000d', '\u000a')
        }
    }

    data class Punctuation(val value: String) : RawToken {
        companion object {
            val validPunctuations = """+    -    *    /    //   %    **
~    &    |    ^    <<   >>
.    ,    =    ;    :
(    )    [    ]    {    }
<    >    >=   <=   ==   !=
+=   -=   *=   /=   //=  %=
&=   |=   ^=   <<=  >>=""".split(" ", "\n").map { it.trim() }.filter { it.isNotEmpty() }

            val validBinaryOps = listOf("+", "-", "*", "/", "//", "%", "**", "&", "|", "^", "<<", ">>", "<", ">", ">=", "<=", "==", "!=")
            val validUnaryOps = listOf("+", "-", "~")

            val validPunctuationsByLength = validPunctuations.groupBy { it.length }
        }
    }

    data class Comment(val value: String) : RawToken {
        override val isIgnored: Boolean = true
    }

    data class Keyword(val value: String) : RawToken {
        companion object {
            val validKeywords = listOf(
                "and", "else", "load", "break", "for", "not", "continue", "if", "or", "def", "in", "pass", "elif", "lambda", "return"
            )
            val reservedKeywords = listOf(
                "as", "global", "assert", "import", "async", "is", "await", "nonlocal", "class", "del", "try", "except", "while", "finally", "with", "from", "yield"
            )

            val validBinaryKeywords = listOf("and", "or", "in")
            val validUnaryKeywords = listOf("not")
        }
    }

    data class Identifier(val value: String) : RawToken

    data class StringLiteral(val value: String) : RawToken
    data class IntLiteral(val value: BigInteger) : RawToken
    data class DecimalLiteral(val value: BigDecimal) : RawToken
}

data class Token<out T : RawToken>(
    val type: T,
    val line: Int,
    val column: Int
)