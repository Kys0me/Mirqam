package rtlide.core.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ProjectState(
    val openFiles: List<String> = emptyList(),
    val activeFile: String? = null
)

object ProjectStateStore {
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private fun getStateFile(project: Project): File {
        return File(project.path, "مرقام/الحالة.json")
    }

    fun load(project: Project): ProjectState {
        val file = getStateFile(project)
        if (!file.exists()) return ProjectState()
        return try {
            json.decodeFromString<ProjectState>(file.readText())
        } catch (_: Exception) {
            ProjectState()
        }
    }

    fun save(project: Project, state: ProjectState) {
        val file = getStateFile(project)
        try {
            file.parentFile.mkdirs()
            file.writeText(json.encodeToString(state))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
