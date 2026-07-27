package rtlide.shell.toolwindow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.theme.IdeColors
import java.io.File
import rtlide.core.project.Project

/** Tool-window "stripe" (IntelliJ's tool-window bar / VS Code's activity bar).
 *  Placed at the start it renders on the RIGHT under RTL. */
@Composable
fun ToolWindowStripe(stripe: Stripe, layout: IdeLayoutState, modifier: Modifier = Modifier) {
    val tools = ToolWindowId.entries.filter { it.stripe == stripe }
    if (stripe == Stripe.Bottom) {
        Row(modifier.fillMaxWidth().background(IdeColors.TabInactiveBackground).border(1.dp, IdeColors.BorderColor).padding(2.dp)) {
            tools.forEach { StripeButton(it, layout) }
        }
    } else {
        Column(
            modifier.fillMaxHeight().width(48.dp).background(IdeColors.TabInactiveBackground).border(1.dp, IdeColors.BorderColor).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            tools.forEach { StripeButton(it, layout) }
        }
    }
}

@Composable
private fun StripeButton(id: ToolWindowId, layout: IdeLayoutState) {
    val active = layout.isVisible(id)
    Text(
        text = id.title,
        color = if (active) Color.White else IdeColors.TextMuted,
        fontSize = 12.sp,
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) IdeColors.SelectionBackground else Color.Transparent)
            .clickable { layout.toggle(id) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** A project tree that lists real files. */
@Composable
fun ProjectToolWindow(project: Project?, onFileSelected: (File) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight().background(IdeColors.GutterBackground).border(1.dp, IdeColors.BorderColor)) {
        Text(
            "مشروع",
            color = IdeColors.TextDefault,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().background(IdeColors.TabInactiveBackground).padding(8.dp),
        )
        
        if (project == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا يوجد مشروع مفتوح", color = IdeColors.TextMuted, fontSize = 12.sp)
            }
        } else {
            val root = project.rootDir
            LazyColumn(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                item {
                    ProjectTreeItem(0, root, onFileSelected)
                }
                // Simplified recursive tree for now. In a real app we'd want expandable nodes.
                val files = root.walk().maxDepth(2).filter { it != root }.toList()
                items(files) { file ->
                    val depth = file.relativeTo(root).path.split(File.separator).size
                    ProjectTreeItem(depth, file, onFileSelected)
                }
            }
        }
    }
}

@Composable
private fun ProjectTreeItem(depth: Int, file: File, onFileSelected: (File) -> Unit) {
    val name = if (file.isDirectory) "📁 ${file.name}" else "📄 ${file.name}"
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { if (!file.isDirectory) onFileSelected(file) }
            .padding(start = (12 + depth * 16).dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = IdeColors.TextDefault,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
