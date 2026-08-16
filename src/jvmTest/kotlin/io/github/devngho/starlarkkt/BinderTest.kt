package io.github.devngho.starlarkkt

import io.github.devngho.starlarkkt.ast.Binder
import io.github.devngho.starlarkkt.ast.UnresolvedIdentifierException
import io.github.devngho.starlarkkt.ast.statement.AssignStatement
import io.github.devngho.starlarkkt.ast.statement.ExpressionStatement
import io.github.devngho.starlarkkt.ast.statement.File
import io.github.devngho.starlarkkt.ast.statement.IfStatement
import io.github.devngho.starlarkkt.expression.Binding
import io.github.devngho.starlarkkt.expression.BindingId
import io.github.devngho.starlarkkt.expression.BindingScope
import io.github.devngho.starlarkkt.expression.Expression
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class BinderTest : FunSpec({
    test("binds parsed source immutably with globals and predeclared names") {
        // Given
        val parsed = parseFile(
            """
            x = y
            if x:
                print([x, y[0:1]])
            """.trimIndent(),
        )

        // When
        val bound = Binder(setOf("y", "print")).bindSimple(parsed).getOrThrow()

        // Then
        assignmentTargetBinding(parsed, 0) shouldBe null
        assignmentValueBinding(parsed) shouldBe null
        assignmentTargetBinding(bound, 0) shouldBe Binding(BindingId(0), BindingScope.GLOBAL)
        assignmentValueBinding(bound) shouldBe Binding(BindingId(2), BindingScope.PREDECLARED)
        boundIfCondition(bound) shouldBe Binding(BindingId(0), BindingScope.GLOBAL)
        boundPrintCallee(bound) shouldBe Binding(BindingId(1), BindingScope.PREDECLARED)
        boundListElements(bound) shouldBe listOf(
            Binding(BindingId(0), BindingScope.GLOBAL),
            Binding(BindingId(2), BindingScope.PREDECLARED),
        )
    }

    test("binds direct contextual call subscript and slice variables without binding selectors") {
        // Given
        val parsed = File(
            listOf(
                ExpressionStatement(
                    dot(
                        Expression.Identifier("receiver", null),
                        Expression.InvokeOp(
                            dot(Expression.Identifier("member", null), Expression.Identifier("call", null)),
                            listOf(Expression.Identifier("argument", null)),
                        ),
                    ),
                ),
                ExpressionStatement(
                    dot(
                        Expression.Identifier("receiver", null),
                        Expression.SubscriptOp(
                            Expression.Identifier("selector", null),
                            Expression.Identifier("index", null),
                        ),
                    ),
                ),
                ExpressionStatement(
                    dot(
                        Expression.Identifier("receiver", null),
                        Expression.SliceOp(
                            Expression.Identifier("selector", null),
                            Expression.Identifier("start", null),
                            Expression.Identifier("end", null),
                            Expression.Identifier("stride", null),
                        ),
                    ),
                ),
            ),
        )

        // When
        val bound = Binder(setOf("argument", "end", "index", "receiver", "start", "stride")).bindSimple(parsed).getOrThrow()

        // Then
        val call = contextualRight(bound, 0).shouldBeInstanceOf<Expression.InvokeOp>()
        contextualReceiver(bound, 0).binding shouldBe Binding(BindingId(3), BindingScope.PREDECLARED)
        call.callee.shouldBeInstanceOf<Expression.BinaryOp>().let { selector ->
            selector.left.shouldBeInstanceOf<Expression.Identifier>().binding shouldBe null
            selector.right.shouldBeInstanceOf<Expression.Identifier>().binding shouldBe null
        }
        call.arguments.single().shouldBeInstanceOf<Expression.Identifier>().binding shouldBe Binding(BindingId(0), BindingScope.PREDECLARED)

        val subscript = contextualRight(bound, 1).shouldBeInstanceOf<Expression.SubscriptOp>()
        contextualReceiver(bound, 1).binding shouldBe Binding(BindingId(3), BindingScope.PREDECLARED)
        subscript.expression.shouldBeInstanceOf<Expression.Identifier>().binding shouldBe null
        subscript.index.shouldBeInstanceOf<Expression.Identifier>().binding shouldBe Binding(BindingId(2), BindingScope.PREDECLARED)

        val slice = contextualRight(bound, 2).shouldBeInstanceOf<Expression.SliceOp>()
        contextualReceiver(bound, 2).binding shouldBe Binding(BindingId(3), BindingScope.PREDECLARED)
        slice.expression.shouldBeInstanceOf<Expression.Identifier>().binding shouldBe null
        slice.start.shouldBeInstanceOf<Expression.Identifier>().binding shouldBe Binding(BindingId(4), BindingScope.PREDECLARED)
        slice.end.shouldBeInstanceOf<Expression.Identifier>().binding shouldBe Binding(BindingId(1), BindingScope.PREDECLARED)
        slice.stride.shouldBeInstanceOf<Expression.Identifier>().binding shouldBe Binding(BindingId(5), BindingScope.PREDECLARED)
    }

    test("resolves non-dot binary expressions under attribute context") {
        // Given
        val parsed = File(
            listOf(
                ExpressionStatement(
                    dot(
                        Expression.Identifier("receiver", null),
                        binary(
                            Expression.Identifier("left", null),
                            "+",
                            Expression.Identifier("right", null),
                        ),
                    ),
                ),
            ),
        )

        // When
        val bound = Binder(setOf("receiver", "left", "right")).bindSimple(parsed).getOrThrow()

        // Then
        val binary = contextualRight(bound, 0).shouldBeInstanceOf<Expression.BinaryOp>()
        contextualReceiver(bound, 0).binding shouldBe Binding(BindingId(1), BindingScope.PREDECLARED)
        binary.left.shouldBeInstanceOf<Expression.Identifier>().binding shouldBe Binding(BindingId(0), BindingScope.PREDECLARED)
        binary.right.shouldBeInstanceOf<Expression.Identifier>().binding shouldBe Binding(BindingId(2), BindingScope.PREDECLARED)
    }

    test("reports unresolved variables under attribute context instead of selector names") {
        // Given
        val parsed = File(
            listOf(
                ExpressionStatement(
                    dot(
                        Expression.Identifier("receiver", null),
                        Expression.InvokeOp(
                            Expression.Identifier("selector", null),
                            listOf(Expression.Identifier("missing", null)),
                        ),
                    ),
                ),
            ),
        )

        // When
        val failure = Binder(setOf("receiver")).bindSimple(parsed).exceptionOrNull()

        // Then
        failure.shouldBeInstanceOf<UnresolvedIdentifierException>().name shouldBe "missing"
    }

    test("snapshots predeclared names at construction") {
        // Given
        val names = mutableSetOf("original")
        val binder = Binder(names)
        names.clear()

        // When
        val bound = binder.bindSimple(File(listOf(ExpressionStatement(Expression.Identifier("original", null)))))

        // Then
        bound.getOrThrow().statements.single()
            .shouldBeInstanceOf<ExpressionStatement>()
            .expression.shouldBeInstanceOf<Expression.Identifier>()
            .binding shouldBe Binding(BindingId(0), BindingScope.PREDECLARED)
    }

    test("recursively binds unary binary and list expression children") {
        // Given
        val parsed = parseFile("x = -value + [value]")

        // When
        val bound = Binder(setOf("value")).bindSimple(parsed).getOrThrow()

        // Then
        val binary = bound.statements.single()
            .shouldBeInstanceOf<AssignStatement>()
            .expression.shouldBeInstanceOf<Expression.BinaryOp>()
        binary.left.shouldBeInstanceOf<Expression.UnaryOp>()
            .operand.shouldBeInstanceOf<Expression.Identifier>()
            .binding shouldBe Binding(BindingId(1), BindingScope.PREDECLARED)
        binary.right.shouldBeInstanceOf<Expression.ListLiteral>()
            .elements.single().shouldBeInstanceOf<Expression.Identifier>()
            .binding shouldBe Binding(BindingId(1), BindingScope.PREDECLARED)
    }

    test("recursively binds builtin object values") {
        // Given
        val parsed = File(
            listOf(
                ExpressionStatement(
                    Expression.BuiltinObject(mapOf("field" to Expression.Identifier("value", null))),
                ),
            ),
        )

        // When
        val bound = Binder(setOf("value")).bindSimple(parsed).getOrThrow()

        // Then
        bound.statements.single()
            .shouldBeInstanceOf<ExpressionStatement>()
            .expression.shouldBeInstanceOf<Expression.BuiltinObject>()
            .mappings.getValue("field").shouldBeInstanceOf<Expression.Identifier>()
            .binding shouldBe Binding(BindingId(0), BindingScope.PREDECLARED)
        parsed.statements.single()
            .shouldBeInstanceOf<ExpressionStatement>()
            .expression.shouldBeInstanceOf<Expression.BuiltinObject>()
            .mappings.getValue("field").shouldBeInstanceOf<Expression.Identifier>()
            .binding shouldBe null
    }

    test("reports the first unresolved variable while skipping a nearby dot selector") {
        // Given
        val parsed = File(
            listOf(
                ExpressionStatement(
                    Expression.InvokeOp(
                        dot(
                            Expression.Identifier("receiver", null),
                            Expression.Identifier("selector", null),
                        ),
                        listOf(
                            Expression.Identifier("firstMissing", null),
                            Expression.Identifier("secondMissing", null),
                        ),
                    ),
                ),
            ),
        )

        // When
        val failure = Binder(setOf("receiver")).bindSimple(parsed).exceptionOrNull()

        // Then
        failure.shouldBeInstanceOf<UnresolvedIdentifierException>().name shouldBe "firstMissing"
    }

    test("produces equal output for fresh bind operations and empty input") {
        // Given
        val parsed = parseFile("x = y")

        // When
        val first = Binder(setOf("y")).bindSimple(parsed).getOrThrow()
        val second = Binder(setOf("y")).bindSimple(parsed).getOrThrow()
        val empty = Binder().bindSimple(File(emptyList())).getOrThrow()

        // Then
        first shouldBe second
        empty shouldBe File(emptyList())
    }
})

private fun boundIfCondition(file: File) = file.statements[1]
    .shouldBeInstanceOf<IfStatement>()
    .condition.shouldBeInstanceOf<Expression.Identifier>()
    .binding

private fun boundPrintCallee(file: File) = file.statements[1]
    .shouldBeInstanceOf<IfStatement>()
    .ifThen.statements.single()
    .shouldBeInstanceOf<ExpressionStatement>()
    .expression.shouldBeInstanceOf<Expression.InvokeOp>()
    .callee.shouldBeInstanceOf<Expression.Identifier>()
    .binding

private fun boundListElements(file: File): List<Binding?> {
    val list = file.statements[1]
        .shouldBeInstanceOf<IfStatement>()
        .ifThen.statements.single()
        .shouldBeInstanceOf<ExpressionStatement>()
        .expression.shouldBeInstanceOf<Expression.InvokeOp>()
        .arguments.single()
        .shouldBeInstanceOf<Expression.ListLiteral>()
    val first = list.elements[0].shouldBeInstanceOf<Expression.Identifier>().binding
    val second = list.elements[1]
        .shouldBeInstanceOf<Expression.SliceOp>()
        .expression.shouldBeInstanceOf<Expression.Identifier>()
        .binding
    return listOf(first, second)
}
