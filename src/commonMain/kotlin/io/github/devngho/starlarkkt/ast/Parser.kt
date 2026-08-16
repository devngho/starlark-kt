package io.github.devngho.starlarkkt.ast

import io.github.devngho.starlarkkt.ast.statement.*
import io.github.devngho.starlarkkt.expression.ExpressionParser.parseExpression
import io.github.devngho.starlarkkt.token.RawToken

class Parser(val lexer: Lexer) {
    fun parseFile(): Result<File> {
        val statements = mutableListOf<Statement>()
        while (lexer.peek() != null) {
            statements.addAll(parseStatements().getOrElse { return Result.failure(it) })
        }
        return Result.success(File(statements))
    }

    fun parseStatements(minIndentLevel: Int = 0): Result<List<Statement>> {
        val lines = mutableListOf<Statement>()
        var indentLevel = -1

        while (true) {
            val token = lexer.peek(ignoreWhitespace = false) ?: break

            if (indentLevel != 0 && indentLevel != -1) {
                // should be indented, or just a new line
                if (token.type !is RawToken.Whitespace) {
                    break
                }

                if (token.type.value == "\n") {
                    lexer.next(ignoreWhitespace = false) // consume the newline
                    continue
                }

                if (token.type.value == " ") {
                    var level = 0
                    while (lexer.peek(ignoreWhitespace = false)?.type is RawToken.Whitespace && lexer.peek(ignoreWhitespace = false)?.type?.value == " ") {
                        lexer.next(ignoreWhitespace = false)
                        level++
                    }

                    if (level < indentLevel) {
                        break
                    } else if (level > indentLevel) {
                        return Result.failure(IllegalArgumentException("Unexpected indentation level: $level, expected: $indentLevel"))
                    }
                }

                return Result.failure(IllegalArgumentException("Unexpected token: $token"))
            }

            if (indentLevel == -1) {
                // find the indentation level of the next line
                if (token.type is RawToken.Whitespace && token.type.value == "\n") {
                    lexer.next(ignoreWhitespace = false) // consume the newline
                    continue
                }

                if (token.type is RawToken.Whitespace && token.type.value == " ") {
                    var level = 0
                    while (lexer.peek(ignoreWhitespace = false)?.type is RawToken.Whitespace && lexer.peek(ignoreWhitespace = false)?.type?.value == " ") {
                        lexer.next(ignoreWhitespace = false)
                        level++
                    }

                    indentLevel = level

                    if (indentLevel < minIndentLevel) {
                        return Result.failure(IllegalArgumentException("Unexpected indentation level: $indentLevel, expected min: $minIndentLevel"))
                    }
                }

                if (indentLevel == -1) indentLevel = 0
            }

            // here we should be at the correct indentation level, so we can parse a statement
            lines.add(parseSingleStatement(indentLevel).getOrElse { return Result.failure(it) })
        }

        return Result.success(lines)
    }

    fun parseSingleStatement(minIndentLevel: Int): Result<Statement> {
        val firstToken = lexer.peek() ?: return Result.failure(IllegalArgumentException("Unexpected end of input"))

        return when (firstToken.type) {
            is RawToken.Keyword -> {
                when (firstToken.type.value) {
                    "if" -> {
                        lexer.next() // consume 'if'
                        val condition = parseExpression(lexer).getOrElse { return Result.failure(it) }

                        // expect a colon
                        val colonToken = lexer.next()
                        if (colonToken?.type !is RawToken.Punctuation || colonToken.type.value != ":") {
                            return Result.failure(IllegalArgumentException("Expected ':', but got: $colonToken"))
                        }

                        val thenStatements = parseStatements(minIndentLevel).getOrElse { return Result.failure(it) }
                        val elseStatements = if (lexer.peek()?.type is RawToken.Keyword && lexer.peek()?.type?.value == "else") {
                            lexer.next() // consume 'else'

                            val elseColonToken = lexer.next()
                            if (elseColonToken?.type !is RawToken.Punctuation || elseColonToken.type.value != ":") {
                                return Result.failure(IllegalArgumentException("Expected ':', but got: $elseColonToken"))
                            }

                            parseStatements(minIndentLevel).getOrElse { return Result.failure(it) }
                        } else {
                            null
                        }
                        Result.success(IfStatement(condition, Block(thenStatements), elseStatements?.let { Block(it) }))
                    }
                    "for" -> TODO()
                    "def" -> TODO()
                    "return" -> TODO()
                    else -> Result.failure(IllegalArgumentException("Unexpected keyword: ${firstToken.type.value}"))
                }
            }

            is RawToken.Identifier -> parseAssignmentOrExpressionStatement()
            else -> Result.failure(IllegalArgumentException("Unexpected token: $firstToken"))
        }
    }

    fun parseAssignmentOrExpressionStatement(): Result<Statement> {
        val expr = parseExpression(lexer).getOrElse { return Result.failure(it) }
        val nextToken = lexer.peek()

        return Result.success(if (nextToken?.type is RawToken.Punctuation && nextToken.type.value == "=") {
            lexer.next() // consume '='
            val valueExpr = parseExpression(lexer).getOrElse { throw it }
            AssignStatement(expr, valueExpr)
        } else {
            ExpressionStatement(expr)
        })
    }
}