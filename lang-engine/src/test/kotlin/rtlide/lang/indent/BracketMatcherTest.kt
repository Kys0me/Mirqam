package rtlide.lang.indent

import org.junit.Test
import org.junit.Assert.*
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
    fun testCalculateSmartEnter_WithTrigger() {
        val line = "إجراء ترحيب() ابدأ"
        val result = calculateSmartEnter(line, rules)
        // Should produce newline + indent + newline + dedent
        assertEquals("\n    \nانتهى", result.text)
        assertEquals(1, result.caretLineOffset)
        assertEquals(4, result.caretColOffset)
    }

    @Test
    fun testCalculateSmartEnter_WithTriggerAndLeadingWhitespace() {
        val line = "    إن كان صح ابدأ"
        val result = calculateSmartEnter(line, rules)
        assertEquals("\n        \n    انتهى", result.text)
        assertEquals(1, result.caretLineOffset)
        assertEquals(8, result.caretColOffset)
    }
}
