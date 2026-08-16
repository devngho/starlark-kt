package io.github.devngho.starlarkkt

import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.github.devngho.starlarkkt.ast.Lexer
import io.github.devngho.starlarkkt.expression.Expression
import io.github.devngho.starlarkkt.expression.ExpressionParser
import io.github.devngho.starlarkkt.token.RawToken
import io.github.devngho.starlarkkt.token.Token
import io.github.devngho.starlarkkt.token.Tokenizer
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe

class ExpressionParserTest: FunSpec({
    val testcases: List<Pair<String, Expression>> = listOf(
        "a << 5" to
            Expression.BinaryOp(
                operator = Token(RawToken.Punctuation(value = "<<"), line = 1, column = 2),
                left = Expression.Identifier(name = "a", binding = null),
                right = Expression.IntLiteral(value =  5.toBigInteger())
            ),
        "a + b * -c" to
            Expression.BinaryOp(
                operator = Token(RawToken.Punctuation(value = "+"), line = 1, column = 2),
                left = Expression.Identifier(name = "a", binding = null),
                right = Expression.BinaryOp(
                    operator = Token(RawToken.Punctuation(value = "*"), line = 1, column = 6),
                    left = Expression.Identifier(name = "b", binding = null),
                    right = Expression.UnaryOp(
                        operator = Token(RawToken.Punctuation(value = "-"), line = 1, column = 8),
                        operand = Expression.Identifier(name = "c", binding = null)
                    )
                )
            ),
        "1 or 'hello'" to
            Expression.BinaryOp(
                operator = Token(RawToken.Keyword(value = "or"), line = 1, column = 2),
                left = Expression.IntLiteral(value = 1.toBigInteger()),
                right = Expression.StringLiteral(value = "hello")
            ),
        "--list.len" to
            Expression.UnaryOp(
                operator = Token(RawToken.Punctuation(value = "-"), line = 1, column = 0),
                operand = Expression.UnaryOp(
                    operator = Token(RawToken.Punctuation(value = "-"), line = 1, column = 1),
                    operand = Expression.BinaryOp(
                        operator = Token(RawToken.Punctuation(value = "."), line = 1, column = 6),
                        left = Expression.Identifier(name = "list", binding = null),
                        right = Expression.Identifier(name = "len", binding = null)
                    )
                )
            ),
        "list.len(a)" to
            Expression.InvokeOp(
                callee = Expression.BinaryOp(
                    operator = Token(RawToken.Punctuation(value = "."), line = 1, column = 4),
                    left = Expression.Identifier(name = "list", binding = null),
                    right = Expression.Identifier(name = "len", binding = null)
                ),
                arguments = listOf(Expression.Identifier(name = "a", binding = null))
            ),
        "[a, b, c][1:2:1][0]" to
            Expression.SubscriptOp(
                expression = Expression.SliceOp(
                    expression = Expression.ListLiteral(
                        elements = listOf(
                            Expression.Identifier(name = "a", binding = null),
                            Expression.Identifier(name = "b", binding = null),
                            Expression.Identifier(name = "c", binding = null)
                        )
                    ),
                    start = Expression.IntLiteral(value = 1.toBigInteger()),
                    end = Expression.IntLiteral(value = 2.toBigInteger()),
                    stride = Expression.IntLiteral(value = 1.toBigInteger())
                ),
                index = Expression.IntLiteral(value = 0.toBigInteger())
            ),
        "a[:][::]" to
            Expression.SliceOp(
                expression = Expression.SliceOp(
                    expression = Expression.Identifier(name = "a", binding = null),
                    start = null,
                    end = null,
                    stride = null,
                ),
                start = null,
                end = null,
                stride = null
            )
    )

    withData<Pair<String, Expression>>({ it.first }, testcases) @Suppress("unchecked_cast") { testcase ->
        val tokenizer = Tokenizer((testcase as Pair<String, List<*>>).first)
        val tokens = tokenizer.tokenize()
        val lexer = Lexer(tokens.getOrNull() ?: emptyList())
        val expression = ExpressionParser.parseExpression(lexer)

        expression.shouldBeSuccess {
            it.shouldBe(testcase.second)
        }
    }
})