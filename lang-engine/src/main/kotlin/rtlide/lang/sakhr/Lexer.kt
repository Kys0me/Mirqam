package rtlide.lang.sakhr

import rtlide.lang.analysis.DiagnosticCollector
import rtlide.lang.analysis.Location

class Lexer(private val source: String, private val diagnostics: DiagnosticCollector) {
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var line = 1
    private var column = 1

    private val keywords = mapOf(
        "إجراء" to TokenType.PROCEDURE,
        "ليكن" to TokenType.LET,
        "ألزم" to TokenType.CONST,
        "إذن" to TokenType.THEN,
        "وإلا" to TokenType.ELSE,
        "ابدأ" to TokenType.BEGIN,
        "انتهى" to TokenType.END,
        "السياق" to TokenType.CONTEXT,
        "رجع" to TokenType.RETURN,
        "صح" to TokenType.BOOLEAN,
        "خطأ" to TokenType.BOOLEAN
    )

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }
        tokens.add(Token(TokenType.EOF, "", null, Location(line, column)))
        return tokens
    }

    private fun scanToken() {
        val c = advance()
        checkTashkeel(c)
        
        when (c) {
            '(' -> addToken(TokenType.LEFT_PAREN)
            ')' -> addToken(TokenType.RIGHT_PAREN)
            ':' -> {
                if (match(':')) addToken(TokenType.DOUBLE_COLON)
                else addToken(TokenType.COLON)
            }
            '.' -> addToken(TokenType.DOT)
            '،' -> addToken(TokenType.COMMA)
            '=' -> {
                if (match('=')) addToken(TokenType.EQUALS_EQUALS)
                else addToken(TokenType.EQUALS)
            }
            '<' -> addToken(TokenType.LESS)
            '>' -> addToken(TokenType.GREATER)
            '+' -> addToken(TokenType.PLUS)
            '-' -> addToken(TokenType.MINUS)
            '*' -> addToken(TokenType.STAR)
            '/' -> {
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance()
                } else {
                    addToken(TokenType.SLASH)
                }
            }
            '!' -> {
                if (match('=')) addToken(TokenType.BANG_EQUALS)
                else diagnostics.reportError("رمز غير صالح: '!'", Location(line, column - 1))
            }
            ' ' , '\r', '\t' -> { /* ignore whitespace */ }
            '\n' -> {
                line++
                column = 1
            }
            '"' -> string()
            else -> {
                if (isDigit(c)) {
                    number()
                } else if (isArabicAlpha(c)) {
                    identifier()
                } else {
                    diagnostics.reportError("رمز غير صالح: '$c'", Location(line, column - 1))
                }
            }
        }
    }

    private fun identifier() {
        while (isArabicAlphaNumeric(peek())) advance()
        
        val text = source.substring(start, current)
        if (text == "إن" && peek() == ' ') {
            val potentialSpace = current
            if (source.substring(potentialSpace + 1).startsWith("كان") && 
                !isArabicAlphaNumeric(source.getOrElse(potentialSpace + 4) { '\u0000' })) {
                advance() // space
                advance(); advance(); advance() // ك ا ن
                addToken(TokenType.IF)
                return
            }
        }

        var type = keywords[text]
        if (type == null) type = TokenType.IDENTIFIER
        
        val literal = if (type == TokenType.BOOLEAN) text == "صح" else null
        addToken(type, literal)
    }

    private fun number() {
        while (isDigit(peek())) advance()

        if (peek() == '.' && isDigit(peekNext())) {
            advance()
            while (isDigit(peek())) advance()
        }

        val text = source.substring(start, current)
        val normalized = text.map {
            if (it in '\u0660'..'\u0669') {
                (it.code - 0x0660 + '0'.code).toChar()
            } else {
                it
            }
        }.joinToString("")

        addToken(TokenType.NUMBER, normalized.toDoubleOrNull() ?: 0.0)
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }

        if (isAtEnd()) {
            diagnostics.reportError("نص غير منتهٍ؛ يتوقع وجود علامة اقتباس في نهاية النص.", Location(line, column - 1))
            return
        }

        advance()
        val value = source.substring(start + 1, current - 1)
        addToken(TokenType.STRING, value)
    }

    private fun checkTashkeel(c: Char) {
        val tashkeelRange = '\u064B'..'\u0652'
        if (c in tashkeelRange) {
            diagnostics.reportError("يمنع استخدام علامات التشكيل خارج النصوص الصريحة.", Location(line, column - 1))
        }
    }

    private fun isArabicAlpha(c: Char): Boolean {
        val tashkeelRange = '\u064B'..'\u0652'
        return (c in '\u0621'..'\u064A' || c == '_') && c !in tashkeelRange
    }

    private fun isArabicAlphaNumeric(c: Char): Boolean {
        return isArabicAlpha(c) || isDigit(c)
    }

    private fun isDigit(c: Char): Boolean {
        return c in '0'..'9' || c in '\u0660'..'\u0669'
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false
        current++
        column++
        return true
    }

    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[current]
    private fun peekNext(): Char = if (current + 1 >= source.length) '\u0000' else source[current + 1]

    private fun advance(): Char {
        val c = source[current++]
        column++
        return c
    }

    private fun isAtEnd(): Boolean = current >= source.length

    private fun addToken(type: TokenType, literal: Any? = null) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal, Location(line, column - text.length)))
    }
}
