package rtlide.shell.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

    Row(modifier.fillMaxSize().background(IdeColors.TabInactiveBackground)) {
        // Left rail: branding + actions — fixed width, like IntelliJ's welcome sidebar
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(IdeColors.ToolbarBackground)
                .padding(top = 40.dp, bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    "مرقام",
                    color = IdeColors.TextDefault,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "بيئة تطوير عربية متكاملة",
                    color = IdeColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 32.dp)
                )
            }

            WelcomeActionItem("مشروع جديد", Icons.Default.Add, onClick = onNewProject)
            WelcomeActionItem("فتح...", Icons.AutoMirrored.Filled.List, onClick = onOpenProject)
            WelcomeActionItem("الحصول من VCS...", Icons.Default.CloudDownload, onClick = { })

            Spacer(Modifier.weight(1f))

            Text(
                "الإصدار 1.0",
                color = IdeColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // Right panel: recent projects list
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 32.dp, vertical = 40.dp)
        ) {
            Text(
                "المشاريع الأخيرة",
                color = IdeColors.TextDefault,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
            )

            if (recentProjects.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد مشاريع سابقة", color = IdeColors.TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(recentProjects) { project ->
                        RecentProjectItem(project, onClick = { onProjectSelected(project) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeActionItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .background(if (isHovered) IdeColors.MenuSelectionBackground else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isHovered) IdeColors.MenuSelectionText else IdeColors.StatusbarBackground,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (isHovered) IdeColors.MenuSelectionText else IdeColors.TextDefault,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun RecentProjectItem(project: Project, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .hoverable(interactionSource)
            .background(if (isHovered) IdeColors.MenuSelectionBackground else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProjectAvatar(name = project.name)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                project.name,
                color = if (isHovered) IdeColors.MenuSelectionText else IdeColors.TextDefault,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                project.path,
                color = if (isHovered) IdeColors.MenuSelectionText else IdeColors.TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

// IntelliJ-style colored square avatar with the project's first letter —
// color is deterministic per name so the same project always gets the same color
@Composable
private fun ProjectAvatar(name: String) {
    val avatarColors = listOf(
        Color(0xFF3574F0), Color(0xFF3FA85F), Color(0xFFE55765),
        Color(0xFFCB5DF0), Color(0xFFE5A00D), Color(0xFF3DB9CF)
    )
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val color = avatarColors[(name.hashCode().let { if (it < 0) -it else it }) % avatarColors.size]

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}