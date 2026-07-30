package rtlide.shell.toolwindow

import androidx.compose.runtime.mutableStateMapOf
import java.io.File

class ProjectTreeState(val root: File) {
    private val expandedPaths = mutableStateMapOf<String, Boolean>()
    
    init {
        // Expand root by default
        expandedPaths[root.absolutePath] = true
    }

    fun isExpanded(file: File): Boolean = expandedPaths[file.absolutePath] == true

    fun toggle(file: File) {
        val path = file.absolutePath
        expandedPaths[path] = !(expandedPaths[path] ?: false)
    }

    fun collapseAll() {
        expandedPaths.clear()
        expandedPaths[root.absolutePath] = true
    }
    
    fun expandAll() {
        root.walk().filter { it.isDirectory }.forEach {
            expandedPaths[it.absolutePath] = true
        }
    }
}
