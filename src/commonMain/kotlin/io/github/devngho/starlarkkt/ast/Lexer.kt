package io.github.devngho.starlarkkt.ast

import io.github.devngho.starlarkkt.token.RawToken
import io.github.devngho.starlarkkt.token.Token

class Lexer(val tokens: List<Token<*>>) {
    var position = 0
        private set

    fun next(ignoreWhitespace: Boolean = true, ignoreComment: Boolean = true): Token<*>? {
        val token = tokens.getOrNull(position++)
        return if (ignoreWhitespace && token?.type is RawToken.Whitespace || ignoreComment && token?.type is RawToken.Comment) next(ignoreWhitespace, ignoreComment) else token
    }

    fun peek(ignoreWhitespace: Boolean = true, ignoreComment: Boolean = true): Token<*>? {
        val token = tokens.getOrNull(position)
        return if (ignoreWhitespace && token?.type is RawToken.Whitespace || ignoreComment && token?.type is RawToken.Comment) {
            position++
            peek(ignoreWhitespace, ignoreComment)
        } else {
            token
        }
    }

    fun peeks(count: Int, ignoreWhitespace: Boolean = true, ignoreComment: Boolean = true): List<Token<*>> {
        val tokens = mutableListOf<Token<*>>()
        for (i in 0 until count) {
            val token = peek(ignoreWhitespace, ignoreComment)
            if (token != null) {
                tokens.add(token)
            } else {
                break
            }
        }
        return tokens
    }
}