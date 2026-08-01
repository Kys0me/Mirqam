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
import rtlide.editor.analysis.AnalysisPipeline
import rtlide.lang.SakhrLang
import rtlide.lang.analysis.Diagnostic
import rtlide.lang.analysis.Location
import rtlide.lang.analysis.QuickFix
import rtlide.lang.analysis.Severity
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

    private var analysisJob: Job? = null

    fun requestAnalysis(scope: CoroutineScope) {
        analysisJob?.cancel()
        analysisJob = scope.launch {
            delay(500.milliseconds)
            val text = document.text()
            val result = IdeServices.analyzer.analyze(text, file)
            diagnostics = result.diagnostics
            symbols = result.symbols
            typeAtLocation = result.typeAtLocation
            structFields = result.structFields
            completionModel = result.completion ?: completionModel
        }
    }

    fun applyQuickFix(fix: QuickFix, location: Location, length: Int) {
        val lineIndex = location.line - 1
        if (lineIndex !in document.lines.indices) return
        val lineText = document.lineText(lineIndex)
        
        // Try new engine first
        val actions = IdeServices.codeActionEngine.getActionsForDiagnostic(
            Diagnostic("", location, Severity.Hint, length, listOf(fix)),
            lineText
        )
        val action = actions.firstOrNull()
        if (action != null && action.edits.isNotEmpty()) {
            document.applyEdits(action.edits)
            return
        }

        val replacement = fix.label // Was replacement, but label is used in CodeActionEngine for title
        // Fallback for interactive fixes
        val colIndex = (location.column - 1).coerceIn(0, lineText.length)
        
        when {
            fix.replacement == "ADD_INITIALIZER" -> {
                if (quickFixInput == null) {
                    activePendingFix = fix to location
                    activePendingFixLength = length
                    quickFixInput = "" 
                    return
                }
                document.caret = rtlide.core.document.Caret(lineIndex, (colIndex + length).coerceIn(0, lineText.length))
                document.insert(" = $quickFixInput")
                quickFixInput = null
                activePendingFix = null
                activePendingFixLength = 0
            }
            fix.replacement.startsWith("CREATE_VAR:") -> {
                val varName = fix.replacement.removePrefix("CREATE_VAR:")
                var insertLine = 0
                var indent = ""
                for (i in lineIndex downTo 0) {
                    val l = document.lineText(i)
                    if (l.contains("ابدأ")) {
                        insertLine = i + 1
                        val match = Regex("^\\s*").find(l)
                        indent = (match?.value ?: "") + "    "
                        break
                    }
                }
                document.caret = rtlide.core.document.Caret(insertLine, 0)
                document.insert("${indent}ليكن $varName\n")
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

        scope?.let { AnalysisPipeline.attach(newTab, it) }
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
