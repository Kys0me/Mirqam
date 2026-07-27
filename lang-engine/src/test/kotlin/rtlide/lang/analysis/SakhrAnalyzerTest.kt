package rtlide.lang.analysis

import org.junit.Test
import kotlin.test.assertTrue

class SakhrAnalyzerTest {
    private val analyzer = SakhrAnalyzer()

    @Test
    fun testSyntaxError() {
        val source = "ليكن س = "
        val diagnostics = analyzer.analyze(source)
        assertTrue(diagnostics.any { it.severity == Severity.Error })
    }

    @Test
    fun testUnusedVariableWarning() {
        val source = "ليكن س = 10"
        val diagnostics = analyzer.analyze(source)
        assertTrue(diagnostics.any { it.severity == Severity.Warning && it.message.contains("غير مستخدم") })
    }

    @Test
    fun testVarCanBeValWarning() {
        val source = """
            ليكن س = 10
            أكتب(س)
        """.trimIndent()
        val diagnostics = analyzer.analyze(source)
        assertTrue(diagnostics.any { it.severity == Severity.Warning && it.message.contains("ألزم") })
    }

    @Test
    fun testNoErrorForUsedVar() {
        val source = """
            ليكن س = 10
            س = 20
            أكتب(س)
        """.trimIndent()
        val diagnostics = analyzer.analyze(source)
        // No warnings for unused or "can be val"
        assertTrue(diagnostics.none { it.severity == Severity.Warning })
    }

    @Test
    fun testEnforcedInitialization() {
        val source = "ليكن س"
        val diagnostics = analyzer.analyze(source)
        assertTrue(diagnostics.any { it.severity == Severity.Error && it.message.contains("تعيين قيمة ابتدائية") })
        assertTrue(diagnostics.any { it.fixes.any { fix -> fix.replacement == "ADD_INITIALIZER" } })
    }

    @Test
    fun testValReassignmentError() {
        val source = """
            ألزم س = 10
            س = 20
        """.trimIndent()
        val diagnostics = analyzer.analyze(source)
        assertTrue(diagnostics.any { it.severity == Severity.Error && it.message.contains("ألزم") })
        assertTrue(diagnostics.any { it.fixes.any { fix -> fix.replacement == "CHANGE_TO_VAR" } })
    }

    @Test
    fun testNoWarningForFunctionParams() {
        val source = """
            إجراء تجربة(س: رقم) ابدأ
                أكتب(س)
            انتهى
        """.trimIndent()
        // Should not have "can be val" or "unused" warning for س (though it is used here)
        // Let's test unused param
        val sourceUnused = """
            إجراء تجربة(س: رقم) ابدأ
                رجع
            انتهى
        """.trimIndent()
        val diagsUnused = analyzer.analyze(sourceUnused)
        assertTrue(diagsUnused.none { it.severity == Severity.Warning }, "Should have no warnings for parameters")
    }
}
