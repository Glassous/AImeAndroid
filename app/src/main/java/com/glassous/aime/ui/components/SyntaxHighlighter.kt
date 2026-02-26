package com.glassous.aime.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

object SyntaxHighlighter {

    private val KEYWORDS = setOf(
        "abstract", "actual", "annotation", "as", "break", "by", "catch", "class", "companion",
        "const", "constructor", "continue", "crossinline", "data", "delegate", "do", "dynamic",
        "else", "enum", "expect", "external", "false", "field", "file", "final", "finally",
        "for", "fun", "get", "if", "import", "in", "infix", "init", "inline", "inner",
        "interface", "internal", "is", "it", "lateinit", "noinline", "null", "object",
        "open", "operator", "out", "override", "package", "param", "private", "property",
        "protected", "public", "receiver", "reified", "return", "sealed", "set", "setparam",
        "super", "suspend", "tailrec", "this", "throw", "true", "try", "typealias", "typeof",
        "val", "var", "vararg", "when", "where", "while",
        "boolean", "byte", "char", "double", "float", "int", "long", "short", "void",
        "default", "new", "switch", "case", "extends", "implements", "instanceof", "synchronized",
        "transient", "volatile", "native", "strictfp", "throws", "assert", "goto",
        "def", "elif", "pass", "lambda", "yield", "with", "from", "global", "nonlocal",
        "del", "async", "await", "function", "let", "const", "export", "debugger"
    )

    private enum class TokenType {
        NONE, KEYWORD, STRING, COMMENT, NUMBER, ANNOTATION, SYMBOL
    }

    private data class Token(
        val type: TokenType,
        val start: Int,
        val end: Int
    )

    fun highlight(code: String, language: String?, isDarkTheme: Boolean): AnnotatedString {
        return buildAnnotatedString {
            append(code)

            // Colors
            val keywordColor = if (isDarkTheme) Color(0xFFCC7832) else Color(0xFF0033B3) // Orange/Blue
            val stringColor = if (isDarkTheme) Color(0xFF6A8759) else Color(0xFF067D17) // Green
            val numberColor = if (isDarkTheme) Color(0xFF6897BB) else Color(0xFF1750EB) // Blue
            val commentColor = if (isDarkTheme) Color(0xFF808080) else Color(0xFF808080) // Gray
            val annotationColor = if (isDarkTheme) Color(0xFFBBB529) else Color(0xFF9E880D) // Yellow/Gold

            val tokens = tokenize(code)

            for (token in tokens) {
                when (token.type) {
                    TokenType.KEYWORD -> addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold), token.start, token.end)
                    TokenType.STRING -> addStyle(SpanStyle(color = stringColor), token.start, token.end)
                    TokenType.COMMENT -> addStyle(SpanStyle(color = commentColor), token.start, token.end)
                    TokenType.NUMBER -> addStyle(SpanStyle(color = numberColor), token.start, token.end)
                    TokenType.ANNOTATION -> addStyle(SpanStyle(color = annotationColor), token.start, token.end)
                    else -> {}
                }
            }
        }
    }

    private fun tokenize(code: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val len = code.length

        while (i < len) {
            val c = code[i]

            // 1. Comments
            if (c == '/' && i + 1 < len) {
                val next = code[i + 1]
                if (next == '/') {
                    // Single line comment
                    val start = i
                    i += 2
                    while (i < len && code[i] != '\n') {
                        i++
                    }
                    tokens.add(Token(TokenType.COMMENT, start, i))
                    continue
                } else if (next == '*') {
                    // Multi line comment
                    val start = i
                    i += 2
                    while (i < len - 1 && !(code[i] == '*' && code[i + 1] == '/')) {
                        i++
                    }
                    if (i < len - 1) {
                        i += 2 // consume */
                    } else {
                        i = len // unterminated
                    }
                    tokens.add(Token(TokenType.COMMENT, start, i))
                    continue
                }
            } else if (c == '#') {
                // Python/Shell style comment (simplified, assumes # at start or after space if strict, but here lenient)
                 // But wait, # can be in string or char. This logic assumes we are at top level (not in string).
                 // We handle strings below. Since we check comment first, we prioritize comment?
                 // No, usually string takes precedence if we are in code. But here we are iterating sequentially.
                 // So if we find " then we enter string mode. If we find # inside string we treat as string content.
                 // So checking # here is correct IF we ensure strings are checked first?
                 // Actually, standard parsers check tokens. 
                 // Let's reorder: Strings check should be robust.
                 // Python comments start with #. C/Java don't.
                 // Let's support # comment for now as it's common in scripts.
                 val start = i
                 i++
                 while (i < len && code[i] != '\n') {
                     i++
                 }
                 tokens.add(Token(TokenType.COMMENT, start, i))
                 continue
            }

            // 2. Strings
            if (c == '"' || c == '\'') {
                val quote = c
                val start = i
                i++
                while (i < len) {
                    if (code[i] == '\\' && i + 1 < len) {
                        i += 2 // Skip escaped char
                        continue
                    }
                    if (code[i] == quote) {
                        i++ // Consume closing quote
                        break
                    }
                    i++
                }
                tokens.add(Token(TokenType.STRING, start, i))
                continue
            }

            // 3. Numbers
            if (c.isDigit()) {
                val start = i
                while (i < len && (code[i].isDigit() || code[i] == '.' || code[i] == 'f' || code[i] == 'L' || code[i] == 'x' || code[i] in 'a'..'f' || code[i] in 'A'..'F')) {
                     // Very simplified number parsing
                    i++
                }
                tokens.add(Token(TokenType.NUMBER, start, i))
                continue
            }

            // 4. Annotations
            if (c == '@') {
                val start = i
                i++
                if (i < len && code[i].isLetter()) {
                    while (i < len && (code[i].isLetterOrDigit() || code[i] == '_')) {
                        i++
                    }
                    tokens.add(Token(TokenType.ANNOTATION, start, i))
                    continue
                }
            }

            // 5. Keywords and Identifiers
            if (c.isLetter() || c == '_') {
                val start = i
                while (i < len && (code[i].isLetterOrDigit() || code[i] == '_')) {
                    i++
                }
                val word = code.substring(start, i)
                if (KEYWORDS.contains(word)) {
                    tokens.add(Token(TokenType.KEYWORD, start, i))
                }
                continue
            }

            // Skip whitespace and other chars
            i++
        }
        return tokens
    }
}
