package rtlide.core.document

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/** Logical caret: a line index + a character offset (column) inside that line.
 *  Everything in the editor is expressed in LOGICAL coordinates; visual/Bidi
 *  geometry is derived from the text layout, never stored here. */
data class Caret(val line: Int, val col: Int)

/**
 * Represents a transformation to the document.
 */
data class TextEdit(
    val start: Caret,
    val end: Caret,
    val newText: String
)

/**
 * Minimal line-based document backed by Compose snapshot state, so any mutation
 * recomposes the editor. This is deliberately simple; the public surface
 * (lines / caret / insert / backspace / moveCaret) is what the rest of the
 * project depends on, so it can be swapped for a piece-table / rope later
 * without touching the UI.
 */
@Stable
class Document(initial: String = "") {

    private data class DocumentState(val lines: List<String>, val caret: Caret)
    private val undoStack = mutableListOf<DocumentState>()
    private val redoStack = mutableListOf<DocumentState>()

    var lines by mutableStateOf(splitLines(initial))
        private set

    var caret by mutableStateOf(Caret(0, 0))

    var selectionAnchor by mutableStateOf<Caret?>(null)

    fun lineText(index: Int): String = lines.getOrElse(index) { "" }

    fun text(): String = lines.joinToString("\n")

    private fun pushUndo() {
        undoStack.add(DocumentState(lines, caret))
        if (undoStack.size > 100) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = DocumentState(lines, caret)
            redoStack.add(currentState)
            val prevState = undoStack.removeAt(undoStack.size - 1)
            lines = prevState.lines
            caret = prevState.caret
            selectionAnchor = null
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = DocumentState(lines, caret)
            undoStack.add(currentState)
            val nextState = redoStack.removeAt(redoStack.size - 1)
            lines = nextState.lines
            caret = nextState.caret
            selectionAnchor = null
        }
    }

    /** Insert [textToInsert] at the caret. Handles embedded newlines. 
     *  If there is a selection, it is replaced by [textToInsert]. */
    fun insert(textToInsert: String) {
        if (textToInsert.isEmpty() && !hasSelection) return
        pushUndo()
        if (hasSelection) deleteSelectionInternal()
        if (textToInsert.isEmpty()) return

        val (l, c) = caret
        val current = lineText(l)
        val safeCol = c.coerceIn(0, current.length)
        val next = lines.toMutableList()
        if ('\n' in textToInsert) {
            val merged = current.substring(0, safeCol) + textToInsert + current.substring(safeCol)
            val pieces = splitLines(merged)
            next.removeAt(l)
            next.addAll(l, pieces)
            lines = next
            val lastPiece = pieces.last()
            val tailLen = current.length - safeCol
            caret = Caret(l + pieces.size - 1, (lastPiece.length - tailLen).coerceAtLeast(0))
        } else {
            next[l] = current.substring(0, safeCol) + textToInsert + current.substring(safeCol)
            lines = next
            caret = Caret(l, safeCol + textToInsert.length)
        }
        selectionAnchor = null
    }

    /** Delete the character before the caret, merging lines at column 0. */
    fun backspace() {
        if (hasSelection) {
            pushUndo()
            deleteSelectionInternal()
            return
        }
        val (l, c) = caret
        val current = lineText(l)
        if (l == 0 && c == 0) return
        pushUndo()
        val next = lines.toMutableList()
        when {
            c > 0 -> {
                val safeCol = c.coerceIn(1, current.length)
                next[l] = current.removeRange(safeCol - 1, safeCol)
                lines = next
                caret = Caret(l, safeCol - 1)
            }
            l > 0 -> {
                val prev = lineText(l - 1)
                next[l - 1] = prev + current
                next.removeAt(l)
                lines = next
                caret = Caret(l - 1, prev.length)
            }
        }
        selectionAnchor = null
    }

    fun deleteForward() {
        if (hasSelection) {
            pushUndo()
            deleteSelectionInternal()
            return
        }
        val (l, c) = caret
        val current = lineText(l)
        if (l == lines.size - 1 && c == current.length) return
        pushUndo()
        val next = lines.toMutableList()
        when {
            c < current.length -> {
                next[l] = current.removeRange(c, c + 1)
                lines = next
            }
            l < lines.size - 1 -> {
                val nextLine = lineText(l + 1)
                next[l] = current + nextLine
                next.removeAt(l + 1)
                lines = next
            }
        }
        selectionAnchor = null
    }

    fun moveCaret(deltaLine: Int, deltaCol: Int, extendSelection: Boolean) {
        if (extendSelection && selectionAnchor == null) selectionAnchor = caret
        if (!extendSelection) selectionAnchor = null
        val line = (caret.line + deltaLine).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        val col = (caret.col + deltaCol).coerceIn(0, lineText(line).length)
        caret = Caret(line, col)
    }

    fun moveCaretToLineStart(extendSelection: Boolean) {
        if (extendSelection && selectionAnchor == null) selectionAnchor = caret
        if (!extendSelection) selectionAnchor = null
        val current = lineText(caret.line)
        val firstNonWhitespace = current.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        val targetCol = if (caret.col == firstNonWhitespace && caret.col != 0) 0 else firstNonWhitespace
        caret = Caret(caret.line, targetCol)
    }

    fun moveCaretToLineEnd(extendSelection: Boolean) {
        if (extendSelection && selectionAnchor == null) selectionAnchor = caret
        if (!extendSelection) selectionAnchor = null
        caret = Caret(caret.line, lineText(caret.line).length)
    }

    fun moveCaretByWord(delta: Int, extendSelection: Boolean) {
        if (extendSelection && selectionAnchor == null) selectionAnchor = caret
        if (!extendSelection) selectionAnchor = null
        val (l, c) = caret
        val current = lineText(l)
        
        var newCol = c
        if (delta > 0) {
            // Forward
            if (c == current.length) {
                if (l < lines.size - 1) {
                    caret = Caret(l + 1, 0)
                }
            } else {
                while (newCol < current.length && !current[newCol].isLetterOrDigit()) newCol++
                while (newCol < current.length && current[newCol].isLetterOrDigit()) newCol++
                caret = Caret(l, newCol)
            }
        } else {
            // Backward
            if (c == 0) {
                if (l > 0) {
                    caret = Caret(l - 1, lineText(l - 1).length)
                }
            } else {
                while (newCol > 0 && !current[newCol - 1].isLetterOrDigit()) newCol--
                while (newCol > 0 && current[newCol - 1].isLetterOrDigit()) newCol--
                caret = Caret(l, newCol)
            }
        }
    }

    fun deleteByWord(delta: Int) {
        if (hasSelection) {
            deleteSelection()
            return
        }
        moveCaretByWord(delta, true)
        deleteSelection()
    }

    val hasSelection: Boolean get() = selectionAnchor != null && selectionAnchor != caret

    fun getSelectionRange(): Pair<Caret, Caret>? {
        val anchor = selectionAnchor ?: return null
        if (anchor == caret) return null
        return if (anchor.line < caret.line || (anchor.line == caret.line && anchor.col < caret.col)) {
            anchor to caret
        } else {
            caret to anchor
        }
    }

    fun getSelectedText(): String? {
        val (start, end) = getSelectionRange() ?: return null
        return if (start.line == end.line) {
            lineText(start.line).substring(start.col, end.col)
        } else {
            val sb = StringBuilder()
            sb.append(lineText(start.line).substring(start.col)).append("\n")
            for (i in (start.line + 1) until end.line) {
                sb.append(lineText(i)).append("\n")
            }
            sb.append(lineText(end.line).substring(0, end.col))
            sb.toString()
        }
    }

    fun deleteSelection() {
        if (hasSelection) {
            pushUndo()
            deleteSelectionInternal()
        }
    }

    private fun deleteSelectionInternal() {
        val (start, end) = getSelectionRange() ?: return
        val next = lines.toMutableList()
        
        val startLineText = lineText(start.line)
        val endLineText = lineText(end.line)
        
        val safeStartCol = start.col.coerceIn(0, startLineText.length)
        val safeEndCol = end.col.coerceIn(0, endLineText.length)
        
        val mergedLine = startLineText.substring(0, safeStartCol) + endLineText.substring(safeEndCol)
        
        for (i in end.line downTo start.line + 1) {
            if (i < next.size) {
                next.removeAt(i)
            }
        }
        
        if (start.line < next.size) {
            next[start.line] = mergedLine
        }
        
        lines = next
        caret = Caret(start.line, safeStartCol)
        selectionAnchor = null
    }

    fun copySelection() {
        val text = getSelectedText() ?: return
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }

    fun cutSelection() {
        copySelection()
        deleteSelection()
    }

    fun paste() {
        val contents = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)
        val text = contents?.getTransferData(DataFlavor.stringFlavor) as? String ?: return
        insert(text)
    }

    fun selectAll() {
        selectionAnchor = Caret(0, 0)
        caret = Caret((lines.size - 1).coerceAtLeast(0), lineText((lines.size - 1).coerceAtLeast(0)).length)
    }

    fun selectWordAt(c: Caret) {
        val line = lineText(c.line)
        if (line.isEmpty()) return
        val col = c.col.coerceIn(0, line.length)
        
        var start = col
        while (start > 0 && (line[start - 1].isLetterOrDigit() || line[start - 1] == '_')) start--
        
        var end = col
        while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) end++
        
        if (start < end) {
            selectionAnchor = Caret(c.line, start)
            caret = Caret(c.line, end)
        }
    }

    fun selectLineAt(lineIndex: Int) {
        val line = lineText(lineIndex)
        selectionAnchor = Caret(lineIndex, 0)
        caret = Caret(lineIndex, line.length)
    }

    fun setText(newText: String) {
        lines = splitLines(newText)
        caret = Caret(0, 0)
        selectionAnchor = null
        undoStack.clear()
        redoStack.clear()
    }

    /** Applies a sequence of edits. Assumes they are non-overlapping or sorted. */
    fun applyEdits(edits: List<TextEdit>) {
        if (edits.isEmpty()) return
        pushUndo()
        // Sort edits backwards to maintain offset validity
        val sorted = edits.sortedByDescending { it.start.line * 10000 + it.start.col }
        
        for (edit in sorted) {
            selectionAnchor = edit.start
            caret = edit.end
            deleteSelectionInternal()
            // We need a non-pushing insert
            insertAtCaretInternal(edit.newText)
        }
        selectionAnchor = null
    }

    private fun insertAtCaretInternal(textToInsert: String) {
        if (textToInsert.isEmpty()) return

        val (l, c) = caret
        val current = lineText(l)
        val safeCol = c.coerceIn(0, current.length)
        val next = lines.toMutableList()
        if ('\n' in textToInsert) {
            val merged = current.substring(0, safeCol) + textToInsert + current.substring(safeCol)
            val pieces = splitLines(merged)
            next.removeAt(l)
            next.addAll(l, pieces)
            lines = next
            val lastPiece = pieces.last()
            val tailLen = current.length - safeCol
            caret = Caret(l + pieces.size - 1, (lastPiece.length - tailLen).coerceAtLeast(0))
        } else {
            next[l] = current.substring(0, safeCol) + textToInsert + current.substring(safeCol)
            lines = next
            caret = Caret(l, safeCol + textToInsert.length)
        }
    }

    /** Replace the entire document content, preserving undo history. */
    fun replaceFullText(newText: String) {
        pushUndo()
        lines = splitLines(newText)
        caret = Caret(0, 0)
        selectionAnchor = null
    }

    private fun splitLines(s: String): List<String> =
        if (s.isEmpty()) listOf("") else s.split('\n')
}
