package io.github.devngho.starlarkkt

import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.github.devngho.starlarkkt.token.RawToken
import io.github.devngho.starlarkkt.token.Token
import io.github.devngho.starlarkkt.token.Tokenizer
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe

class TokenizerTest: FunSpec({
    val testcases: List<Pair<String, List<Token<*>>>> = listOf(
        "foo()" to listOf(
            Token(type= RawToken.Identifier(value = "foo"), line=1, column=0),
            Token(type= RawToken.Punctuation(value = "("), line=1, column=3),
            Token(type= RawToken.Punctuation(value =")"), line=1, column=4)
        ),
        "bar()" to listOf(
            Token(type= RawToken.Identifier(value = "bar"), line=1, column=0),
            Token(type= RawToken.Punctuation(value = "("), line=1, column=3),
            Token(type= RawToken.Punctuation(value =")"), line=1, column=4)
        ),
        "something = baz()" to listOf(
            Token(type= RawToken.Identifier(value = "something"), line=1, column=0),
            Token(type= RawToken.Whitespace(value = " "), line=1, column=9),
            Token(type= RawToken.Punctuation(value = "="), line=1, column=10),
            Token(type= RawToken.Whitespace(value = " "), line=1, column=11),
            Token(type= RawToken.Identifier(value = "baz"), line=1, column=12),
            Token(type= RawToken.Punctuation(value = "("), line=1, column=15),
            Token(type= RawToken.Punctuation(value =")"), line=1, column=16)
        ),
        "a <<= 5" to listOf(
            Token(type= RawToken.Identifier(value = "a"), line=1, column=0),
            Token(type= RawToken.Whitespace(value = " "), line=1, column=1),
            Token(type= RawToken.Punctuation(value = "<<="), line=1, column=2),
            Token(type= RawToken.Whitespace(value = " "), line=1, column=5),
            Token(type= RawToken.IntLiteral(value = "5", literal = 5.toBigInteger()), line=1, column=6)
        ),
        """("string literal")""" to listOf(
            Token(type= RawToken.Punctuation(value = "("), line=1, column=0),
            Token(type= RawToken.StringLiteral(value = "string literal", literal = "string literal"), line=1, column=1),
            Token(type= RawToken.Punctuation(value = ")"), line=1, column=17)
        )
//        """
//            |a = .234e+1
//            |b = 0. + .0 + 0e0
//            |c = a + b
//            |
//            |def add(x, y):
//            |    return x + y
//            |
//            |result = add(a, b)
//        """.trimMargin() to listOf()
    )

    withData<Pair<String, List<Token<*>>>>({ it.first }, testcases) @Suppress("unchecked_cast") { testcase ->
        val tokenizer = Tokenizer((testcase as Pair<String, List<*>>).first)
        val tokens = tokenizer.tokenize()

        tokens.shouldBeSuccess {
            it.shouldBe(testcase.second)
        }

    }
})