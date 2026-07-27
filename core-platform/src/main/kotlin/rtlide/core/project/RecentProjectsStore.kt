package rtlide.core.project

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object RecentProjectsStore {
    private val storageFile = File(System.getProperty("user.home"), ".rtlide/recent-projects.json")
    private val json = Json { prettyPrint = true }

    fun getRecentProjects(): List<Project> {
        if (!storageFile.exists()) return emptyList()
        return try {
            json.decodeFromString<List<Project>>(storageFile.readText())
                .sortedByDescending { it.lastOpened }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addProject(project: Project) {
        val current = getRecentProjects().toMutableList()
        current.removeIf { it.path == project.path }
        current.add(0, project.copy(lastOpened = System.currentTimeMillis()))
        save(current.take(10))
    }

    private fun save(projects: List<Project>) {
        try {
            storageFile.parentFile.mkdirs()
            storageFile.writeText(json.encodeToString(projects))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
