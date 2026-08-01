package rtlide.editor

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rtlide.core.document.Document
import rtlide.lang.SakhrLang
import rtlide.lang.analysis.Diagnostic
import rtlide.lang.analysis.Location
import rtlide.lang.analysis.QuickFix
import rtlide.lang.highlight.Highlighter
import rtlide.lang.indent.reformat
import rtlide.lang.intelligence.CompletionModel
import rtlide.lang.sakhr.SakhrType
import rtlide.lang.schema.LanguageDefinition
import rtlide.lang.tokenizer.Tokenizer
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class EditorTab(
    val file: File,
    val document: Document,
    val lang: LanguageDefinition,
    val highlighter: Highlighter
) {
    var diagnostics by mutableStateOf<List<Diagnostic>>(emptyList())
    var symbols by mutableStateOf<List<String>>(emptyList())
    var typeAtLocation by mutableStateOf<Map<Location, SakhrType>>(emptyMap())
    var structFields by mutableStateOf<Map<String, List<String>>>(emptyMap())
    var completionModel by mutableStateOf(CompletionModel())
    var hoveredDiagnostics by mutableStateOf<List<Diagnostic>>(emptyList())
    var instantTooltip by mutableStateOf(false)
    
    var lastSavedLines by mutableStateOf(document.lines)
    val isDirty by derivedStateOf { document.lines != lastSavedLines }
    
    var quickFixInput by mutableStateOf<String?>(null)
    var activePendingFix by mutableStateOf<Pair<QuickFix, Location>?>(null)
    var activePendingFixLength by mutableStateOf(0)

    fun applyQuickFix(fix: QuickFix, location: Location, length: Int) {
        val replacement = fix.replacement
        val lineIndex = location.line - 1
        if (lineIndex !in document.lines.indices) return
        
        val lineText = document.lineText(lineIndex)
        val colIndex = (location.column - 1).coerceIn(0, lineText.length)
        
        val startCaret = rtlide.core.document.Caret(lineIndex, (colIndex + fix.startColOffset).coerceIn(0, lineText.length))
        val endLine = (lineIndex + fix.endLineOffset).coerceIn(0, document.lines.size - 1)
        val endCol = if (fix.endColOffset != null) {
            val offset: Int = fix.endColOffset!!
            // If it's the same line, it's relative to start. If multi-line, it's absolute (1-based from analyzer).
            val baseEndCol = if (fix.endLineOffset == 0) startCaret.col + offset else (offset - 1)
            baseEndCol.coerceIn(0, document.lineText(endLine).length)
        } else {
            (colIndex + length).coerceIn(0, document.lineText(endLine).length)
        }
        val endCaret = rtlide.core.document.Caret(endLine, endCol)

        when {
            replacement == "CHANGE_TO_VAR" -> {
                // Find 'ألزم' before the variable
                val startOfLine = lineText.substring(0, colIndex)
                val constIndex = startOfLine.lastIndexOf("ألزم")
                if (constIndex != -1) {
                    document.caret = rtlide.core.document.Caret(lineIndex, constIndex + 4)
                    document.selectionAnchor = rtlide.core.document.Caret(lineIndex, constIndex)
                    document.insert("ليكن")
                }
            }
            replacement == "ADD_INITIALIZER" -> {
                if (quickFixInput == null) {
                    activePendingFix = fix to location
                    activePendingFixLength = length
                    quickFixInput = "" // Initialize to empty string to keep dialog open
                    return
                }
                document.caret = rtlide.core.document.Caret(lineIndex, (colIndex + length).coerceIn(0, lineText.length))
                document.insert(" = $quickFixInput")
                quickFixInput = null
                activePendingFix = null
                activePendingFixLength = 0
            }
            replacement == "ألزم" -> {
                // Find 'ليكن' before the variable
                val startOfLine = lineText.substring(0, colIndex)
                val letIndex = startOfLine.lastIndexOf("ليكن")
                if (letIndex != -1) {
                    document.caret = rtlide.core.document.Caret(lineIndex, letIndex + 4)
                    document.selectionAnchor = rtlide.core.document.Caret(lineIndex, letIndex)
                    document.insert("ألزم")
                }
            }
            replacement.startsWith("ADD_TYPE:") -> {
                val typeName = replacement.removePrefix("ADD_TYPE:")
                document.caret = rtlide.core.document.Caret(lineIndex, (colIndex + length).coerceIn(0, lineText.length))
                document.insert(": $typeName")
            }
            replacement == "REMOVE_TYPE" -> {
                val afterId = if (colIndex + length < lineText.length) lineText.substring(colIndex + length) else ""
                val colonIndex = afterId.indexOf(':')
                if (colonIndex != -1) {
                    val fromColon = afterId.substring(colonIndex)
                    var typeEnd = 1 // Skip colon
                    while (typeEnd < fromColon.length && (fromColon[typeEnd].isLetter() || fromColon[typeEnd].isWhitespace())) {
                        typeEnd++
                    }
                    
                    document.caret = rtlide.core.document.Caret(lineIndex, (colIndex + length + colonIndex + typeEnd).coerceAtMost(lineText.length))
                    document.selectionAnchor = rtlide.core.document.Caret(lineIndex, (colIndex + length).coerceAtMost(lineText.length))
                    document.insert("")
                }
            }
            replacement.startsWith("ADD_RETURN_TYPE:") -> {
                val typeName = replacement.removePrefix("ADD_RETURN_TYPE:")
                // Find ')' after the function name
                val afterFn = lineText.substring(colIndex)
                val closingParen = afterFn.indexOf(')')
                if (closingParen != -1) {
                    document.caret = rtlide.core.document.Caret(lineIndex, (colIndex + closingParen + 1).coerceAtMost(lineText.length))
                    document.insert(": $typeName")
                }
            }
            replacement == "REMOVE_RETURN_TYPE" -> {
                // Find ': Type' after ')'
                val afterFn = lineText.substring(colIndex)
                val closingParen = afterFn.indexOf(')')
                if (closingParen != -1) {
                    val afterParen = afterFn.substring(closingParen + 1)
                    val colonIndex = afterParen.indexOf(':')
                    if (colonIndex != -1) {
                         val fromColon = afterParen.substring(colonIndex)
                         var typeEnd = 1
                         while (typeEnd < fromColon.length && (fromColon[typeEnd].isLetter() || fromColon[typeEnd].isWhitespace())) {
                             typeEnd++
                         }
                         document.caret = rtlide.core.document.Caret(lineIndex, (colIndex + closingParen + 1 + colonIndex + typeEnd).coerceAtMost(lineText.length))
                         document.selectionAnchor = rtlide.core.document.Caret(lineIndex, (colIndex + closingParen + 1).coerceAtMost(lineText.length))
                         document.insert("")
                    }
                }
            }
            replacement.startsWith("CREATE_VAR:") -> {
                val varName = replacement.removePrefix("CREATE_VAR:")
                // Smart search for scope start (nearest "ابدأ" upwards or start of file)
                var insertLine = 0
                var indent = ""
                for (i in lineIndex downTo 0) {
                    val l = document.lineText(i)
                    if (l.contains("ابدأ")) {
                        insertLine = i + 1
                        // Heuristic for indentation
                        val match = Regex("^\\s*").find(l)
                        indent = (match?.value ?: "") + "    "
                        break
                    }
                }
                
                document.caret = rtlide.core.document.Caret(insertLine, 0)
                document.insert("${indent}ليكن $varName\n")
            }
            replacement == "SAFE_DELETE_VAR" || replacement == "SAFE_DELETE_FUNCTION" -> {
                // Perform smart deletion using the provided range
                document.caret = endCaret
                document.selectionAnchor = startCaret
                
                // If we are deleting a full line (or multiple lines), try to clean up the trailing newline
                if (startCaret.col == 0 && endCaret.line < document.lines.size - 1) {
                    val lastLineText = document.lineText(endCaret.line)
                    val trailingPart = if (endCaret.col < lastLineText.length) lastLineText.substring(endCaret.col) else ""
                    if (trailingPart.isBlank()) {
                        document.caret = rtlide.core.document.Caret(endCaret.line + 1, 0)
                    }
                }
                
                document.insert("")
            }
            replacement == "SAFE_DELETE_PARAM" -> {
                val startParen = lineText.lastIndexOf('(', colIndex)
                val endParen = lineText.indexOf(')', colIndex)
                if (startParen != -1 && endParen != -1 && endParen > startParen) {
                    val unitStart = startCaret.col
                    val unitEnd = endCaret.col
                    
                    if (unitStart > startParen && unitEnd <= endParen && unitEnd >= unitStart) {
                        var deleteStart = unitStart
                        var deleteEnd = unitEnd
                        
                        val beforeUnit = lineText.substring(startParen + 1, unitStart)
                        val afterUnit = lineText.substring(unitEnd, endParen)
                        
                        if (afterUnit.contains('،')) {
                            val commaPos = afterUnit.indexOf('،')
                            deleteEnd = unitEnd + commaPos + 1
                            while (deleteEnd < endParen && lineText[deleteEnd].isWhitespace()) deleteEnd++
                        } else if (beforeUnit.contains('،')) {
                            val commaPos = beforeUnit.lastIndexOf('،')
                            deleteStart = startParen + 1 + commaPos
                        } else {
                            deleteStart = (startParen + 1 + beforeUnit.takeWhile { it.isWhitespace() }.length).coerceAtMost(unitStart)
                            deleteEnd = (unitEnd + afterUnit.takeLastWhile { it.isWhitespace() }.length).coerceAtLeast(unitEnd)
                        }
                        
                        document.caret = rtlide.core.document.Caret(lineIndex, deleteEnd)
                        document.selectionAnchor = rtlide.core.document.Caret(lineIndex, deleteStart)
                        document.insert("")
                    }
                }
            }
            else -> {
                // Suggestion replacement
                document.caret = rtlide.core.document.Caret(lineIndex, (colIndex + length).coerceAtMost(lineText.length))
                document.selectionAnchor = rtlide.core.document.Caret(lineIndex, colIndex)
                document.insert(replacement)
            }
        }
    }

    fun showIntentionActions() {
        val currentCaret = document.caret
        val diags = diagnostics.filter { d ->
            d.location.line - 1 == currentCaret.line &&
            currentCaret.col in (d.location.column - 1)..(d.location.column - 1 + d.length)
        }
        if (diags.isNotEmpty()) {
            hoveredDiagnostics = diags
            instantTooltip = true
        }
    }

    fun reformat() {
        val oldText = document.text()
        val newText = reformat(
            oldText,
            lang.indent,
            lineComment = lang.grammar.lineComment,
            stringDelimiters = lang.grammar.strings.mapNotNull { it.begin.firstOrNull() }.ifEmpty { listOf('"') },
        )
        if (newText == oldText) return
        // Reformat preserves the line count, so keep the caret on its line.
        val caretBefore = document.caret
        document.replaceFullText(newText)
        val line = caretBefore.line.coerceIn(0, document.lines.lastIndex)
        val col = caretBefore.col.coerceIn(0, document.lineText(line).length)
        document.caret = rtlide.core.document.Caret(line, col)
    }
}

