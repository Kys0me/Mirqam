package rtlide.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import rtlide.core.project.Project
import rtlide.core.project.ProjectState
import rtlide.core.project.ProjectStateStore
import rtlide.editor.EditorState
import rtlide.terminal.pty.ShellProcessBackend
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class ShellViewModel(val scope: CoroutineScope) {
    var currentProject by mutableStateOf<Project?>(null)
        private set

    val editorState = EditorState(scope)
    val terminalBackend = ShellProcessBackend(scope)

    private var autoSaveJobs = mutableMapOf<String, Job>()

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
        
        terminalBackend.start()
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
