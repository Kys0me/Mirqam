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
    fun testCalculateSmartEnter_TriggerInsideString_Ignored() {
        val line = "أكتب(\"ابدأ\")"
        val result = calculateSmartEnter(line, rules)
        assertEquals("\n", result.text)
        assertEquals(0, result.caretColOffset)
    }

    @Test
    fun testCalculateSmartEnter_TriggerInsideComment_Ignored() {
        val line = "ليكن س = 1 // ابدأ"
        val result = calculateSmartEnter(line, rules)
        assertEquals("\n", result.text)
    }

    @Test
    fun testCalculateSmartEnter_NestedBlock_AutoClosesEvenWithOuterCloser() {
        // The existing 'انتهى' belongs to the procedure block; the inner
        // 'إن كان … ابدأ' still needs its own closer (balance is +1).
        val fullText = """
            إجراء ترحيب() ابدأ
                إن كان صح ابدأ
            انتهى
        """.trimIndent()
        val line = "    إن كان صح ابدأ"
        val result = calculateSmartEnter(line, rules, fullText, 1)
        assertEquals("\n        \n    انتهى", result.text)
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

    @Test
    fun testReformat_TriggerInsideStringIgnored() {
        val input = """
ابدأ
أكتب("ابدأ")
أكتب("انتهى")
انتهى
""".trimIndent()

        val expected = """
ابدأ
    أكتب("ابدأ")
    أكتب("انتهى")
انتهى
""".trimIndent()

        assertEquals(expected, reformat(input, rules))
    }

    @Test
    fun testReformat_TriggerInsideCommentIgnored() {
        val input = """
ابدأ
// هنا ابدأ شيء
أكتب("1")
انتهى
""".trimIndent()

        val expected = """
ابدأ
    // هنا ابدأ شيء
    أكتب("1")
انتهى
""".trimIndent()

        assertEquals(expected, reformat(input, rules))
    }

    @Test
    fun testReformat_TriggerInsideIdentifierIgnored() {
        // 'ابدأها' contains 'ابدأ' but is a different word
        val input = """
ابدأ
ليكن ابدأها = 1
انتهى
""".trimIndent()

        val expected = """
ابدأ
    ليكن ابدأها = 1
انتهى
""".trimIndent()

        assertEquals(expected, reformat(input, rules))
    }

    @Test
    fun testReformat_EndElseBeginChain() {
        // 'انتهى وإلا ابدأ' must dedent for itself, then re-indent what follows
        val input = """
إن كان صح إذن ابدأ
أكتب("نعم")
انتهى وإلا ابدأ
أكتب("لا")
انتهى
""".trimIndent()

        val expected = """
إن كان صح إذن ابدأ
    أكتب("نعم")
انتهى وإلا ابدأ
    أكتب("لا")
انتهى
""".trimIndent()

        assertEquals(expected, reformat(input, rules))
    }

    @Test
    fun testReformat_MultiLineStringPreserved() {
        val input = """
ابدأ
ليكن نص = "سطر أول
   سطر ثانٍ محفوظ"
أكتب(نص)
انتهى
""".trimIndent()

        val expected = """
ابدأ
    ليكن نص = "سطر أول
   سطر ثانٍ محفوظ"
    أكتب(نص)
انتهى
""".trimIndent()

        assertEquals(expected, reformat(input, rules))
    }

    @Test
    fun testReformat_ContinuationIndentInsideParens() {
        val input = """
ابدأ
أكتب(س،
ص)
انتهى
""".trimIndent()

        val expected = """
ابدأ
    أكتب(س،
        ص)
انتهى
""".trimIndent()

        assertEquals(expected, reformat(input, rules))
    }

    @Test
    fun testReformat_UnbalancedEndDoesNotGoNegative() {
        val input = """
انتهى
أكتب("1")
""".trimIndent()

        val expected = """
انتهى
أكتب("1")
""".trimIndent()

        assertEquals(expected, reformat(input, rules))
    }

    @Test
    fun testReformat_BlankLinesBecomeEmpty() {
        val input = "ابدأ\n   \nأكتب(\"1\")\nانتهى"
        val expected = "ابدأ\n\n    أكتب(\"1\")\nانتهى"
        assertEquals(expected, reformat(input, rules))
    }

    @Test
    fun testReformat_Idempotent() {
        val input = """
إجراء ترحيب() ابدأ
    إن كان صح إذن ابدأ
        أكتب("مرحبا // ليس تعليقًا ابدأ")
    انتهى وإلا ابدأ
        أكتب("لا")
    انتهى
انتهى
""".trimIndent()

        val once = reformat(input, rules)
        assertEquals(once, reformat(once, rules))
        assertEquals(input, once)
    }
}
