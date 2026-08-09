# starlark-kt

// create an alert quote
> [!CAUTION]
> This project is WIP and provided as-is.

A Kotlin implementation of the Starlark-like language for embedding in KMP projects.

This project aims to be:
- extensible and embeddable
  - able to edit its behavior from adding builtins to add new keywords
- compatible with KMP projects

By contrast, this project is not intended to be:
- performant (maybe later)
- match the original Starlark spec exactly
  - for example, this project omits octal escapes and Unicode escapes in strings and allows fixed-number of arguments in function calls (no *args or **kwargs)