class EditorState(private val scope: CoroutineScope? = null) {
    val tabs = mutableStateListOf<EditorTab>()
    var activeTabIndex by mutableStateOf(-1)

    val activeTab: EditorTab? get() = tabs.getOrNull(activeTabIndex)

    var fontSize by mutableStateOf(15f)
    var showZoomPopup by mutableStateOf(false)
    private var zoomBaseFontSize by mutableStateOf(15f)
    private var zoomPopupJob: Job? = null

    companion object {
        const val MIN_FONT_SIZE = 8f
        const val MAX_FONT_SIZE = 72f
    }

    fun zoom(delta: Float) {
        if (!showZoomPopup) {
            zoomBaseFontSize = fontSize
        }
        fontSize = (fontSize - delta).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        showZoomPopup = true
        
        zoomPopupJob?.cancel()
        zoomPopupJob = scope?.launch {
            delay(3000.milliseconds)
            showZoomPopup = false
        }
    }

    fun undoZoom() {
        fontSize = zoomBaseFontSize
        showZoomPopup = false
        zoomPopupJob?.cancel()
    }

    fun openFile(file: File) {
        val existingIndex = tabs.indexOfFirst { it.file.absolutePath == file.absolutePath }
        if (existingIndex != -1) {
            activeTabIndex = existingIndex
            return
        }

        if (!file.exists() || file.isDirectory) return

        val content = try { file.readText() } catch (_: Exception) { "" }
        val doc = Document(content)
        
        // Currently only Sakhr is supported as first-class citizen
        val lang = SakhrLang.definition()
        val theme = SakhrLang.theme()
        val highlighter = Highlighter(Tokenizer(lang.grammar), theme)

        val newTab = EditorTab(file, doc, lang, highlighter)
        tabs.add(newTab)
        activeTabIndex = tabs.size - 1
    }

