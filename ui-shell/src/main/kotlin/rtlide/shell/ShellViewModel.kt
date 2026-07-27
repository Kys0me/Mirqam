package rtlide.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import rtlide.core.project.Project
import rtlide.core.project.ProjectState
import rtlide.core.project.ProjectStateStore
import rtlide.editor.EditorState
import rtlide.lang.analysis.SakhrAnalyzer
import rtlide.terminal.pty.Pty4jBackend
import rtlide.terminal.pty.TerminalBackend
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class RunTab(
    val name: String, 
    val backend: TerminalBackend,
    var lastCommand: Array<String>? = null
)

class ShellViewModel(val scope: CoroutineScope) {
    var currentProject by mutableStateOf<Project?>(null)
        private set

    val editorState = EditorState(scope)
    
    val runTabs = mutableStateListOf<RunTab>()
    var activeRunTabIndex by mutableStateOf(0)

    val activeRunTab: RunTab? get() = runTabs.getOrNull(activeRunTabIndex)

    private val sakhrAnalyzer = SakhrAnalyzer()

    init {
        // Persist UI state when tabs change or active tab changes
        scope.launch {
            snapshotFlow { 
                editorState.tabs.map { it.file.absolutePath } to editorState.activeTabIndex 
            }.collect {
                saveProjectState()
            }
        }
    }

    fun openProject(project: Project) {
        currentProject = project
        val state = ProjectStateStore.load(project)
        
        editorState.tabs.clear()
        state.openFiles.forEach { path ->
            val file = File(path)
            if (file.exists()) {
                editorState.openFile(file)
                observeDocument(file)
            }
        }
        
        state.activeFile?.let { path ->
            val index = editorState.tabs.indexOfFirst { it.file.absolutePath == path }
            if (index != -1) {
                editorState.activeTabIndex = index
            }
        }
        
        if (runTabs.isEmpty()) {
            addRunTab("تشغيل 1")
        }
    }

    fun addRunTab(name: String = "تشغيل جديد"): RunTab {
        val backend = Pty4jBackend(scope, currentProject?.path ?: System.getProperty("user.home"))
        val tab = RunTab(name, backend)
        runTabs.add(tab)
        activeRunTabIndex = runTabs.lastIndex
        return tab
    }

    fun closeRunTab(index: Int) {
        if (index in runTabs.indices) {
            runTabs[index].backend.close()
            runTabs.removeAt(index)
            if (activeRunTabIndex >= runTabs.size) {
                activeRunTabIndex = (runTabs.size - 1).coerceAtLeast(0)
            }
        }
    }

    fun runCommand(cmd: Array<String>, tabName: String = "تشغيل") {
        scope.launch {
            var tab = runTabs.find { it.name == tabName }
            if (tab == null) {
                tab = addRunTab(tabName)
            } else {
                activeRunTabIndex = runTabs.indexOf(tab)
            }
            
            // Clear the console before running
            tab.backend.clear()
            
            tab.lastCommand = cmd
            tab.backend.start(cmd)
        }
    }

    fun stopProcess(tab: RunTab) {
        tab.backend.close()
    }

    fun rerunProcess(tab: RunTab) {
        val cmd = tab.lastCommand ?: return
        runCommand(cmd, tab.name)
    }

    fun openFile(file: File) {
        val isNew = editorState.tabs.none { it.file.absolutePath == file.absolutePath }
        editorState.openFile(file)
        if (isNew) {
            observeDocument(file)
        }
        saveProjectState()
    }

    private fun observeDocument(file: File) {
        val tab = editorState.tabs.find { it.file.absolutePath == file.absolutePath } ?: return
        scope.launch {
            snapshotFlow { tab.document.text() }.collectLatest { content ->
                // Run analysis if it's a Sakhr file
                if (file.extension.lowercase() in listOf("صخر", "sakhr")) {
                    val result = sakhrAnalyzer.analyze(content)
                    tab.diagnostics = result.diagnostics
                    tab.symbols = result.symbols
                } else {
                    tab.diagnostics = emptyList()
                    tab.symbols = emptyList()
                }
                
                // Delay auto-save to debounce typing
                delay(2000.milliseconds)
                saveFile(file, content)
            }
        }
    }

    private fun saveFile(file: File, content: String) {
        try {
            file.writeText(content)
            println("Smart saved: ${file.name}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveProjectState() {
        val project = currentProject ?: return
        val state = ProjectState(
            openFiles = editorState.tabs.map { it.file.absolutePath },
            activeFile = editorState.activeTab?.file?.absolutePath
        )
        ProjectStateStore.save(project, state)
    }
}
