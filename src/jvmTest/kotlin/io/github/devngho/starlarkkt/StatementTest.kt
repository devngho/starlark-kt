package io.github.devngho.starlarkkt

import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.github.devngho.starlarkkt.ast.Lexer
import io.github.devngho.starlarkkt.ast.Parser
import io.github.devngho.starlarkkt.ast.statement.AssignStatement
import io.github.devngho.starlarkkt.ast.statement.File
import io.github.devngho.starlarkkt.expression.Expression
import io.github.devngho.starlarkkt.token.RawToken
import io.github.devngho.starlarkkt.token.Token
import io.github.devngho.starlarkkt.token.Tokenizer
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe

class StatementTest: FunSpec({
    val testcases: List<Pair<String, File>> = listOf(
        """
        |a = a << 5
        |b[0] = 1
        |obj.attr = "value"
        """.trimMargin() to File(
            listOf(
                AssignStatement(
                    binding = Expression.Identifier(name = "a", binding = null),
                    expression = Expression.BinaryOp(
                        operator = Token(RawToken.Punctuation(value = "<<"), line = 1, column = 6),
                        left = Expression.Identifier(name = "a", binding = null),
                        right = Expression.IntLiteral(value = 5.toBigInteger())
                    )
                ),
                AssignStatement(
                    binding = Expression.SubscriptOp(
                        expression = Expression.Identifier(name = "b", binding = null),
                        index = Expression.IntLiteral(value = 0.toBigInteger())
                    ),
                    expression = Expression.IntLiteral(value = 1.toBigInteger())
                ),
                AssignStatement(
                    binding = Expression.BinaryOp(
                        operator = Token(RawToken.Punctuation(value = "."), line = 3, column = 4),
                        left = Expression.Identifier(name = "obj", binding = null),
                        right = Expression.Identifier(name = "attr", binding = null),
                    ),
                    expression = Expression.StringLiteral(value = "value")
                )
            ),
        ),
        """
        |if False:
        |    print("Hello")
        |
        |if a:
        |    print(a)
        |else:
        |    print(b)
        """.trimMargin() to File(
            listOf(
                io.github.devngho.starlarkkt.ast.statement.IfStatement(
                    condition = Expression.Identifier(name = "False", binding = null),
                    ifThen = io.github.devngho.starlarkkt.ast.statement.Block(
                        statements = listOf(
                            io.github.devngho.starlarkkt.ast.statement.ExpressionStatement(
                                expression = Expression.InvokeOp(
                                    callee = Expression.Identifier(name = "print", binding = null),
                                    arguments = listOf(Expression.StringLiteral(value = "Hello"))
                                )
                            )
                        )
                    ),
                    ifElse = null
                ),
                io.github.devngho.starlarkkt.ast.statement.IfStatement(
                    condition = Expression.Identifier(name = "a", binding = null),
                    ifThen = io.github.devngho.starlarkkt.ast.statement.Block(
                        statements = listOf(
                            io.github.devngho.starlarkkt.ast.statement.ExpressionStatement(
                                expression = Expression.InvokeOp(
                                    callee = Expression.Identifier(name = "print", binding = null),
                                    arguments = listOf(Expression.Identifier(name = "a", binding = null))
                                )
                            )
                        )
                    ),
                    ifElse = io.github.devngho.starlarkkt.ast.statement.Block(
                        statements = listOf(
                            io.github.devngho.starlarkkt.ast.statement.ExpressionStatement(
                                expression = Expression.InvokeOp(
                                    callee = Expression.Identifier(name = "print", binding = null),
                                    arguments = listOf(Expression.Identifier(name = "b", binding = null))
                                )
                            )
                        )
                    )
                )
            )
        )
    )

    withData<Pair<String, File>>({ it.first }, testcases) @Suppress("unchecked_cast") { testcase ->
        val tokenizer = Tokenizer(testcase.first)
        val tokens = tokenizer.tokenize()
        val lexer = Lexer(tokens.getOrNull() ?: emptyList())
        val file = Parser(lexer).parseFile()

        file.shouldBeSuccess {
            it.shouldBe(testcase.second)
        }
    }
})