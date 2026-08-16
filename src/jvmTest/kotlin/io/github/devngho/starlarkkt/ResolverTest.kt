package io.github.devngho.starlarkkt

import io.github.devngho.starlarkkt.ast.Binder
import io.github.devngho.starlarkkt.ast.BindingBudget
import io.github.devngho.starlarkkt.ast.BindingLimitExceededException
import io.github.devngho.starlarkkt.ast.UnresolvedIdentifierException
import io.github.devngho.starlarkkt.ast.Resolver
import io.github.devngho.starlarkkt.ast.statement.Block
import io.github.devngho.starlarkkt.ast.statement.ExpressionStatement
import io.github.devngho.starlarkkt.ast.statement.File
import io.github.devngho.starlarkkt.ast.statement.IfStatement
import io.github.devngho.starlarkkt.ast.statement.Statement
import io.github.devngho.starlarkkt.expression.Binding
import io.github.devngho.starlarkkt.expression.BindingId
import io.github.devngho.starlarkkt.expression.BindingScope
import io.github.devngho.starlarkkt.expression.Expression
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ResolverTest : FunSpec({
    test("exposes structural bindings when constructed") {
        // Given
        val global = Binding(id = BindingId(0), scope = BindingScope.GLOBAL)
        val predeclared = Binding(id = BindingId(1), scope = BindingScope.PREDECLARED)

        // When
        val error = UnresolvedIdentifierException("missing")

        // Then
        global shouldBe Binding(id = BindingId(0), scope = BindingScope.GLOBAL)
        global shouldBe Binding(id = BindingId(0), scope = BindingScope.GLOBAL)
        (global == Binding(id = BindingId(1), scope = BindingScope.GLOBAL)) shouldBe false
        (global == predeclared) shouldBe false
        BindingScope.entries.toSet() shouldBe setOf(
            BindingScope.GLOBAL,
            BindingScope.PREDECLARED,
            BindingScope.LOCAL,
            BindingScope.FREE,
            BindingScope.CELL,
        )
        error.name shouldBe "missing"
        error.message shouldBe "Unresolved identifier: missing"
    }

    test("resolves declarations across forward references and if branches") {
        // Given
        val file = parseFile(
            """
            x
            if condition:
                x = x
            else:
                y = x
            y
            """.trimIndent(),
        )

        // When
        val resolver = Resolver.create(file, emptySet()).getOrThrow()

        // Then
        resolver.resolve("x").getOrThrow() shouldBe Binding(BindingId(0), BindingScope.GLOBAL)
        resolver.resolve("y").getOrThrow() shouldBe Binding(BindingId(1), BindingScope.GLOBAL)
    }

    test("allocates globals before sorted unshadowed predeclared names") {
        // Given
        val file = parseFile(
            """
            y = 1
            x = y
            y = x
            """.trimIndent(),
        )

        // When
        val first = Resolver.create(file, linkedSetOf("z", "x", "a")).getOrThrow()
        val second = Resolver.create(file, linkedSetOf("a", "z", "x")).getOrThrow()

        // Then
        first.resolve("y").getOrThrow() shouldBe Binding(BindingId(0), BindingScope.GLOBAL)
        first.resolve("x").getOrThrow() shouldBe Binding(BindingId(1), BindingScope.GLOBAL)
        first.resolve("a").getOrThrow() shouldBe Binding(BindingId(2), BindingScope.PREDECLARED)
        first.resolve("z").getOrThrow() shouldBe Binding(BindingId(3), BindingScope.PREDECLARED)
        second.resolve("a").getOrThrow() shouldBe first.resolve("a").getOrThrow()
        second.resolve("z").getOrThrow() shouldBe first.resolve("z").getOrThrow()
    }

    test("collects only direct identifier assignment targets") {
        // Given
        val file = parseFile(
            """
            obj.attr = 1
            items[index] = 1
            slices[start:end:stride] = 1
            """.trimIndent(),
        )

        // When
        val resolver = Resolver.create(file, emptySet()).getOrThrow()

        // Then
        listOf("obj", "items", "index", "slices", "start", "end", "stride").forEach { name ->
            resolver.resolve(name).exceptionOrNull()
                .shouldBeInstanceOf<UnresolvedIdentifierException>()
                .name shouldBe name
        }
    }

    test("returns a typed failure for an unresolved identifier") {
        // Given
        val resolver = Resolver.create(parseFile("x = 1"), emptySet()).getOrThrow()

        // When
        val failure = resolver.resolve("missing").exceptionOrNull()

        // Then
        failure.shouldBeInstanceOf<UnresolvedIdentifierException>().name shouldBe "missing"
    }

    test("returns a typed limit failure while collecting deep statement scopes") {
        // Given
        val file = deeplyNestedIfFile(256)

        // When
        val failure = Resolver.create(file, emptySet()).exceptionOrNull()

        // Then
        failure.shouldBeInstanceOf<BindingLimitExceededException>()
    }

    test("returns a typed limit failure for a parser-generated deep binary chain") {
        // Given
        val file = parseFile("target = " + List(256) { "value" }.joinToString(" + "))

        // When
        val failure = Binder(setOf("value")).bindSimple(file).exceptionOrNull()

        // Then
        failure.shouldBeInstanceOf<BindingLimitExceededException>()
    }

    test("shares the total visit budget across resolution and binding") {
        // Given
        val file = File(
            List(3_000) {
                ExpressionStatement(Expression.StringLiteral("literal"))
            },
        )

        // When
        val failure = Binder().bindSimple(file).exceptionOrNull()

        // Then
        failure.shouldBeInstanceOf<BindingLimitExceededException>()
    }

    test("accepts 128 nested budget visits and rejects the 129th") {
        // Given
        val budget = BindingBudget()

        // When
        val accepted = nestedVisits(budget, 128)
        val rejected = nestedVisits(budget, 129)

        // Then
        accepted.getOrThrow() shouldBe Unit
        rejected.exceptionOrNull().shouldBeInstanceOf<BindingLimitExceededException>()
    }

    test("accepts 8192 sequential budget visits and rejects the 8193rd") {
        // Given
        val budget = BindingBudget()

        // When
        val accepted = List(8_192) { budget.visit { Result.success(Unit) } }
        val rejected = budget.visit { Result.success(Unit) }

        // Then
        accepted.forEach { it.getOrThrow() shouldBe Unit }
        rejected.exceptionOrNull().shouldBeInstanceOf<BindingLimitExceededException>()
    }
})

private fun deeplyNestedIfFile(depth: Int): File {
    var statement: Statement = ExpressionStatement(Expression.StringLiteral("end"))
    repeat(depth) {
        statement = IfStatement(
            condition = Expression.StringLiteral("condition"),
            ifThen = Block(listOf(statement)),
        )
    }
    return File(listOf(statement))
}

private fun nestedVisits(budget: BindingBudget, remaining: Int): Result<Unit> = budget.visit {
    if (remaining == 1) Result.success(Unit) else nestedVisits(budget, remaining - 1)
}
