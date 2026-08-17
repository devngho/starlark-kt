package io.github.devngho.starlarkkt.ast

import io.github.devngho.starlarkkt.ast.statement.AssignStatement
import io.github.devngho.starlarkkt.ast.statement.Block
import io.github.devngho.starlarkkt.ast.statement.ExpressionStatement
import io.github.devngho.starlarkkt.ast.statement.File
import io.github.devngho.starlarkkt.ast.statement.IfStatement
import io.github.devngho.starlarkkt.ast.statement.Statement
import io.github.devngho.starlarkkt.expression.Binding
import io.github.devngho.starlarkkt.expression.Expression

class Binder(predeclared: Set<String> = emptySet()) {
    data class BindResult(val file: File, val predeclared: Map<String, Binding>)

    private val predeclared = predeclared.toSet()

    fun bindSimple(file: File): Result<File> {
        val budget = BindingBudget()
        val resolver = Resolver.create(file, predeclared, budget).getOrElse { return Result.failure(it) }
        return bindStatements(file.statements, resolver, budget).map(::File)
    }

    fun bind(file: File): Result<BindResult> {
        val budget = BindingBudget()
        val resolver = Resolver.create(file, predeclared, budget).getOrElse { return Result.failure(it) }
        return bindStatements(file.statements, resolver, budget).map { bound ->
            BindResult(File(bound), resolver.predeclaredBindings)
        }
    }

    private fun bindStatements(
        statements: List<Statement>,
        resolver: Resolver,
        budget: BindingBudget,
    ): Result<List<Statement>> {
        val bound = mutableListOf<Statement>()
        for (statement in statements) {
            bound += bindStatement(statement, resolver, budget).getOrElse { return Result.failure(it) }
        }
        return Result.success(bound)
    }

    private fun bindStatement(
        statement: Statement,
        resolver: Resolver,
        budget: BindingBudget,
    ): Result<Statement> = budget.visit {
        when (statement) {
            is AssignStatement -> {
                val binding = bindExpression(statement.binding, resolver, budget).getOrElse { return@visit Result.failure(it) }
                val expression = bindExpression(statement.expression, resolver, budget).getOrElse { return@visit Result.failure(it) }
                Result.success(AssignStatement(binding, expression))
            }
            is ExpressionStatement -> bindExpression(statement.expression, resolver, budget).map(::ExpressionStatement)
            is IfStatement -> {
                val condition = bindExpression(statement.condition, resolver, budget).getOrElse { return@visit Result.failure(it) }
                val ifThen = bindBlock(statement.ifThen, resolver, budget).getOrElse { return@visit Result.failure(it) }
                val ifElse = statement.ifElse?.let { block ->
                    bindBlock(block, resolver, budget).getOrElse { return@visit Result.failure(it) }
                }
                Result.success(IfStatement(condition, ifThen, ifElse))
            }

            is Block -> bindBlock(statement, resolver, budget).map { Block(it.statements) }
            is File -> bindStatements(statement.statements, resolver, budget).map(::File)
        }
    }

    private fun bindBlock(block: Block, resolver: Resolver, budget: BindingBudget): Result<Block> = budget.visit {
        bindStatements(block.statements, resolver, budget).map(::Block)
    }

    private fun <T : Expression> bindExpression(
        expression: T,
        resolver: Resolver,
        budget: BindingBudget,
    ): Result<T> = budget.visit @Suppress("UNCHECKED_CAST") {
        when (expression) {
            is Expression.IntLiteral,
            is Expression.DecimalLiteral,
            is Expression.StringLiteral -> Result.success(expression)

            is Expression.Identifier -> resolver.resolve(expression.name).map { expression.copy(binding = it) }
            is Expression.ListLiteral -> bindExpressions(expression.elements, resolver, budget).map(expression::copy)
            is Expression.LambdaLiteral -> {
                val params = bindExpressions(expression.parameters, resolver, budget).getOrElse { return@visit Result.failure(it) }
                val body = bindExpression(expression.body, resolver, budget).getOrElse { return@visit Result.failure(it) }

                Result.success(expression.copy(parameters = params, body = body))
            }

            is Expression.BuiltinObject -> bindMappings(expression.mappings, resolver, budget).map(expression::copy)
            is Expression.BinaryOp -> {
                val left = bindExpression(expression.left, resolver, budget).getOrElse { return@visit Result.failure(it) }
                val right = if (expression.operator.type.value == ".") {
                    bindAttributeContext(expression.right, resolver, budget).getOrElse { return@visit Result.failure(it) }
                } else {
                    bindExpression(expression.right, resolver, budget).getOrElse { return@visit Result.failure(it) }
                }
                Result.success(expression.copy(left = left, right = right))
            }

            is Expression.UnaryOp -> bindExpression(expression.operand, resolver, budget).map { operand ->
                expression.copy(operand = operand)
            }

            is Expression.ConditionalOp -> {
                val condition = bindExpression(expression.condition, resolver, budget).getOrElse { return@visit Result.failure(it) }
                val thenBranch = bindExpression(expression.trueBranch, resolver, budget).getOrElse { return@visit Result.failure(it) }
                val elseBranch = bindExpression(expression.falseBranch, resolver, budget).getOrElse { return@visit Result.failure(it) }
                Result.success(expression.copy(condition = condition, trueBranch = thenBranch, falseBranch = elseBranch))
            }

            is Expression.SubscriptOp -> {
                val target = bindExpression(expression.expression, resolver, budget).getOrElse { return@visit Result.failure(it) }
                bindExpression(expression.index, resolver, budget).map { index -> expression.copy(expression = target, index = index) }
            }

            is Expression.SliceOp -> {
                val target = bindExpression(expression.expression, resolver, budget).getOrElse { return@visit Result.failure(it) }
                val start = expression.start?.let { bindExpression(it, resolver, budget).getOrElse { return@visit Result.failure(it) } }
                val end = expression.end?.let { bindExpression(it, resolver, budget).getOrElse { return@visit Result.failure(it) } }
                val stride = expression.stride?.let { bindExpression(it, resolver, budget).getOrElse { return@visit Result.failure(it) } }
                Result.success(expression.copy(expression = target, start = start, end = end, stride = stride))
            }

            is Expression.InvokeOp -> {
                val callee = bindExpression(expression.callee, resolver, budget).getOrElse { return@visit Result.failure(it) }
                bindExpressions(expression.arguments, resolver, budget).map { arguments -> expression.copy(callee = callee, arguments = arguments) }
            }
        } as Result<T>
    }

