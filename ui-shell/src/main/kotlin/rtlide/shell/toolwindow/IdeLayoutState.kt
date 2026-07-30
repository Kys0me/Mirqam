package rtlide.shell.toolwindow

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class Stripe { Start, Bottom, End }

enum class ToolWindowId(val title: String, val stripe: Stripe, val icon: ImageVector) {
    Project("مشروع", Stripe.Start, Icons.Default.Folder),
    Structure("البنية", Stripe.Start, Icons.Default.AccountTree),
    Run("تشغيل", Stripe.Bottom, Icons.Default.PlayArrow),
    Problems("مشاكل", Stripe.Bottom, Icons.Default.Warning),
}

/** Holds tool-window visibility and panel sizes as Compose state. */
@Stable
class IdeLayoutState {

    private val visibility = mutableStateMapOf(
        ToolWindowId.Project to true,
        ToolWindowId.Run to true,
    )

    var startWidth by mutableStateOf(280.dp)
        private set

    var bottomHeight by mutableStateOf(220.dp)
        private set

    fun isVisible(id: ToolWindowId): Boolean = visibility[id] == true

    fun toggle(id: ToolWindowId) {
        visibility[id] = !(visibility[id] ?: false)
    }

    fun show(id: ToolWindowId) {
        visibility[id] = true
    }

    fun hideAllToolWindows() {
        visibility.keys.toList().forEach { visibility[it] = false }
    }

    fun resizeStart(dx: Float) {
        // In RTL, dx < 0 means moving mouse to the left (increasing right panel width)
        startWidth = (startWidth.value - dx).coerceIn(160f, 640f).dp
    }

    fun resizeBottom(dy: Float) {
        bottomHeight = (bottomHeight.value - dy).coerceIn(100f, 600f).dp
    }
}
