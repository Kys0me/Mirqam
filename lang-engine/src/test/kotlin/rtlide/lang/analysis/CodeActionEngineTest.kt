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
}
