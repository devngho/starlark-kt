package io.github.devngho.starlarkkt.cli

import io.github.devngho.starlarkkt.ast.Binder
import io.github.devngho.starlarkkt.ast.Lexer
import io.github.devngho.starlarkkt.ast.Parser
import io.github.devngho.starlarkkt.ast.statement.AssignStatement
import io.github.devngho.starlarkkt.ast.statement.Block
import io.github.devngho.starlarkkt.ast.statement.ExpressionStatement
import io.github.devngho.starlarkkt.ast.statement.File
import io.github.devngho.starlarkkt.ast.statement.IfStatement
import io.github.devngho.starlarkkt.ast.statement.Statement
import io.github.devngho.starlarkkt.ast.statement.StatementList
import io.github.devngho.starlarkkt.eval.ContextDeclarationsDSL.Companion.context
import io.github.devngho.starlarkkt.eval.Interpreter
import io.github.devngho.starlarkkt.eval.Value
import io.github.devngho.starlarkkt.expression.Expression
import io.github.devngho.starlarkkt.token.Tokenizer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

internal data class CliIo(
    val readSource: (String) -> Result<String>,
    val output: (String) -> Unit,
    val error: (String) -> Unit,
)

fun main(args: Array<String>) {
    val io = CliIo(
        readSource = { path -> runCatching { Files.readString(Path.of(path)) } },
        output = ::println,
        error = System.err::println,
    )
    exitProcess(runCli(args.toList(), io))
}

internal fun runCli(args: List<String>, io: CliIo): Int {
    if (args.size != 1) {
        io.error("Error: expected exactly one Starlark source file path")
        return 2
    }

    val path = args.single()
    val source = io.readSource(path).getOrElse { failure ->
        io.error("Error: failed to read '$path': ${failure.message ?: failure.javaClass.simpleName}")
        return 1
    }
    val tokens = runCatching { Tokenizer(source).tokenize().getOrThrow() }.getOrElse { failure ->
        io.error("Error: failed to tokenize '$path': ${failure.message ?: failure.javaClass.simpleName}")
        return 1
    }
    val ast = runCatching { Parser(Lexer(tokens)).parseFile().getOrThrow() }.getOrElse { failure ->
        io.error("Error: failed to parse '$path': ${failure.message ?: failure.javaClass.simpleName}")
        return 1
    }
    val (file, bindings) = runCatching { Binder(
        predeclared = setOf("assert_eq", "str", "assert_fails", "assert_", "print")
    ).bind(ast).getOrThrow() }.getOrElse { failure ->
        io.error("Error: failed to bind '$path': ${failure.message ?: failure.javaClass.simpleName}")
        return 1
    }

    io.output("AST:\n$ast\n\nBindings:\n${renderBindings(file)}")

    val run = Interpreter(file, bindings, context {
        function("print") {
            io.output(it.joinToString(" ") { it.toStringValue() })

            Value.Unit
        }

        function("str") {
            if (it.size != 1) {
                throw IllegalArgumentException("str expects exactly 1 argument, got ${it.size}")
            }
            Value.String(it.single().toStringValue())
        }

        function("assert_eq") {
            if (it.size != 2) {
                throw IllegalArgumentException("assert_eq expects exactly 2 arguments, got ${it.size}")
            }
            val (left, right) = it
            if (left != right) {
                throw AssertionError("assert_eq failed: $left != $right")
            }
            Value.Unit
        }

        function("assert_fails") {
            // todo: implement assert_fails
            Value.Unit
        }

        function("assert_") {
            if (it.size != 1) {
                throw IllegalArgumentException("assert_ expects exactly 1 argument, got ${it.size}")
            }
            val condition = it.single()
            if (!condition.toBoolean()) {
                throw AssertionError("assert_ failed: $condition is not true")
            }
            Value.Unit
        }
    }).run()

    return 0
}

private fun renderBindings(file: File): String = buildList {
    file.statements.forEach(::renderStatement)
}.joinToString("\n")

private fun MutableList<String>.renderStatement(statement: Statement) {
    when (statement) {
        is AssignStatement -> {
            renderExpression(statement.binding)
            renderExpression(statement.expression)
        }
        is ExpressionStatement -> renderExpression(statement.expression)
        is IfStatement -> {
            renderExpression(statement.condition)
            renderBlock(statement.ifThen)
            statement.ifElse?.let(::renderBlock)
        }

        is StatementList -> statement.statements.forEach(::renderStatement)
    }
}

private fun MutableList<String>.renderBlock(block: Block) {
    block.statements.forEach(::renderStatement)
}

private fun MutableList<String>.renderExpression(expression: Expression) {
    when (expression) {
        is Expression.IntLiteral,
        is Expression.DecimalLiteral,
        is Expression.StringLiteral -> Unit
        is Expression.Identifier -> add(
            "${expression.name} -> ${expression.binding?.let { "${it.scope}#${it.id}" } ?: "unbound"}",
        )
        is Expression.ListLiteral -> expression.elements.forEach(::renderExpression)
        is Expression.LambdaLiteral -> {
            expression.parameters.forEach(::renderExpression)
            renderExpression(expression.body)
        }
        is Expression.BuiltinObject -> expression.mappings.toSortedMap().values.forEach(::renderExpression)
        is Expression.BinaryOp -> {
            renderExpression(expression.left)
            renderExpression(expression.right)
        }
        is Expression.UnaryOp -> renderExpression(expression.operand)
        is Expression.ConditionalOp -> {
            renderExpression(expression.condition)
            renderExpression(expression.trueBranch)
            renderExpression(expression.falseBranch)
        }
        is Expression.SubscriptOp -> {
            renderExpression(expression.expression)
            renderExpression(expression.index)
        }
        is Expression.SliceOp -> {
            renderExpression(expression.expression)
            expression.start?.let(::renderExpression)
            expression.end?.let(::renderExpression)
            expression.stride?.let(::renderExpression)
        }
        is Expression.InvokeOp -> {
            renderExpression(expression.callee)
            expression.arguments.forEach(::renderExpression)
        }
    }
}
