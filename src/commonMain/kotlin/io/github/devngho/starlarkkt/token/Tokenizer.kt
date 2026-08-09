package io.github.devngho.starlarkkt.token

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.collections.get

class Tokenizer(
    val input: String
) {
    var position: Int = 0
        private set

    private fun nextChar(): Char? = input.getOrNull(position++)

    private fun peekChar(): Char? = input.getOrNull(position)

    private fun peekChars(n: Int): String? =
        if (position + n <= input.length) input.substring(position, position + n) else null

    private fun parseNumberLiteral(): Result<RawToken> {
        if (peekChars(2) in listOf("0o", "0O")) {
            position += 2

            val digits = buildString {
                while (peekChar() in '0'..'7') {
                    append(nextChar())
                }
            }

            if (digits.isEmpty()) {
                return Result.failure(IllegalStateException("Invalid octal literal"))
            }

            return try {
                val value = BigInteger.parseString(digits, 8)
                Result.success(RawToken.IntLiteral(value))
            } catch (e: NumberFormatException) {
                Result.failure(IllegalStateException("Invalid octal literal"))
            }
        }

        if (peekChars(2) in listOf("0x", "0X")) {
            position += 2

            val digits = buildString {
                while (peekChar() in '0'..'9' || peekChar() in 'a'..'f' || peekChar() in 'A'..'F') {
                    append(nextChar())
                }
            }

            if (digits.isEmpty()) {
                return Result.failure(IllegalStateException("Invalid hexadecimal literal"))
            }

            return try {
                val value = BigInteger.parseString(digits, 16)
                Result.success(RawToken.IntLiteral(value))
            } catch (e: NumberFormatException) {
                Result.failure(IllegalStateException("Invalid hexadecimal literal"))
            }
        }

        val intPart = buildString {
            while (peekChar() in '0'..'9') {
                append(nextChar())
            }
        }

        val maybeDot = peekChar() == '.'

        val decimalPart = if (maybeDot) {
            nextChar() // consume the dot
            buildString {
                while (peekChar() in '0'..'9') {
                    append(nextChar())
                }
            }
        } else ""

        val maybeExponent = peekChar()?.lowercaseChar() == 'e'

        val exponentPart = if (maybeExponent) {
            nextChar() // consume the 'e' or 'E'
            val sign = if (peekChar() == '+' || peekChar() == '-') nextChar() else null
            val digits = buildString {
                while (peekChar() in '0'..'9') {
                    append(nextChar())
                }
            }
            if (digits.isEmpty()) {
                return Result.failure(IllegalStateException("Invalid exponent in number literal"))
            }
            (sign?.toString() ?: "") + digits
        } else ""

        if (intPart.isNotEmpty() && intPart.startsWith("0") && !maybeDot && !maybeExponent) {
            return Result.failure(IllegalStateException("Invalid number literal with leading zero"))
        }

        if (maybeDot || maybeExponent) {
            // it's decimal

            return try {
                val intValue = if (intPart.isEmpty()) "0" else intPart
                val decimalValue = if (decimalPart.isEmpty()) "0" else decimalPart
                val exponentValue = if (exponentPart.isEmpty()) "0" else exponentPart

                val value = BigDecimal.parseString("$intValue.$decimalValue" + if (exponentPart.isNotEmpty()) "e$exponentValue" else "")

                Result.success(RawToken.DecimalLiteral(value))
            } catch (e: NumberFormatException) {
                Result.failure(IllegalStateException("Invalid decimal literal"))
            }
        } else {
            // it's integer

            return try {
                val value = BigInteger.parseString(intPart)
                Result.success(RawToken.IntLiteral(value))
            } catch (e: NumberFormatException) {
                Result.failure(IllegalStateException("Invalid integer literal"))
            }
        }
    }

    private fun parseStringLiteral(): Result<RawToken> {
        val prefix = when (peekChars(2)) {
            "r\"" -> {
                position += 2
                "r"
            }

            "b\"" -> {
                return Result.failure(IllegalStateException("Invalid string literal prefix 'b\"' (not supported yet)"))
//                position += 2
//                "b"
            }

            "rb", "br" -> {
                return Result.failure(IllegalStateException("Invalid string literal prefix 'rb' or 'br' (not supported yet)"))
//                position += 2
//                "rb"
            }

            else -> ""
        }

        // available quotes are ", ', """, '''

        val quote = when (peekChars(3)) {
            "\"\"\"" -> {
                position += 3
                "\"\"\""
            }

            "'''" -> {
                position += 3
                "'''"
            }

            else -> {
                val char = nextChar() ?: return Result.failure(IllegalStateException("Unexpected end while parsing a string literal"))
                if (char != '"' && char != '\'') {
                    return Result.failure(IllegalStateException("Unexpected character '$char' while parsing a string literal"))
                }

                char.toString()
            }
        }

        val isMultiline = quote.length == 3
        val escapes = when (prefix) {
            "r" -> emptyMap()
            else -> mapOf(
                "\\n" to "\n",
                "\\t" to "\t",
                "\\r" to "\r",
                "\\\"" to "\"",
                "\\'" to "'",
                "\\\\" to "\\"
            )
        }

        val body = buildString {
            while (true) {
                val char = nextChar() ?: return Result.failure(IllegalStateException("Unexpected end while parsing a string literal"))

                if (peekChars(2) in escapes.keys) {
                    append(escapes[peekChars(2)]!!)
                    position += 2
                    continue
                }

                if (peekChars(quote.length) == quote) {
                    position += quote.length
                    break
                }

                if (!isMultiline && char == '\n') {
                    return Result.failure(IllegalStateException("Unexpected newline in single-line string literal"))
                }

                append(char)
            }
        }

        return Result.success(RawToken.StringLiteral(body))
    }

    private fun parseIdentifierOrKeyword(): Result<RawToken> {
        val content = buildString {
            if (peekChar()?.isLetter() == true || peekChar() == '_') {
                append(nextChar())
            } else {
                return Result.failure(IllegalStateException("Unexpected character '${peekChar()}' while parsing an identifier or keyword"))
            }

            while (peekChar()?.isLetterOrDigit() == true || peekChar() == '_') {
                append(nextChar())
            }
        }

        return if (content in RawToken.Keyword.validKeywords) {
            Result.success(RawToken.Keyword(content))
        } else if (content in RawToken.Keyword.reservedKeywords) {
            Result.failure(IllegalStateException("Reserved keyword '$content' cannot be used as an identifier"))
        } else {
            Result.success(RawToken.Identifier(content))
        }
    }

    fun tokenize(): Result<List<Token<*>>> {
        val tokens = mutableListOf<Token<*>>()
        var line = 1
        var offset = 1

        while (position < input.length) {
            val char = peekChar() ?: break
            val column = position - offset + 1

            when {
                char == '\n' -> {
                    nextChar()
                    tokens.add(Token(RawToken.Whitespace(char), line, column))
                    line++
                    offset = position
                }

                char in RawToken.Whitespace.validWhitespaces -> {
                    tokens.add(Token(RawToken.Whitespace(nextChar()!!), line, column))
                }

                char.isDigit() || peekChars(2)?.let { it[0] == '.' && it[1].isDigit() } == true -> tokens.add(
                    Token(
                        parseNumberLiteral().getOrElse { return Result.failure(it) },
                        line,
                        column
                    )
                )

                peekChars(3) in RawToken.Punctuation.validPunctuationsByLength[3]!! -> {
                    tokens.add(Token(RawToken.Punctuation(peekChars(3)!!), line, column))
                    position += 3
                }

                peekChars(2) in RawToken.Punctuation.validPunctuationsByLength[2]!! -> {
                    tokens.add(Token(RawToken.Punctuation(peekChars(2)!!), line, column))
                    position += 2
                }

                char.toString() in RawToken.Punctuation.validPunctuationsByLength[1]!! -> {
                    tokens.add(Token(RawToken.Punctuation(nextChar()!!.toString()), line, column))
                }

                char == '"' || char == '\'' || peekChars(2) in listOf("r\"", "b\"", "r'", "b'") || peekChars(3) in listOf("rb\"", "br\"", "rb'", "br'") -> tokens.add(parseStringLiteral().getOrElse {
                    return Result.failure(it)
                }.let { Token(it, line, column) })

                else -> tokens.add(
                    Token(
                        parseIdentifierOrKeyword().getOrElse { return Result.failure(it) },
                        line,
                        column
                    )
                )
            }
        }

        return Result.success(tokens)
    }

    companion object {
        fun tokenize(input: String): Result<List<Token<*>>> = Tokenizer(input).tokenize()
    }
}