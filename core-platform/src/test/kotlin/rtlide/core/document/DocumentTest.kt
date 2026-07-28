package rtlide.core.document

import org.junit.Test
import kotlin.test.assertEquals

class DocumentTest {

    @Test
    fun testSafeDeletionOutOfBounds() {
        val doc = Document("مرحبا بكم") // Length 9
        doc.selectionAnchor = Caret(0, 0)
        doc.caret = Caret(0, 100) // Out of bounds
        
        doc.insert("") // Should not crash
        assertEquals("", doc.text())
        assertEquals(Caret(0, 0), doc.caret)
    }

    @Test
    fun testSafeDeletionNegative() {
        val doc = Document("مرحبا")
        doc.selectionAnchor = Caret(0, -10)
        doc.caret = Caret(0, 10)
        
        doc.insert("") // Should not crash
        assertEquals("", doc.text())
    }

    @Test
    fun testSafeDeletionInvalidRange() {
        val doc = Document("مرحبا") // Length 5
        // Range [6, 5) which was reported in the crash
        doc.selectionAnchor = Caret(0, 6)
        doc.caret = Caret(0, 5)
        
        doc.insert("") // Should not crash and handle logically
        assertEquals("مرحبا", doc.text()) // Since range is effectively reversed/invalid but clamped to length
    }
}
