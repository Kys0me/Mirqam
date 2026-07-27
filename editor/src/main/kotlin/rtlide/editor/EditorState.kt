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
)

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
        closeTab(activeTabIndex)
    }
}
