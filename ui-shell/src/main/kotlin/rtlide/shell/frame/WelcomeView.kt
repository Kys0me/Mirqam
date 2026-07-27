package rtlide.shell.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.project.Project
import rtlide.core.project.RecentProjectsStore
import rtlide.core.theme.IdeColors

@Composable
fun WelcomeView(
    onNewProject: () -> Unit,
    onOpenProject: () -> Unit,
    onProjectSelected: (Project) -> Unit,
    modifier: Modifier = Modifier
) {
    val recentProjects = RecentProjectsStore.getRecentProjects()

    Row(modifier.fillMaxSize().background(IdeColors.GutterBackground)) {
        // Left side: Actions
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(48.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "RTL IDE",
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "بيئة تطوير عربية متكاملة",
                color = IdeColors.TextMuted,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            WelcomeActionItem("مشروع جديد", Icons.Default.Add, onClick = onNewProject)
            WelcomeActionItem("فتح مشروع...", Icons.AutoMirrored.Filled.List, onClick = onOpenProject)
        }

        // Right side: Recent projects
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(IdeColors.TabInactiveBackground)
                .padding(48.dp)
        ) {
            Text(
                "المشاريع الأخيرة",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (recentProjects.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد مشاريع سابقة", color = IdeColors.TextMuted)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentProjects) { project ->
                        RecentProjectItem(project, onClick = { onProjectSelected(project) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeActionItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 18.sp)
    }
}

@Composable
private fun RecentProjectItem(project: Project, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Text(project.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(project.path, color = IdeColors.TextMuted, fontSize = 12.sp)
    }
}
