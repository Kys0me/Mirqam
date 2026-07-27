package rtlide.core.project

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Project(
    val name: String,
    val path: String,
    val lastOpened: Long = System.currentTimeMillis()
) {
    val rootDir: File get() = File(path)
}
