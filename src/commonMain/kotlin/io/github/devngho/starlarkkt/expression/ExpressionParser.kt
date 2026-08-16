package io.github.devngho.starlarkkt.expression

import io.github.devngho.starlarkkt.ast.Lexer
import io.github.devngho.starlarkkt.token.RawToken

object ExpressionParser {
    fun parseExpression(lexer: Lexer, precedence: Int = 0): Result<Expression> {
        // pratt parsing

        var left = lexer.next().let { token ->
            checkNotNull(token)

            when (val type = token.type) {
                is RawToken.Whitespace, is RawToken.Comment -> return Result.failure(IllegalArgumentException("Unexpected token: $token"))

                is RawToken.IntLiteral -> Expression.IntLiteral(type.literal)
                is RawToken.DecimalLiteral -> Expression.DecimalLiteral(type.literal)
                is RawToken.StringLiteral -> Expression.StringLiteral(type.literal)
                is RawToken.Identifier -> Expression.Identifier(type.value, null)
                is RawToken.Keyword -> {
                    if (type.value in RawToken.Keyword.validUnaryKeywords) {
                        val operand = parseExpression(lexer, Expression.UnaryOp.opPrecedence[type.value] ?: 0).getOrElse { return Result.failure(it) }
                        Expression.UnaryOp(token, operand)
                    } else if (type.value == "lambda") {
                        // Parse lambda expression
                        val params = mutableListOf<Expression.Identifier>()
                        while (true) {
                            val nextToken = lexer.peek()
                            if (nextToken?.type is RawToken.Identifier) {
                                params.add(Expression.Identifier(nextToken.type.value, null))
                                lexer.next() // consume identifier

                                // check comma
                                val afterParamToken = lexer.peek()
                                if (afterParamToken?.type is RawToken.Punctuation && afterParamToken.type.value == ",") {
                                    lexer.next() // consume ','
                                    continue
                                } else if (afterParamToken?.type is RawToken.Punctuation && afterParamToken.type.value == ":") {
                                    lexer.next() // consume ':'
                                    break
                                } else {
                                    return Result.failure(IllegalArgumentException("Unexpected token: $afterParamToken"))
                                }
                            } else if (nextToken?.type is RawToken.Punctuation && nextToken.type.value == ":") {
                                lexer.next() // consume ':'
                                break
                            } else {
                                return Result.failure(IllegalArgumentException("Unexpected token: $token"))
                            }
                        }

                        val body = parseExpression(lexer).getOrElse { return Result.failure(it) }

                        Expression.LambdaLiteral(params, body)
                    } else {
                        return Result.failure(IllegalArgumentException("Unexpected token: $token"))
                    }
                }

                is RawToken.Punctuation -> {
                    when(type.value) {
                        in RawToken.Punctuation.validUnaryOps -> {
                            val operand = parseExpression(lexer, Expression.UnaryOp.opPrecedence[type.value] ?: 0).getOrElse {
                                return Result.failure(it)
                            }
                            Expression.UnaryOp(token, operand)
                        }
                        "(" -> {
                            val expr = parseExpression(lexer).getOrElse { return Result.failure(it) }
                            val nextToken = lexer.next()
                            if (nextToken?.type is RawToken.Punctuation && nextToken.type.value == ")") {
                                expr
                            } else {
                                return Result.failure(IllegalArgumentException("Expected ')', but got: $nextToken"))
                            }
                        }
                        "[" -> {
                            // list literal

                            val elements = mutableListOf<Expression>()
                            while (true) {
                                val nextToken = lexer.peek()
                                if (nextToken?.type is RawToken.Punctuation && nextToken.type.value == "]") {
                                    lexer.next() // consume ']'"
                                    break
                                }

                                val element = parseExpression(lexer).getOrElse { return Result.failure(it) }
                                elements.add(element)

                                val nextTokenAfterElement = lexer.peek()
                                if (nextTokenAfterElement?.type is RawToken.Punctuation && nextTokenAfterElement.type.value == ",") {
                                    lexer.next() // consume ','
                                } else if (nextTokenAfterElement?.type is RawToken.Punctuation && nextTokenAfterElement.type.value == "]") {
                                    lexer.next() // consume ']'
                                    break
                                } else {
                                    return Result.failure(IllegalArgumentException("Expected ',' or ']', but got: $nextTokenAfterElement"))
                                }
                            }

                            Expression.ListLiteral(elements)
                        }
                        else -> {
                            return Result.failure(IllegalArgumentException("Unexpected token: $token"))
                        }
                    }
                }
            }
        }

        while (true) {
            val nextToken = lexer.peek() ?: break

            // function invocation
            if (nextToken.type is RawToken.Punctuation && nextToken.type.value == "(") {
                // check precedence
                if (Expression.InvokeOp.PRECEDENCE < precedence) {
                    break
                }

                // check left is a bindable expression
                if (!left.isBindable) {
                    return Result.failure(IllegalArgumentException("Cannot invoke a non-bindable expression: $left"))
                }

                lexer.next() // consume '('
                val args = mutableListOf<Expression>()
                while (true) {
                    val arg = parseExpression(lexer).getOrElse { return Result.failure(it) }
                    args.add(arg)
                    if (lexer.peek()?.type is RawToken.Punctuation && lexer.peek()?.type?.value == ",") {
                        lexer.next() // consume ','
                    } else {
                        break
                    }
                }
                val nextTokenAfterArgs = lexer.next()
                if (nextTokenAfterArgs?.type is RawToken.Punctuation && nextTokenAfterArgs.type.value == ")") {
                    left = Expression.InvokeOp(left, args)
                } else {
                    return Result.failure(IllegalArgumentException("Expected ')', but got: $nextTokenAfterArgs"))
                }

                continue
            }

            // list subscript / slice
            if (nextToken.type is RawToken.Punctuation && nextToken.type.value == "[") {
                // check precedence
                if (Expression.SubscriptOp.PRECEDENCE < precedence) {
                    break
                }

                lexer.next() // consume '['
                val args = mutableListOf<Expression>()
                val sliceArgs = mutableListOf<Expression?>()

                if (!(lexer.peek()?.type is RawToken.Punctuation && lexer.peek()?.type?.value == ":")) {
                    while (true) {
                        val arg = parseExpression(lexer).getOrElse { return Result.failure(it) }
                        args.add(arg)
                        if (lexer.peek()?.type is RawToken.Punctuation && lexer.peek()?.type?.value == ",") {
                            lexer.next() // consume ','
                        } else {
                            break
                        }
                    }
                } else {
                    sliceArgs.add(null) // start is null
                }

                // if the next token is a colon, then this is a slice
                if (lexer.peek()?.type is RawToken.Punctuation && lexer.peek()?.type?.value == ":") {
                    lexer.next() // consume ':'
                    if (args.isNotEmpty()) sliceArgs.add(args[0])

                    while (true) {
                        if (lexer.peek()?.type is RawToken.Punctuation && lexer.peek()?.type?.value == "]") {
                            sliceArgs.add(null) // last thing is null
                            break
                        } else if (lexer.peek()?.type is RawToken.Punctuation && lexer.peek()?.type?.value == ":") {
                            sliceArgs.add(null) // this thing is null
                            lexer.next() // consume ':'
                            continue
                        }

                        val arg = parseExpression(lexer).getOrElse { return Result.failure(it) }
                        sliceArgs.add(arg)
                        if (lexer.peek()?.type is RawToken.Punctuation && lexer.peek()?.type?.value == ":") {
                            lexer.next() // consume ':'
                        } else {
                            break
                        }
                    }
                }

                val nextTokenAfterArgs = lexer.next()
                if (nextTokenAfterArgs?.type is RawToken.Punctuation && nextTokenAfterArgs.type.value == "]") {
                    left = if (sliceArgs.isNotEmpty()) {
                        Expression.SliceOp(left, sliceArgs.getOrNull(0), sliceArgs.getOrNull(1), sliceArgs.getOrNull(2))
                    } else {
                        Expression.SubscriptOp(left, args[0])
                    }
                } else {
                    return Result.failure(IllegalArgumentException("Expected ']', but got: $nextTokenAfterArgs"))
                }

                continue
            }

            val nextPrecedence = when (val type = nextToken.type) {
                is RawToken.Punctuation -> Expression.BinaryOp.opLeftPrecedence[type.value] ?: break
                is RawToken.Keyword -> Expression.BinaryOp.opLeftPrecedence[type.value] ?: break
                else -> break
            }

            if (nextPrecedence < precedence) {
                break
            }

            lexer.next() // consume operator

            val right = parseExpression(lexer, Expression.BinaryOp.opRightPrecedence[nextToken.type.value] ?: 0).getOrElse { return Result.failure(it) }

            left = Expression.BinaryOp(left, nextToken, right)
        }

        return Result.success(left)
    }
}