    private fun <T: Expression> bindExpressions(
        expressions: List<T>,
        resolver: Resolver,
        budget: BindingBudget,
    ): Result<List<T>> {
        val bound = mutableListOf<T>()
        for (expression in expressions) {
            bound += bindExpression(expression, resolver, budget).getOrElse { return Result.failure(it) }
        }
        return Result.success(bound)
    }

    private fun bindMappings(
        mappings: Map<String, Expression>,
        resolver: Resolver,
        budget: BindingBudget,
    ): Result<Map<String, Expression>> {
        val bound = linkedMapOf<String, Expression>()
        for ((name, expression) in mappings) {
            bound[name] = bindExpression(expression, resolver, budget).getOrElse { return Result.failure(it) }
        }
        return Result.success(bound)
    }

    private fun bindAttributeContext(
        expression: Expression,
        resolver: Resolver,
        budget: BindingBudget,
    ): Result<Expression> = when (expression) {
        is Expression.Identifier -> budget.visit { Result.success(expression.copy(binding = null)) }
        is Expression.BinaryOp -> if (expression.operator.type.value == ".") {
            budget.visit {
                val left = bindAttributeContext(expression.left, resolver, budget).getOrElse { return@visit Result.failure(it) }
                val right = bindAttributeContext(expression.right, resolver, budget).getOrElse { return@visit Result.failure(it) }
                Result.success(expression.copy(left = left, right = right))
            }
        } else {
            bindExpression(expression, resolver, budget)
        }
        is Expression.ConditionalOp -> budget.visit {
            val condition = bindAttributeContext(expression.condition, resolver, budget).getOrElse { return@visit Result.failure(it) }
            val trueBranch = bindAttributeContext(expression.trueBranch, resolver, budget).getOrElse { return@visit Result.failure(it) }
            val falseBranch = bindAttributeContext(expression.falseBranch, resolver, budget).getOrElse { return@visit Result.failure(it) }
            Result.success(expression.copy(condition = condition, trueBranch = trueBranch, falseBranch = falseBranch))
        }
        is Expression.InvokeOp -> budget.visit {
            val callee = bindAttributeContext(expression.callee, resolver, budget).getOrElse { return@visit Result.failure(it) }
            bindExpressions(expression.arguments, resolver, budget).map { arguments -> expression.copy(callee = callee, arguments = arguments) }
        }
        is Expression.SubscriptOp -> budget.visit {
            val target = bindAttributeContext(expression.expression, resolver, budget).getOrElse { return@visit Result.failure(it) }
            bindExpression(expression.index, resolver, budget).map { index -> expression.copy(expression = target, index = index) }
        }
        is Expression.SliceOp -> budget.visit {
            val target = bindAttributeContext(expression.expression, resolver, budget).getOrElse { return@visit Result.failure(it) }
            val start = expression.start?.let { bindExpression(it, resolver, budget).getOrElse { return@visit Result.failure(it) } }
            val end = expression.end?.let { bindExpression(it, resolver, budget).getOrElse { return@visit Result.failure(it) } }
            val stride = expression.stride?.let { bindExpression(it, resolver, budget).getOrElse { return@visit Result.failure(it) } }
            Result.success(expression.copy(expression = target, start = start, end = end, stride = stride))
        }
        is Expression.IntLiteral,
        is Expression.DecimalLiteral,
        is Expression.StringLiteral,
        is Expression.ListLiteral,
        is Expression.LambdaLiteral,
        is Expression.BuiltinObject,
        is Expression.UnaryOp -> bindExpression(expression, resolver, budget)
    }
}
