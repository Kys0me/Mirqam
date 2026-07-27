package rtlide.editor

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
    var hoveredDiagnostic by mutableStateOf<Diagnostic?>(null)
    var instantTooltip by mutableStateOf(false)
    
    var quickFixInput by mutableStateOf<String?>(null)
    var activePendingFix by mutableStateOf<Pair<QuickFix, Location>?>(null)
    var activePendingFixLength by mutableStateOf(0)

    fun applyQuickFix(fix: QuickFix, location: Location, length: Int) {
        val replacement = fix.replacement
        val lineIndex = location.line - 1
        val colIndex = location.column - 1
        val line = document.lineText(lineIndex)
        
        when {
            replacement == "CHANGE_TO_VAR" -> {
                // Find 'ألزم' before the variable
                val startOfLine = line.substring(0, colIndex)
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
                document.caret = rtlide.core.document.Caret(lineIndex, colIndex + length)
                document.insert(" = $quickFixInput")
                quickFixInput = null
                activePendingFix = null
                activePendingFixLength = 0
            }
            replacement == "ألزم" -> {
                // Find 'ليكن' before the variable
                val startOfLine = line.substring(0, colIndex)
                val letIndex = startOfLine.lastIndexOf("ليكن")
                if (letIndex != -1) {
                    document.caret = rtlide.core.document.Caret(lineIndex, letIndex + 4)
                    document.selectionAnchor = rtlide.core.document.Caret(lineIndex, letIndex)
                    document.insert("ألزم")
                }
            }
            replacement.startsWith("ADD_TYPE:") -> {
                val typeName = replacement.removePrefix("ADD_TYPE:")
                document.caret = rtlide.core.document.Caret(lineIndex, colIndex + length)
                document.insert(": $typeName")
            }
            replacement == "REMOVE_TYPE" -> {
                // Find ': Type' after the identifier
                val afterId = line.substring(colIndex + length)
                val colonIndex = afterId.indexOf(':')
                if (colonIndex != -1) {
                    val remaining = afterId.substring(colonIndex + 1).trimStart()
                    // Find the end of the type name (letters and spaces for 'قائمة رقم')
                    var typeEnd = 0
                    while (typeEnd < remaining.length && (remaining[typeEnd].isLetter() || remaining[typeEnd].isWhitespace())) {
                        typeEnd++
                    }
                    val totalToRemove = (afterId.length - (afterId.substring(colonIndex + 1 + typeEnd).length))
                    
                    document.caret = rtlide.core.document.Caret(lineIndex, colIndex + length + totalToRemove)
                    document.selectionAnchor = rtlide.core.document.Caret(lineIndex, colIndex + length)
                    document.insert("")
                }
            }
            replacement.startsWith("ADD_RETURN_TYPE:") -> {
                val typeName = replacement.removePrefix("ADD_RETURN_TYPE:")
                // Find ')' after the function name
                val afterFn = line.substring(colIndex)
                val closingParen = afterFn.indexOf(')')
                if (closingParen != -1) {
                    document.caret = rtlide.core.document.Caret(lineIndex, colIndex + closingParen + 1)
                    document.insert(": $typeName")
                }
            }
            replacement == "REMOVE_RETURN_TYPE" -> {
                // Find ': Type' after ')'
                val afterFn = line.substring(colIndex)
                val closingParen = afterFn.indexOf(')')
                if (closingParen != -1) {
                    val afterParen = afterFn.substring(closingParen + 1)
                    val colonIndex = afterParen.indexOf(':')
                    if (colonIndex != -1) {
                         val remaining = afterParen.substring(colonIndex + 1).trimStart()
                         var typeEnd = 0
                         while (typeEnd < remaining.length && (remaining[typeEnd].isLetter() || remaining[typeEnd].isWhitespace())) {
                             typeEnd++
                         }
                         val totalToRemove = (afterParen.length - (afterParen.substring(colonIndex + 1 + typeEnd).length))
                         document.caret = rtlide.core.document.Caret(lineIndex, colIndex + closingParen + 1 + totalToRemove)
                         document.selectionAnchor = rtlide.core.document.Caret(lineIndex, colIndex + closingParen + 1)
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
                        // Heuristic for indentation: look at the line with "ابدأ" or the line after it
                        val match = Regex("^\\s*").find(l)
                        indent = (match?.value ?: "") + "    "
                        break
                    }
                }
                
                val newCaret = rtlide.core.document.Caret(insertLine, 0)
                document.caret = newCaret
                document.insert("${indent}ليكن $varName\n")
            }
            else -> {
                // Suggestion replacement
                document.caret = rtlide.core.document.Caret(lineIndex, colIndex + length)
                document.selectionAnchor = rtlide.core.document.Caret(lineIndex, colIndex)
                document.insert(replacement)
            }
        }
    }

    fun showIntentionActions() {
        val currentCaret = document.caret
        val diag = diagnostics.find { d ->
            d.location.line - 1 == currentCaret.line &&
            currentCaret.col in (d.location.column - 1)..(d.location.column - 1 + d.length)
        }
        if (diag != null) {
            hoveredDiagnostic = diag
            instantTooltip = true
        }
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
