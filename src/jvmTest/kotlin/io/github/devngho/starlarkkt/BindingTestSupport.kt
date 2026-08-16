package io.github.devngho.starlarkkt

import io.github.devngho.starlarkkt.ast.Lexer
import io.github.devngho.starlarkkt.ast.Parser
import io.github.devngho.starlarkkt.ast.statement.AssignStatement
import io.github.devngho.starlarkkt.ast.statement.ExpressionStatement
import io.github.devngho.starlarkkt.ast.statement.File
import io.github.devngho.starlarkkt.expression.Expression
import io.github.devngho.starlarkkt.token.RawToken
import io.github.devngho.starlarkkt.token.Token
import io.github.devngho.starlarkkt.token.Tokenizer
import io.kotest.matchers.types.shouldBeInstanceOf

internal fun parseFile(source: String) = Parser(
    Lexer(Tokenizer(source).tokenize().getOrThrow()),
).parseFile().getOrThrow()

internal fun assignmentTargetBinding(file: File, index: Int) = file.statements[index]
    .shouldBeInstanceOf<AssignStatement>()
    .binding.shouldBeInstanceOf<Expression.Identifier>()
    .binding

internal fun assignmentValueBinding(file: File) = file.statements[0]
    .shouldBeInstanceOf<AssignStatement>()
    .expression.shouldBeInstanceOf<Expression.Identifier>()
    .binding

internal fun contextualRight(file: File, index: Int) = file.statements[index]
    .shouldBeInstanceOf<ExpressionStatement>()
    .expression.shouldBeInstanceOf<Expression.BinaryOp>()
    .right

internal fun contextualReceiver(file: File, index: Int) = file.statements[index]
    .shouldBeInstanceOf<ExpressionStatement>()
    .expression.shouldBeInstanceOf<Expression.BinaryOp>()
    .left.shouldBeInstanceOf<Expression.Identifier>()

internal fun dot(left: Expression, right: Expression) = binary(left, ".", right)

internal fun binary(left: Expression, operator: String, right: Expression) = Expression.BinaryOp(
    left = left,
    operator = Token(RawToken.Punctuation(operator), line = 1, column = 1),
    right = right,
)
