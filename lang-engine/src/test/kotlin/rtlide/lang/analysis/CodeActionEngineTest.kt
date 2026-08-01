package rtlide.lang.analysis

import org.junit.Test
import rtlide.core.document.Document
import kotlin.test.assertEquals

class CodeActionEngineTest {
    private val engine = CodeActionEngine()

    @Test
    fun testChangeToVar() {
        val source = "ألزم س = 10"
        val location = Location(1, 6) // Points to 'س'
        val diagnostic = Diagnostic("...", location, Severity.Warning, 1, listOf(QuickFix("استخدام 'ليكن'", "CHANGE_TO_VAR")))
        
        val actions = engine.getActionsForDiagnostic(diagnostic, source)
        assertEquals(1, actions.size)
        val action = actions[0]
        
        val doc = Document(source)
        doc.applyEdits(action.edits)
        assertEquals("ليكن س = 10", doc.text())
    }

    @Test
    fun testRemoveType() {
        val source = "ليكن س: رقم = 10"
        val location = Location(1, 6) // 'س'
        val diagnostic = Diagnostic("...", location, Severity.Hint, 1, listOf(QuickFix("إزالة النوع", "REMOVE_TYPE")))
        
        val actions = engine.getActionsForDiagnostic(diagnostic, source)
        assertEquals(1, actions.size)
        
        val doc = Document(source)
        doc.applyEdits(actions[0].edits)
        assertEquals("ليكن س = 10", doc.text())
    }
    
    @Test
    fun testSafeDeleteVar() {
        val source = "ليكن س = 10\nأكتب(20)"
        val location = Location(1, 6) 
        val diagnostic = Diagnostic("...", location, Severity.Warning, 1, listOf(
            QuickFix("حذف آمن", "SAFE_DELETE_VAR", -5, 0, 11)
        ))
        
        val actions = engine.getActionsForDiagnostic(diagnostic, source)
        val doc = Document(source)
        doc.applyEdits(actions[0].edits)
        assertEquals("\nأكتب(20)", doc.text())
    }

    @Test
    fun testSafeDeleteParam() {
        val source = "إجراء المطلع(وسائط: قائمة(نص)): عدم ابدأ"
        val location = Location(1, 14) // 'وسائط'
        // 'وسائط: قائمة(نص)' length: 5 + 2 + 9 = 16
        val diagnostic = Diagnostic("...", location, Severity.Warning, 5, listOf(
            QuickFix("حذف آمن", "SAFE_DELETE_PARAM", 0, 0, 16)
        ))
        
        val actions = engine.getActionsForDiagnostic(diagnostic, source)
        assertEquals(1, actions.size)
        
        val doc = Document(source)
        doc.applyEdits(actions[0].edits)
        assertEquals("إجراء المطلع(): عدم ابدأ", doc.text())
    }

    @Test
    fun testSafeDeleteFunction() {
        val source = "إجراء س() ابدأ انتهى\nأكتب(1)"
        val location = Location(1, 6) // 'س'
        // 'إجراء س() ابدأ انتهى'
        // start (1, 1), name (1, 6), end (1, 21)
        // startColOffset = 1 - 6 = -5
        // endLineOffset = 1 - 1 = 0
        // endColOffset = 21 - 1 = 20
        val diagnostic = Diagnostic("...", location, Severity.Warning, 1, listOf(
            QuickFix("حذف آمن", "SAFE_DELETE_FUNCTION", -5, 0, 20)
        ))

        val actions = engine.getActionsForDiagnostic(diagnostic, source)
        assertEquals(1, actions.size)

        val doc = Document(source)
        doc.applyEdits(actions[0].edits)
        assertEquals("\nأكتب(1)", doc.text())
    }

    @Test
    fun testSafeDeleteStruct() {
        val source = "بنية س ابدأ انتهى\nأكتب(1)"
        val location = Location(1, 6) // 'س'
        val diagnostic = Diagnostic("...", location, Severity.Warning, 1, listOf(
            QuickFix("حذف آمن", "SAFE_DELETE_STRUCT", -5, 0, 17)
        ))

        val actions = engine.getActionsForDiagnostic(diagnostic, source)
        val doc = Document(source)
        doc.applyEdits(actions[0].edits)
        assertEquals("\nأكتب(1)", doc.text())
    }

    @Test
    fun testSafeDeleteField() {
        val source = "بنية س ابدأ\n    حقل: رقم\nانتهى"
        val location = Location(2, 5) // 'حقل'
        val diagnostic = Diagnostic("...", location, Severity.Warning, 3, listOf(
            QuickFix("حذف آمن", "SAFE_DELETE_FIELD", 0, 0, 8)
        ))

        val lineText = source.split('\n')[1]
        val actions = engine.getActionsForDiagnostic(diagnostic, lineText)
        val doc = Document(source)
        doc.applyEdits(actions[0].edits)
        assertEquals("بنية س ابدأ\nانتهى", doc.text())
    }
}
