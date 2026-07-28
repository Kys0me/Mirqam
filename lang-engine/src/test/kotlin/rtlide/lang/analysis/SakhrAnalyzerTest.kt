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
        
        // startColOffset should reach back to 'ليكن'
        // 'س' is at col 5. 'ليكن' is at col 0. offset = -5.
        assertEquals(-5, fix.startColOffset)
        // endColOffset should reach the end of the line (length 20) starting from 'ليكن'
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
                رجع
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
                رجع أ
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
                    واصل
                انتهى
                إن كان (س > 8) إذن ابدأ
                    اكسر
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
}
