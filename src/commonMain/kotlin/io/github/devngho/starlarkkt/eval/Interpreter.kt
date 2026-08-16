package io.github.devngho.starlarkkt.eval

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.github.devngho.starlarkkt.ast.statement.AssignStatement
import io.github.devngho.starlarkkt.ast.statement.Block
import io.github.devngho.starlarkkt.ast.statement.ExpressionStatement
import io.github.devngho.starlarkkt.ast.statement.File
import io.github.devngho.starlarkkt.ast.statement.IfStatement
import io.github.devngho.starlarkkt.ast.statement.Statement
import io.github.devngho.starlarkkt.expression.Binding
import io.github.devngho.starlarkkt.expression.Expression

class Interpreter(val file: File, val predeclarations: Map<String, Binding>, val globalContext: ContextDeclarations) {
    fun run() {
        val context = Context(globalContext.mapKeys {
            predeclarations[it.key]?.id ?: throw IllegalArgumentException("No value found for identifier: ${it.key}. Is this defined also in the resolver?")
        }.toMutableMap(), null)

        for (statement in file.statements) {
            evaluateStatement(statement, context)
        }
    }

    fun evaluateStatement(statement: Statement, context: Context) {
        when (statement) {
            is ExpressionStatement -> evaluateExpression(statement.expression, context)
            is Block -> {
                val blockContext = Context(mutableMapOf(), context)
                for (stmt in statement.statements) {
                    evaluateStatement(stmt, blockContext)
                }
            }
            is IfStatement -> {
                val conditionValue = evaluateExpression(statement.condition, context)
                if (conditionValue.toBoolean()) {
                    evaluateStatement(statement.ifThen, context)
                } else {
                    statement.ifElse?.let { evaluateStatement(it, context) } ?: Value.Unit
                }
            }

            is AssignStatement -> {
                val value = evaluateExpression(statement.expression, context)

                if (statement.binding is Expression.Identifier) {
                    val bindingId = statement.binding.binding!!.id
                    context.declare(bindingId, value)
                } else {
                    throw IllegalArgumentException("Assignment to non-identifier is not supported")
                }
            }
            is File -> {
                val fileContext = Context(mutableMapOf(), context)
                for (stmt in statement.statements) {
                    evaluateStatement(stmt, fileContext)
                }
            }
        }
    }

