package rtlide.lang.intelligence

import org.junit.Test
import rtlide.lang.analysis.SakhrAnalyzer
import kotlin.test.assertTrue

class CompletionEngineTest {
    private val analyzer = SakhrAnalyzer()

    private fun suggest(source: String, line: Int, col: Int, explicit: Boolean = true): List<CompletionItem> {
        val model = analyzer.analyze(source).completion
        return CompletionEngine.suggest(source.split("\n"), line, col, model, explicit)
    }

    private fun List<CompletionItem>.has(label: String) = any { it.label == label }

    @Test
    fun testLocalVariableNotVisibleOutsideFunction() {
        val source = """
            إجراء تجربة() ابدأ
                ليكن داخلي = 5
                أكتب(داخلي)
            انتهى
            ليكن خارجي = 1
            
        """.trimIndent()
        val items = suggest(source, 5, 0)
        assertTrue(!items.has("داخلي"), "Local variable must not leak out of its function")
        assertTrue(items.has("خارجي"), "Top-level variable should be visible")
        assertTrue(items.has("تجربة"), "Function should be visible")
    }

    @Test
    fun testVariableNotVisibleBeforeDeclaration() {
        val source = """
            
            ليكن متأخر = 5
        """.trimIndent()
        val items = suggest(source, 0, 0)
        assertTrue(!items.has("متأخر"), "Variable must not be suggested before its declaration line")
    }

    @Test
    fun testFunctionHoistedBeforeDeclaration() {
        val source = """
            
            إجراء لاحقة() ابدأ
            انتهى
        """.trimIndent()
        val items = suggest(source, 0, 0)
        assertTrue(items.has("لاحقة"), "Functions are hoisted and visible before their declaration")
    }

    @Test
    fun testReturnOnlyInsideFunction() {
        val source = """
            إجراء تجربة() ابدأ
                
            انتهى
            
        """.trimIndent()
        assertTrue(suggest(source, 1, 4).has("رد"), "'رد' should be offered inside a function body")
        assertTrue(!suggest(source, 3, 0).has("رد"), "'رد' must not be offered at top level")
    }

    @Test
    fun testBreakContinueOnlyInsideLoop() {
        val source = """
            ما دام (صح) كرر
                
            انتهى
            
        """.trimIndent()
        val inside = suggest(source, 1, 4)
        assertTrue(inside.has("اكفف") && inside.has("امض"), "Loop keywords should be offered inside a loop")
        val outside = suggest(source, 3, 0)
        assertTrue(!outside.has("اكفف") && !outside.has("امض"), "Loop keywords must not be offered outside a loop")
    }

    @Test
    fun testTypePositionSuggestsTypesAndStructs() {
        val source = """
            بنية نقطة ابدأ
                س: رقم
            انتهى
            ليكن م: 
        """.trimIndent()
        val items = suggest(source, 3, 8)
        assertTrue(items.has("رقم") && items.has("نص"), "Built-in types expected after ':'")
        assertTrue(items.has("نقطة"), "Struct types expected after ':'")
        assertTrue(!items.has("ليكن"), "Statement keywords make no sense after ':'")
    }

    @Test
    fun testNamingPositionSuggestsNothing() {
        val source = "ليكن "
        assertTrue(suggest(source, 0, 5).isEmpty(), "No suggestions while naming a new variable")
    }

    @Test
    fun testDotCompletionShowsStructFields() {
        val source = """
            بنية نقطة ابدأ
                س: رقم
                ص: رقم
            انتهى
            ليكن ن = نقطة(س = 1، ص = 2)
            ن.
        """.trimIndent()
        val items = suggest(source, 5, 2)
        assertTrue(items.has("س") && items.has("ص"), "Struct fields expected after '.': $items")
        assertTrue(!items.has("ليكن"), "Keywords must not appear in member completion")
    }

    @Test
    fun testDotCompletionShowsListMethods() {
        val source = """
            ليكن ق = [1، 2، 3]
            ق.
        """.trimIndent()
        val items = suggest(source, 1, 2)
        assertTrue(items.has("أضف") && items.has("حجم"), "List extension methods expected after '.': $items")
    }

    @Test
    fun testStatementStartOffersKeywords() {
        val items = suggest("", 0, 0)
        assertTrue(items.has("ليكن") && items.has("إن كان"), "Statement keywords expected at line start")
        assertTrue(items.has("أكتب"), "Built-in functions expected at line start")
    }

    @Test
    fun testPrefixFiltering() {
        val source = """
            ليكن عدد = 5
            ليكن نص_ما = "أ"
            عد
        """.trimIndent()
        val items = suggest(source, 2, 2, explicit = false)
        assertTrue(items.has("عدد"), "Matching symbol expected: $items")
        assertTrue(!items.has("نص_ما"), "Non-matching symbols must be filtered out")
    }

    @Test
    fun testStructBodySuggestsNothingAtFieldPosition() {
        val source = """
            بنية نقطة ابدأ
                
            انتهى
        """.trimIndent()
        assertTrue(suggest(source, 1, 4).isEmpty(), "Inside a struct body a new field name is expected")
    }

    @Test
    fun testMainArgsCompletion() {
        val source = "إجراء المطلع ("
        val items = suggest(source, 0, 14)
        assertTrue(items.has("وسائط: قائمة(نص)"), "Main function args should be suggested")
    }

    @Test
    fun testTypePositionNoParentheses() {
        val source = """
            بنية نقطة ابدأ
                س: رقم
            انتهى
            ليكن م: 
        """.trimIndent()
        val items = suggest(source, 3, 8)
        val point = items.find { it.label == "نقطة" }
        assertTrue(point != null && point.paramCount == -1, "Structs in type position should not have paramCount: $point")
    }
}
