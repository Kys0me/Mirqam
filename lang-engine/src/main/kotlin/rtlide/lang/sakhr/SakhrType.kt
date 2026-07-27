package rtlide.lang.sakhr

enum class SakhrType(val lexeme: String) {
    NUMBER("رقم"),
    STRING("نص"),
    BOOLEAN("منطقي"),
    VOID("عدم"),
    LIST("قائمة"),
    UNKNOWN("مجهول");

    companion object {
        fun fromLexeme(lexeme: String): SakhrType {
            if (lexeme.startsWith("قائمة")) return LIST
            return entries.find { it.lexeme == lexeme } ?: UNKNOWN
        }
    }
}