    fun evaluateExpression(expression: Expression, context: Context): Value = when (expression) {
        is Expression.IntLiteral -> Value.Int(expression.value)
        is Expression.DecimalLiteral -> Value.Decimal(expression.value)
        is Expression.StringLiteral -> Value.String(expression.value)
        is Expression.LambdaLiteral -> Value.Runnable { args ->
            val lambdaContext = Context(mutableMapOf(), context)
            expression.parameters.forEachIndexed { index, param ->
                val bindingId = param.binding?.id
                    ?: throw IllegalArgumentException("Unbound identifier in lambda parameter: ${param.name}")
                lambdaContext.declare(bindingId, args.getOrElse(index) {
                    throw IllegalArgumentException("Not enough arguments provided for lambda")
                })
            }
            evaluateExpression(expression.body, lambdaContext)
        }
        is Expression.Identifier -> {
            val bindingId = expression.binding?.id
                ?: throw IllegalArgumentException("Unbound identifier: ${expression.name}")
            context.getDeclaration(bindingId) ?: throw IllegalArgumentException("No value found for identifier: ${expression.name}")
        }
        is Expression.InvokeOp -> {
            val calleeValue = evaluateExpression(expression.callee, context)
            val argumentValues = expression.arguments.map { evaluateExpression(it, context) }

            if (calleeValue is Value.Runnable) {
                calleeValue.implementation(argumentValues)
            } else {
                throw IllegalArgumentException("Callee is not a function: $calleeValue")
            }
        }
        is Expression.BinaryOp -> {
            val leftValue = evaluateExpression(expression.left, context)
            val rightValue = evaluateExpression(expression.right, context)

            when (expression.operator.type.value) {
                "+" -> {
                    when (leftValue) {
                        is Value.Int if rightValue is Value.Int -> Value.Int(leftValue.value + rightValue.value)
                        is Value.Decimal if rightValue is Value.Decimal -> Value.Decimal(leftValue.value + rightValue.value)
                        is Value.String if rightValue is Value.String -> Value.String(leftValue.value + rightValue.value)
                        else -> throw IllegalArgumentException("Unsupported operand types for +: ${leftValue::class.simpleName} and ${rightValue::class.simpleName}")
                    }
                }

                "-" -> {
                    when (leftValue) {
                        is Value.Int if rightValue is Value.Int -> Value.Int(leftValue.value - rightValue.value)
                        is Value.Decimal if rightValue is Value.Decimal -> Value.Decimal(leftValue.value - rightValue.value)
                        else -> throw IllegalArgumentException("Unsupported operand types for -: ${leftValue::class.simpleName} and ${rightValue::class.simpleName}")
                    }
                }

                "*" -> {
                    when (leftValue) {
                        is Value.Int if rightValue is Value.Int -> Value.Int(leftValue.value * rightValue.value)
                        is Value.Decimal if rightValue is Value.Decimal -> Value.Decimal(leftValue.value * rightValue.value)
                        else -> throw IllegalArgumentException("Unsupported operand types for *: ${leftValue::class.simpleName} and ${rightValue::class.simpleName}")
                    }
                }

                "/" -> {
                    when (leftValue) {
                        is Value.Int if rightValue is Value.Int -> Value.Decimal(BigDecimal.fromBigInteger(leftValue.value) / (BigDecimal.fromBigInteger(rightValue.value)))
                        is Value.Decimal if rightValue is Value.Decimal -> Value.Decimal(leftValue.value / rightValue.value)
                        else -> throw IllegalArgumentException("Unsupported operand types for /: ${leftValue::class.simpleName} and ${rightValue::class.simpleName}")
                    }
                }

                "//" -> {
                    when (leftValue) {
                        is Value.Int if rightValue is Value.Int -> Value.Int(leftValue.value / rightValue.value)
                        else -> throw IllegalArgumentException("Unsupported operand types for //: ${leftValue::class.simpleName} and ${rightValue::class.simpleName}")
                    }
                }

                "and" -> {
                    if (!leftValue.toBoolean()) {
                        leftValue
                    } else {
                        rightValue
                    }
                }

                "or" -> {
                    if (leftValue.toBoolean()) {
                        leftValue
                    } else {
                        rightValue
                    }
                }

                "<<" -> {
                    when (leftValue) {
                        is Value.Int if rightValue is Value.Int -> Value.Int(leftValue.value shl rightValue.value.intValue(true))
                        else -> throw IllegalArgumentException("Unsupported operand types for <<: ${leftValue::class.simpleName} and ${rightValue::class.simpleName}")
                    }
                }

                ">>" -> {
                    when (leftValue) {
                        is Value.Int if rightValue is Value.Int -> Value.Int(leftValue.value shr rightValue.value.intValue(true))
                        else -> throw IllegalArgumentException("Unsupported operand types for >>: ${leftValue::class.simpleName} and ${rightValue::class.simpleName}")
                    }
                }

                ">" -> {
                    when (leftValue) {
                        is Value.Int if rightValue is Value.Int -> if (leftValue.value > rightValue.value) Value.True else Value.False
                        is Value.Decimal if rightValue is Value.Decimal -> if (leftValue.value > rightValue.value) Value.True else Value.False
                        else -> throw IllegalArgumentException("Unsupported operand types for >: ${leftValue::class.simpleName} and ${rightValue::class.simpleName}")
                    }
                }

                "<" -> {
                    when (leftValue) {
                        is Value.Int if rightValue is Value.Int -> if (leftValue.value < rightValue.value) Value.True else Value.False
                        is Value.Decimal if rightValue is Value.Decimal -> if (leftValue.value < rightValue.value) Value.True else Value.False
                        else -> throw IllegalArgumentException("Unsupported operand types for <: ${leftValue::class.simpleName} and ${rightValue::class.simpleName}")
                    }
                }

                "==" -> {
                    if (leftValue == rightValue) Value.True else Value.False
                }


                "%" -> {
                    when (leftValue) {
                        is Value.Int if rightValue is Value.Int -> Value.Int(leftValue.value % rightValue.value)
                        is Value.String -> {
                            // todo: correct string formatting implementation

                            val args: List<Value> = when (rightValue) {
                                is Value.Int -> listOf(rightValue)
                                is Value.String -> listOf(rightValue)
                                else -> throw IllegalArgumentException("Unsupported operand type for % with String: ${rightValue::class.simpleName}")
                            }

                            Value.String(buildString {
                                var argIndex = 0
                                var i = 0
                                while (i < leftValue.value.length) {
                                    if (leftValue.value[i] == '%' && i + 1 < leftValue.value.length && leftValue.value[i + 1] == 's') {
                                        if (argIndex >= args.size) {
                                            throw IllegalArgumentException("Not enough arguments for string formatting")
                                        }
                                        append(args[argIndex])
                                        argIndex++
                                        i += 2 // skip %s
                                    } else if (leftValue.value[i] == '%' && i + 1 < leftValue.value.length && leftValue.value[i + 1] == '%') {
                                        append('%')
                                        i += 2 // skip %%
                                    } else if (leftValue.value[i] == '%' && i + 1 < leftValue.value.length && leftValue.value[i + 1] == 'd') {
                                        if (argIndex >= args.size) {
                                            throw IllegalArgumentException("Not enough arguments for string formatting")
                                        }
                                        append(args[argIndex].let {
                                            if (it is Value.Int) it.value.toString()
                                            else throw IllegalArgumentException("Expected Int for %d formatting, got ${it::class.simpleName}")
                                        })
                                        argIndex++
                                        i += 2 // skip %d
                                    } else if (leftValue.value[i] == '%' && i + 1 < leftValue.value.length && leftValue.value[i + 1] == 'f') {
                                        if (argIndex >= args.size) {
                                            throw IllegalArgumentException("Not enough arguments for string formatting")
                                        }
                                        append(args[argIndex].let {
                                            if (it is Value.Decimal) it.value.toString()
                                            else throw IllegalArgumentException("Expected Decimal for %f formatting, got ${it::class.simpleName}")
                                        })
                                        argIndex++
                                        i += 2 // skip %f
                                    } else if (leftValue.value[i] == '%' && i + 1 < leftValue.value.length && leftValue.value[i + 1] == 'x') {
                                        if (argIndex >= args.size) {
                                            throw IllegalArgumentException("Not enough arguments for string formatting")
                                        }
                                        append(args[argIndex].let {
                                            if (it is Value.Int) it.value.toString(16)
                                            else throw IllegalArgumentException("Expected Int for %x formatting, got ${it::class.simpleName}")
                                        })
                                        argIndex++
                                        i += 2 // skip %x
                                    } else if (leftValue.value[i] == '%' && i + 1 < leftValue.value.length && leftValue.value[i + 1] == 'X') {
                                        if (argIndex >= args.size) {
                                            throw IllegalArgumentException("Not enough arguments for string formatting")
                                        }
                                        append(args[argIndex].let {
                                            if (it is Value.Int) it.value.toString(16).uppercase()
                                            else throw IllegalArgumentException("Expected Int for %X formatting, got ${it::class.simpleName}")
                                        })
                                        argIndex++
                                        i += 2 // skip %X
                                    } else if (leftValue.value[i] == '%' && i + 1 < leftValue.value.length && leftValue.value[i + 1] == 'o') {
                                        if (argIndex >= args.size) {
                                            throw IllegalArgumentException("Not enough arguments for string formatting")
                                        }
                                        append(args[argIndex].let {
                                            if (it is Value.Int) it.value.toString(8)
                                            else throw IllegalArgumentException("Expected Int for %o formatting, got ${it::class.simpleName}")
                                        })
                                        argIndex++
                                        i += 2 // skip %o
                                    } else {
                                        append(leftValue.value[i])
                                        i++
                                    }
                                }
                            })
                        }
                        else -> throw IllegalArgumentException("Unsupported operand types for %: ${leftValue::class.simpleName} and ${rightValue::class.simpleName}")
                    }
                }

                else -> throw IllegalArgumentException("Unsupported binary operator: ${expression.operator.type.value}")
            }
        }
        is Expression.UnaryOp -> {
            val operandValue = evaluateExpression(expression.operand, context)

            when (expression.operator.type.value) {
                "+" -> {
                    when (operandValue) {
                        is Value.Int -> operandValue
                        is Value.Decimal -> operandValue
                        else -> throw IllegalArgumentException("Unsupported operand type for unary +: ${operandValue::class.simpleName}")
                    }
                }

                "-" -> {
                    when (operandValue) {
                        is Value.Int -> Value.Int(-operandValue.value)
                        is Value.Decimal -> Value.Decimal(-operandValue.value)
                        else -> throw IllegalArgumentException("Unsupported operand type for unary -: ${operandValue::class.simpleName}")
                    }
                }

                "not" -> Value.Int(if (!operandValue.toBoolean()) BigDecimal.ONE.toBigInteger() else BigDecimal.ZERO.toBigInteger())

                else -> throw IllegalArgumentException("Unsupported unary operator: ${expression.operator.type.value}")
            }
        }

        else -> throw IllegalArgumentException("Unsupported expression type: ${expression::class.simpleName}")
    }
}