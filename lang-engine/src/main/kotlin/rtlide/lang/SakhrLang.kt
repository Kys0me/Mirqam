package rtlide.lang

import rtlide.lang.schema.BracketPair
import rtlide.lang.schema.Grammar
import rtlide.lang.schema.IndentRules
import rtlide.lang.schema.LanguageDefinition
import rtlide.lang.schema.StringRule
import rtlide.lang.schema.Theme
import rtlide.lang.schema.TokenStyle

/**
 * Sakhr (صخر) language definition.
 */
object SakhrLang {

    fun definition(): LanguageDefinition = LanguageDefinition(
        id = "sakhr",
        displayName = "صخر",
        fileExtensions = listOf("صخر", "sakhr"),
        grammar = Grammar(
            keywords = listOf("إجراء", "ليكن", "ألزم", "إذن", "السياق", "ابدأ", "انتهى", "كرر", "في", "اكسر", "واصل", "و", "أو", "ليس"),
            controlKeywords = listOf("إن كان", "وإلا", "رجع", "ما دام", "لكل"),
            builtins = listOf("أكتب", "إنهاء_البرنامج", "نص", "طول", "حجم", "رقم", "منطقي", "قائمة", "خذ", "المطلع", "اقرأ", "أضف", "أزل", "أدخل"),
            constants = listOf("صح", "خطأ", "عدم"),
            strings = listOf(StringRule(begin = "\"", end = "\"", escape = "\\")),
        ),
        indent = IndentRules(indentTriggers = listOf("ابدأ"), dedentTriggers = listOf("انتهى"), indentSize = 4),
        brackets = listOf(
            BracketPair("(", ")"),
            BracketPair("[", "]"),
            BracketPair("{", "}"),
            BracketPair("\"", "\""),
            BracketPair("'", "'")
        ),
    )

    fun theme(): Theme = Theme(
        name = "dark",
        tokenColors = mapOf(
            "keyword.control.arabic" to TokenStyle("#C586C0"),
            "storage.type.arabic" to TokenStyle("#569CD6"),
            "constant.language.arabic" to TokenStyle("#569CD6"),
            "support.function.builtin" to TokenStyle("#DCDCAA"),
            "entity.name.function" to TokenStyle("#DCDCAA"),
            "constant.numeric" to TokenStyle("#B5CEA8"),
            "string.quoted" to TokenStyle("#CE9178"),
            "comment.line" to TokenStyle("#6A9955", italic = true),
            "default" to TokenStyle("#D4D4D4"),
        ),
    )

}
