package io.github.devngho.starlarkkt.ast

class UnresolvedIdentifierException(val name: String) : IllegalArgumentException(
    "Unresolved identifier: $name",
)
