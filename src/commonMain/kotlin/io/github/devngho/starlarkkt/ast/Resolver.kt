package io.github.devngho.starlarkkt.ast

import io.github.devngho.starlarkkt.ast.statement.AssignStatement
import io.github.devngho.starlarkkt.ast.statement.ExpressionStatement
import io.github.devngho.starlarkkt.ast.statement.File
import io.github.devngho.starlarkkt.ast.statement.IfStatement
import io.github.devngho.starlarkkt.ast.statement.Statement
import io.github.devngho.starlarkkt.ast.statement.StatementList
import io.github.devngho.starlarkkt.expression.Binding
import io.github.devngho.starlarkkt.expression.BindingId
import io.github.devngho.starlarkkt.expression.BindingScope
import io.github.devngho.starlarkkt.expression.Expression

internal class Resolver private constructor(private val predeclared: Set<String>) {
    private val globals = linkedMapOf<String, Binding>()
    val predeclaredBindings: Map<String, Binding>
        field = linkedMapOf<String, Binding>()

    fun resolve(name: String): Result<Binding> = when (name) {
        in globals -> Result.success(globals.getValue(name))
        in predeclaredBindings -> Result.success(predeclaredBindings.getValue(name))
        else -> Result.failure(UnresolvedIdentifierException(name))
    }

    private fun collectDeclarations(statements: List<Statement>, budget: BindingBudget): Result<Unit> {
        for (statement in statements) {
            collectDeclaration(statement, budget).getOrElse { return Result.failure(it) }
        }
        return Result.success(Unit)
    }

    private fun collectDeclaration(statement: Statement, budget: BindingBudget): Result<Unit> = budget.visit {
        when (statement) {
            is AssignStatement -> collectDirectIdentifier(statement.binding)
            is ExpressionStatement -> Result.success(Unit)
            is IfStatement -> {
                collectDeclarations(statement.ifThen.statements, budget).getOrElse { return@visit Result.failure(it) }
                statement.ifElse?.let { block ->
                    collectDeclarations(block.statements, budget).getOrElse { return@visit Result.failure(it) }
                }
                Result.success(Unit)
            }

            is StatementList -> collectDeclarations(statement.statements, budget)
        }
    }

    private fun collectDirectIdentifier(expression: Expression): Result<Unit> {
        if (expression is Expression.Identifier && expression.name !in globals) {
            globals[expression.name] = Binding(BindingId(globals.size), BindingScope.GLOBAL)
        }
        return Result.success(Unit)
    }

    private fun allocatePredeclared() {
        predeclared
            .asSequence()
            .filterNot(globals::containsKey)
            .sorted()
            .forEach { name ->
                predeclaredBindings[name] = Binding(
                    id = BindingId(globals.size + predeclaredBindings.size),
                    scope = BindingScope.PREDECLARED,
                )
            }
    }

    companion object {
        fun create(
            file: File,
            predeclared: Set<String>,
            budget: BindingBudget = BindingBudget(),
        ): Result<Resolver> {
            val resolver = Resolver(predeclared.toSet())
            return resolver.collectDeclarations(file.statements, budget).map {
                resolver.allocatePredeclared()
                resolver
            }
        }
    }
}
