package rtlide.lang.sakhr

import rtlide.lang.analysis.DiagnosticCollector
import rtlide.lang.analysis.Location

class Lexer(private val source: String, private val diagnostics: DiagnosticCollector) {
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var line = 1
    private var column = 1
    // Location where the token currently being scanned begins. Captured before
    // consuming any character so locations stay exact even for multi-line tokens.
    private var startLine = 1
    private var startColumn = 1

    companion object {
        val keywords = mapOf(
            "إجراء" to TokenType.PROCEDURE,
            "ليكن" to TokenType.LET,
            "ألزم" to TokenType.CONST,
            "إذن" to TokenType.THEN,
            "وإلا" to TokenType.ELSE,
            "ابدأ" to TokenType.BEGIN,
            "انتهى" to TokenType.END,
            "السياق" to TokenType.CONTEXT,
            "رد" to TokenType.RETURN,
            "كرر" to TokenType.REPEAT,
            "لكل" to TokenType.FOR_EACH,
            "في" to TokenType.IN,
            "اكفف" to TokenType.BREAK,
            "امض" to TokenType.CONTINUE,
            "بلغ" to TokenType.RAISE,
            "و" to TokenType.AND,
            "أو" to TokenType.OR,
            "ليس" to TokenType.NOT,
            "فارغ" to TokenType.NULL,
            "عدم" to TokenType.VOID,
            "بنية" to TokenType.STRUCT,
            "صح" to TokenType.BOOLEAN,
            "خطأ" to TokenType.BOOLEAN
        )
    }

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current
            startLine = line
            startColumn = column
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

            '<' -> {
                if (match('=')) addToken(TokenType.LESS_EQUALS)
                else addToken(TokenType.LESS)
            }

            '>' -> {
                if (match('=')) addToken(TokenType.GREATER_EQUALS)
                else addToken(TokenType.GREATER)
            }

            '+' -> {
                if (match('=')) addToken(TokenType.PLUS_EQUALS)
                else addToken(TokenType.PLUS)
            }

            '-' -> {
                if (match('=')) addToken(TokenType.MINUS_EQUALS)
                else addToken(TokenType.MINUS)
            }

            '*' -> {
                if (match('=')) addToken(TokenType.STAR_EQUALS)
                else addToken(TokenType.STAR)
            }

            '%' -> addToken(TokenType.PERCENT)
            '[' -> addToken(TokenType.LEFT_BRACKET)
            ']' -> addToken(TokenType.RIGHT_BRACKET)
            '؟' -> addToken(TokenType.QUESTION_MARK)
            '/' -> {
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance()
                } else if (match('=')) {
                    addToken(TokenType.SLASH_EQUALS)
                } else {
                    addToken(TokenType.SLASH)
                }
            }

            '!' -> {
                if (match('=')) addToken(TokenType.BANG_EQUALS)
                else reportUnexpectedChar(c)
            }

            ' ', '\r', '\t' -> { /* ignore whitespace */
            }

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
                    reportUnexpectedChar(c)
                }
            }
        }
    }

    private fun reportUnexpectedChar(c: Char) {
        val location = Location(startLine, startColumn)
        val message = when (c) {
            '!' -> "الرمز '!' لا يُستخدم وحده في لغة صخر. استخدم '!=' للتحقق من عدم المساواة، أو الكلمة 'ليس' لنفي قيمة منطقية."
            ',' -> "الفاصلة اللاتينية ',' غير معتمدة؛ استخدم الفاصلة العربية '،' للفصل بين العناصر."
            '?' -> "علامة الاستفهام اللاتينية '?' غير معتمدة؛ استخدم علامة الاستفهام العربية '؟' للأنواع الاختيارية."
            ';' -> "الفاصلة المنقوطة ';' لا تُستخدم في لغة صخر."
            '{', '}' -> "القوس '$c' لا يُستخدم لتحديد الكتل؛ استخدم 'ابدأ' و'انتهى' بدلاً منها."
            '\'' -> "علامة الاقتباس المفردة (') غير معتمدة؛ استخدم المزدوجة \"...\"."
            in 'a'..'z', in 'A'..'Z' -> "الحرف اللاتيني '$c' لا يصلح لبدء اسم؛ استخدم الأحرف العربية."
            else -> "رمز غير صالح: '$c'"
        }
        diagnostics.reportError(message, location)
    }

    private fun identifier() {
        while (isArabicAlphaNumeric(peek())) advance()

        val text = source.substring(start, current)
        // Multi-word keywords
        if (text == "إن" && peek() == ' ') {
            val potentialSpace = current
            if (source.substring(potentialSpace + 1).startsWith("كان") &&
                !isArabicAlphaNumeric(source.getOrElse(potentialSpace + 4) { '\u0000' })
            ) {
                advance() // space
                advance(); advance(); advance() // ك ا ن
                addToken(TokenType.IF)
                return
            }
        }

        if (text == "ما" && peek() == ' ') {
            val potentialSpace = current
            if (source.substring(potentialSpace + 1).startsWith("دام") &&
                !isArabicAlphaNumeric(source.getOrElse(potentialSpace + 4) { '\u0000' })
            ) {
                advance() // space
                advance(); advance(); advance() // د ا م
                addToken(TokenType.WHILE)
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
            if (it in '\u0660'..'\u0669') (it.code - 0x0660 + '0'.code).toChar() else it
        }.joinToString("")

        addToken(TokenType.NUMBER, normalized.toDoubleOrNull() ?: 0.0)
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') {
                line++
                column = 0
            }
            advance()
        }

        if (isAtEnd()) {
            diagnostics.reportError("نص غير منتهٍ؛ يتوقع وجود علامة اقتباس في نهاية النص.", Location(startLine, startColumn), (current - start).coerceAtLeast(1))
            return
        }

        advance()
        val value = source.substring(start + 1, current - 1)
        addToken(TokenType.STRING, value)
    }

    private fun checkTashkeel(c: Char) {
        val tashkeelRange = '\u064B'..'\u0652'
        if (c in tashkeelRange) {
            diagnostics.reportError("يمنع استخدام علامات التشكيل خارج النصوص الصريحة.", Location(startLine, startColumn))
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
        // Use the captured token-start location: subtracting the lexeme length from
        // the current column is wrong for tokens that span line breaks (strings).
        tokens.add(Token(type, text, literal, Location(startLine, startColumn)))
    }
}
