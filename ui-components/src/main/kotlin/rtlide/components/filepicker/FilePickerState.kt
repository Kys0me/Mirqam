package rtlide.components.filepicker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

class FilePickerState(
    initialDirectory: File = File(System.getProperty("user.home")),
    val onFileSelected: (File?) -> Unit
) {
    var currentDirectory by mutableStateOf(initialDirectory)
        private set

    var files by mutableStateOf(listFiles(initialDirectory))
        private set

    var selectedFile by mutableStateOf<File?>(null)
    
    var searchQuery by mutableStateOf("")

    fun navigateTo(directory: File) {
        if (directory.isDirectory) {
            currentDirectory = directory
            files = listFiles(directory)
            selectedFile = null
            searchQuery = ""
        }
    }

    fun navigateUp() {
        currentDirectory.parentFile?.let { navigateTo(it) }
    }

    private fun listFiles(directory: File): List<File> {
        return try {
            directory.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    val filteredFiles: List<File>
        get() = if (searchQuery.isEmpty()) {
            files
        } else {
            files.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
}
