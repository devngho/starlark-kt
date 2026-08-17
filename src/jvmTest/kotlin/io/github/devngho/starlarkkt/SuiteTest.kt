package io.github.devngho.starlarkkt

import io.github.devngho.starlarkkt.ast.Binder
import io.github.devngho.starlarkkt.ast.Lexer
import io.github.devngho.starlarkkt.ast.Parser
import io.github.devngho.starlarkkt.builtin.typeNames
import io.github.devngho.starlarkkt.builtin.types
import io.github.devngho.starlarkkt.eval.ContextDeclarationsDSL.Companion.context
import io.github.devngho.starlarkkt.eval.Interpreter
import io.github.devngho.starlarkkt.eval.Value
import io.github.devngho.starlarkkt.token.Tokenizer
import io.kotest.core.spec.style.FunSpec
import java.io.File

class SuiteTest: FunSpec({
    // every file on resources folder is a test case, the file name is the test name, and the content is the test code
    // so walk through the resources folder and run each file as a test case
    val resources = this::class.java.classLoader.getResource("testcases")?.file ?: throw IllegalStateException("testcases folder not found")
    val testcases = File(resources).walkTopDown().filter { it.isFile }.toList()

    testcases.forEach { file ->
        val testName = file.nameWithoutExtension
        val testCode = file.readText()
        // split by ---
        val testCases = testCode.split("---").map { it.trim() }.filter { it.isNotEmpty() }

        testCases.forEachIndexed { index, testCase ->
            test("$testName - case ${index + 1}") {
                val tokens = Tokenizer(testCase).tokenize().getOrThrow()
                val ast = Parser(Lexer(tokens)).parseFile().getOrThrow()
                val (file, bindings) = run {
                    Binder(
                        predeclared = setOf("assert_eq", "str", "assert_fails", "assert_", "print", "False", "True") + typeNames
                    ).bind(ast).getOrThrow()
                }

                val run = Interpreter(file, bindings, context {
                    function("print") {
                        println(it.joinToString(" ") { it.toStringValue() })

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

                        if (left is Value.BuiltinObject && right is Value.BuiltinObject) {
                            if (left.type.name != right.type.name) {
                                throw AssertionError("assert_eq failed: $left and $right are of different types")
                            }

                            left.type.methods["__eq__"]?.implementation(listOf(left, right))?.let { result ->
                                if (result is Value.False) {
                                    throw AssertionError("assert_eq failed: $left != $right")
                                }
                            } ?: throw IllegalArgumentException("assert_eq failed: __eq__ method not found for type ${left.type.name}")
                        } else if (left != right) {
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

                    context(types)
                }).run()
            }
        }
    }
})