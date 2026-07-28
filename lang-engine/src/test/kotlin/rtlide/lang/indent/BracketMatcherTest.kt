package rtlide.lang.indent

import org.junit.Assert.assertEquals
import org.junit.Test
import rtlide.lang.schema.IndentRules

class BracketMatcherTest {

    private val rules = IndentRules(
        indentTriggers = listOf("ابدأ"),
        dedentTriggers = listOf("انتهى"),
        indentSize = 4,
        useSpaces = true
    )

    @Test
    fun testCalculateSmartEnter_NoTrigger() {
        val line = "    أكتب(\"مرحبا\")"
        val result = calculateSmartEnter(line, rules)
        assertEquals("\n    ", result.text)
        assertEquals(1, result.caretLineOffset)
        assertEquals(4, result.caretColOffset)
    }

    @Test
    fun testCalculateSmartEnter_WithTrigger_NoAutoCloseNeeded() {
        val line = "إجراء ترحيب() ابدأ"
        val fullText = """
            إجراء ترحيب() ابدأ
            
            انتهى
        """.trimIndent()
        val result = calculateSmartEnter(line, rules, fullText, 0)
        // Should only produce newline + indent, because 'انتهى' already exists
        assertEquals("\n    ", result.text)
        assertEquals(1, result.caretLineOffset)
        assertEquals(4, result.caretColOffset)
    }

    @Test
    fun testCalculateSmartEnter_WithTrigger_AutoCloseNeeded() {
        val line = "إجراء ترحيب() ابدأ"
        val fullText = """
            إجراء ترحيب() ابدأ
        """.trimIndent()
        val result = calculateSmartEnter(line, rules, fullText, 0)
        // Should produce auto-close block
        assertEquals("\n    \nانتهى", result.text)
    }

    @Test
    fun testCalculateSmartEnter_WithTriggerAndLeadingWhitespace() {
        val line = "    إن كان صح ابدأ"
        val result = calculateSmartEnter(line, rules)
        assertEquals("\n        \n    انتهى", result.text)
        assertEquals(1, result.caretLineOffset)
        assertEquals(8, result.caretColOffset)
    }

    @Test
    fun testReformat() {
        val input = """
ابدأ
أكتب("1")
أكتب("2")
انتهى
""".trimIndent()

        val expected = """
ابدأ
    أكتب("1")
    أكتب("2")
انتهى
""".trimIndent()

        assertEquals(expected, reformat(input, rules))
    }

    @Test
    fun testReformat_Nested() {
        val input = """
ابدأ
أكتب("خارج")
ابدأ
أكتب("داخل")
انتهى
انتهى
""".trimIndent()

        val expected = """
ابدأ
    أكتب("خارج")
    ابدأ
        أكتب("داخل")
    انتهى
انتهى
""".trimIndent()

        assertEquals(expected, reformat(input, rules))
    }
}
