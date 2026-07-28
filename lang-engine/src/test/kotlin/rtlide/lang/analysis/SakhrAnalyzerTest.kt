package rtlide.lang.analysis

import org.junit.Test
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
    fun testUnusedVariableWarning() {
        val source = "ليكن س = 10"
        val diagnostics = analyzer.analyze(source).diagnostics
        assertTrue(diagnostics.any { it.severity == Severity.Warning })
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
    fun testNoWarningForFunctionParams() {
        val sourceUnused = """
            إجراء تجربة(س: رقم) ابدأ
                رجع
            انتهى
        """.trimIndent()
        val diagsUnused = analyzer.analyze(sourceUnused).diagnostics
        assertTrue(diagsUnused.none { it.severity == Severity.Warning }, "Should have no warnings for parameters")
    }

    @Test
    fun testImplicitAndExplicitTypes() {
        val source = """
            ليكن س = 5
            أكتب(س)
            ألزم ص: رقم = 10
            أكتب(ص)
            إجراء دالة(أ) ابدأ
                أكتب(أ)
                رجع أ
            انتهى
        """.trimIndent()
        val diags = analyzer.analyze(source).diagnostics
        val infoDiags = diags.filter { it.severity == Severity.Information }
        assertTrue(infoDiags.size >= 2, "Should have multiple information diagnostics")
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
