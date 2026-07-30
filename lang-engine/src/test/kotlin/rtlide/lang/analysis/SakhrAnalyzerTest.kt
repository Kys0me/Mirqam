package rtlide.lang.analysis

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SakhrAnalyzerTest {
    private val analyzer = SakhrAnalyzer()

    @Test
    fun testSyntaxError() {
        val source = "ليكن س = "
        val diagnostics = analyzer.analyze(source).diagnostics
        assertTrue(diagnostics.any { it.severity == Severity.Error })
    }

    @Test
    fun testUnusedVariableSmartRange() {
        val source = "ليكن س : رقم = 5 + 5"
        val diagnostics = analyzer.analyze(source).diagnostics
        val unused = diagnostics.find { it.message.contains("س") && it.severity == Severity.Warning }
        assertTrue(unused != null, "Should have unused warning for 'س'")
        
        val fix = unused.fixes.find { it.replacement == "SAFE_DELETE_VAR" }
        assertTrue(fix != null, "Should have safe delete fix")
        
        // 'س' starts at col 6 (1-based). 'ليكن' starts at col 1. Offset = 1 - 6 = -5.
        assertEquals(-5, fix.startColOffset)
        // fixLength is total length from 'ليكن' to end of '5' (col 20 + length 1 = 21). 21 - 1 = 20.
        assertEquals(20, fix.endColOffset)
    }

    @Test
    fun testVarCanBeValWarning() {
        val source = """
            ليكن س = 10
            أكتب(س)
        """.trimIndent()
        val diagnostics = analyzer.analyze(source).diagnostics
        assertTrue(diagnostics.any { it.severity == Severity.Warning })
    }

    @Test
    fun testNoErrorForUsedVar() {
        val source = """
            ليكن س = 10
            س = 20
            أكتب(س)
        """.trimIndent()
        val diagnostics = analyzer.analyze(source).diagnostics
        assertTrue(diagnostics.none { it.severity == Severity.Warning })
    }

    @Test
    fun testUseBeforeInitialization() {
        val source = """
            ليكن س
            أكتب(س)
        """.trimIndent()
        val diagnostics = analyzer.analyze(source).diagnostics
        assertTrue(diagnostics.any { it.severity == Severity.Error })
    }

    @Test
    fun testValReassignmentError() {
        val source = """
            ألزم س = 10
            س = 20
        """.trimIndent()
        val diagnostics = analyzer.analyze(source).diagnostics
        assertTrue(diagnostics.any { it.severity == Severity.Error })
        assertTrue(diagnostics.any { it.fixes.any { fix -> fix.replacement == "CHANGE_TO_VAR" } })
    }

    @Test
    fun testWarningForUnusedFunctionParams() {
        val sourceUnused = """
            إجراء تجربة(س: رقم) ابدأ
                رد
            انتهى
            تجربة(5)
        """.trimIndent()
        val diagsUnused = analyzer.analyze(sourceUnused).diagnostics
        assertTrue(diagsUnused.any { it.severity == Severity.Warning && it.message.contains("س") }, "Should have warning for unused parameter")
    }

    @Test
    fun testImplicitAndExplicitTypes() {
        val source = """
            ليكن س = 5
            أكتب(س)
            إجراء دالة(أ) ابدأ
                أكتب(أ)
                رد أ
            انتهى
            دالة(س)
        """.trimIndent()
        val diags = analyzer.analyze(source).diagnostics
        val infoDiags = diags.filter { it.severity == Severity.Information }
        assertTrue(infoDiags.isNotEmpty(), "Should have information diagnostics")
    }

    @Test
    fun testUnusedFunctionWarning() {
        val source = """
            إجراء غير_مستخدمة() ابدأ
            انتهى
        """.trimIndent()
        val diags = analyzer.analyze(source).diagnostics
        assertTrue(diags.any { it.severity == Severity.Warning && it.message.contains("غير_مستخدمة") })
    }

    @Test
    fun testLoopsAndLogicalOperators() {
        val source = """
            ليكن س = 0
            ما دام (س < 10) كرر
                س += 1
                إن كان (س == 5) إذن ابدأ
                    امض
                انتهى
                إن كان (س > 8) إذن ابدأ
                    اكفف
                انتهى
            انتهى
            
            ليكن قائمة = [1، 2، 3]
            لكل (عنصر في قائمة) ابدأ
                أكتب(عنصر)
            انتهى
            
            إن كان (صح و ليس خطأ) إذن ابدأ
                أكتب("تمام")
            انتهى
        """.trimIndent()
        val diags = analyzer.analyze(source).diagnostics
        val errors = diags.filter { it.severity == Severity.Error }
        assertTrue(errors.isEmpty(), "Should have no errors. Errors: ${errors.joinToString { it.message }}")
    }

    @Test
    fun testSimpleStruct() {
        val source = """
            بنية س ابدأ ح: رقم انتهى
            ليكن أ = س(ح = 10)
            أكتب(أ.ح)
        """.trimIndent()
        val diags = analyzer.analyze(source).diagnostics
        assertTrue(diags.none { it.severity == Severity.Error }, "Should have no errors. Errors: ${diags.filter { it.severity == Severity.Error }.joinToString { it.message }}")
    }

    @Test
    fun testStructConstructorWithNamedArgs() {
        val source = """
            بنية س ابدأ ح: رقم انتهى
            ليكن أ = س(ح = 10)
        """.trimIndent()
        val diags = analyzer.analyze(source).diagnostics
        assertTrue(diags.none { it.severity == Severity.Error }, "Should have no errors. Errors: ${diags.filter { it.severity == Severity.Error }.joinToString { it.message }}")
    }

    @Test
    fun testAnonymousStructPropertySetting() {
        val source = """
            بنية س ابدأ ح: رقم انتهى
            س(ح = 10).ح = 10
        """.trimIndent()
        val diags = analyzer.analyze(source).diagnostics
        assertTrue(diags.none { it.severity == Severity.Error }, "Should have no errors")
    }

    @Test
    fun testStructPropertySetting() {
        val source = """
            بنية نقطة ابدأ
                س: رقم
                ص: رقم
            انتهى
            ليكن ن = نقطة(س = 10، ص = 20)
            ن.س = 10
            ن.ع = 20
        """.trimIndent()
        val diags = analyzer.analyze(source).diagnostics
        assertTrue(diags.any { it.message.contains("ع") && it.severity == Severity.Error }, "Should have error for missing property 'ع'")
    }

    @Test
    fun testStructPositionalConstructor() {
        val source = """
            بنية نقطة ابدأ
                س: رقم
                ص: رقم = 0
            انتهى
            ليكن ن = نقطة(10)
            ليكن ن2 = نقطة(10، 20)
            ليكن ن3 = نقطة(10، 20، 30)
        """.trimIndent()
        val diags = analyzer.analyze(source).diagnostics
        assertTrue(diags.any { it.message.contains("أكثر من عدد حقول") }, "Should have error for too many args")
    }

    @Test
    fun testStructMissingRequiredField() {
        val source = """
            بنية نقطة ابدأ
                س: رقم
                ص: رقم
            انتهى
            ليكن ن = نقطة(س = 10)
        """.trimIndent()
        val diags = analyzer.analyze(source).diagnostics
        assertTrue(diags.any { it.message.contains("ص") && it.message.contains("لم يتم تعيينه") }, "Should have error for missing required field")
    }

    @Test
    fun testStructTypeMismatchInConstructor() {
        val source = """
            بنية نقطة ابدأ
                س: رقم
            انتهى
            ليكن ن = نقطة(س = "نص")
        """.trimIndent()
        val diags = analyzer.analyze(source).diagnostics
        assertTrue(diags.any { it.message.contains("لا يتطابق") && it.severity == Severity.Error }, "Should have type mismatch error")
    }

    @Test
    fun testOptionalTypeWarning() {
        val source = """
            بنية نقطة ابدأ
                س: رقم
            انتهى
            إجراء تجربة(ن: نقطة؟) ابدأ
                ليكن س = ن.س
            انتهى
        """.trimIndent()
        val diags = analyzer.analyze(source).diagnostics
        assertTrue(diags.any { it.severity == Severity.Warning && it.message.contains("نوع اختياري") }, "Should have warning for property access on optional")
    }

    @Test
    fun testOptionalTypeSetError() {
        val source = """
            بنية نقطة ابدأ
                س: رقم
            انتهى
            إجراء تجربة(ن: نقطة؟) ابدأ
                ن.س = 10
            انتهى
        """.trimIndent()
        val diags = analyzer.analyze(source).diagnostics
        assertTrue(diags.any { it.severity == Severity.Error && it.message.contains("نوع اختياري") }, "Should have error for setting property on optional")
    }
}