    fun closeTab(index: Int) {
        if (index in tabs.indices) {
            tabs.removeAt(index)
            if (activeTabIndex >= tabs.size) {
                activeTabIndex = tabs.size - 1
            }
        }
    }
    
    fun closeActiveTab() {
        if (activeTabIndex != -1) closeTab(activeTabIndex)
    }

    fun gotoNextProblem() {
        val tab = activeTab ?: return
        if (tab.diagnostics.isEmpty()) return
        
        val sorted = tab.diagnostics.sortedWith(compareBy({ it.location.line }, { it.location.column }))
        val currentCaret = tab.document.caret
        
        val next = sorted.find { it.location.line - 1 > currentCaret.line || 
                                (it.location.line - 1 == currentCaret.line && it.location.column - 1 > currentCaret.col) }
                ?: sorted.first()
        
        tab.document.caret = rtlide.core.document.Caret(next.location.line - 1, next.location.column - 1)
    }

    fun gotoPreviousProblem() {
        val tab = activeTab ?: return
        if (tab.diagnostics.isEmpty()) return
        
        val sorted = tab.diagnostics.sortedWith(compareBy({ it.location.line }, { it.location.column }))
        val currentCaret = tab.document.caret
        
        val prev = sorted.findLast { it.location.line - 1 < currentCaret.line || 
                                    (it.location.line - 1 == currentCaret.line && it.location.column - 1 < currentCaret.col) }
                ?: sorted.last()
        
        tab.document.caret = rtlide.core.document.Caret(prev.location.line - 1, prev.location.column - 1)
    }
}
