package rtlide.shell.toolwindow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.components.IdeDropdownMenu
import rtlide.components.IdeMenuItem
import rtlide.components.IdeMenuDivider
import rtlide.core.project.Project
import rtlide.core.theme.IdeColors
import java.io.File

/** Tool-window "stripe" (IntelliJ's tool-window bar / VS Code's activity bar).
 *  Placed at the start it renders on the RIGHT under RTL. */
@Composable
fun ToolWindowStripe(stripe: Stripe, layout: IdeLayoutState, modifier: Modifier = Modifier) {
    val tools = ToolWindowId.entries.filter { it.stripe == stripe }
    if (stripe == Stripe.Bottom) {
        Row(
            modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(IdeColors.TabInactiveBackground)
                .border(width = 1.dp, color = IdeColors.BorderColor),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tools.forEach { StripeButton(it, layout) }
        }
    } else {
        Column(
            modifier
                .fillMaxHeight()
                .width(40.dp)
                .background(IdeColors.TabInactiveBackground)
                .border(width = 1.dp, color = IdeColors.BorderColor),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            tools.forEach { StripeButton(it, layout) }
        }
    }
}

@Composable
private fun StripeButton(id: ToolWindowId, layout: IdeLayoutState) {
    val active = layout.isVisible(id)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { layout.toggle(id) }
            )
            .hoverable(interactionSource)
            .background(
                if (active) IdeColors.SelectionBackground.copy(alpha = 0.3f)
                else if (isHovered) Color.White.copy(alpha = 0.05f)
                else Color.Transparent
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = id.icon,
            contentDescription = id.title,
            tint = if (active) IdeColors.AccentBlue else IdeColors.TextMuted,
            modifier = Modifier.size(20.dp)
        )
        
        if (active) {
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            
            Box(
                Modifier
                    .then(
                        if (id.stripe == Stripe.Bottom) {
                            Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter)
                        } else {
                            Modifier.fillMaxHeight().width(2.dp).align(if (isRtl) Alignment.CenterStart else Alignment.CenterEnd)
                        }
                    )
                    .background(IdeColors.AccentBlue)
            )
        }
    }
}

/** A project tree that lists real files. */
@Composable
fun ProjectToolWindow(project: Project?, onFileSelected: (File) -> Unit, modifier: Modifier = Modifier) {
    var showMenu by remember { mutableStateOf(false) }
    val treeState = remember(project) { project?.let { ProjectTreeState(it.rootDir) } }

    Column(modifier.fillMaxHeight().background(IdeColors.TabInactiveBackground)) {
        // Tool window header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(IdeColors.TabInactiveBackground)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "المشروع",
                color = IdeColors.TextDefault,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Box {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = IdeColors.TextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { showMenu = true }
                )
                
                IdeDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    IdeMenuItem(
                        "طوي الكل",
                        onClick = { 
                            treeState?.collapseAll()
                            showMenu = false 
                        }
                    )
                    IdeMenuItem(
                        "توسيع الكل",
                        onClick = { 
                            treeState?.expandAll()
                            showMenu = false 
                        }
                    )
                    IdeMenuDivider()
                    IdeMenuItem(
                        "تحديث",
                        onClick = { showMenu = false }
                    )
                }
            }
        }
        HorizontalDivider(color = IdeColors.BorderColor)

        if (project == null || treeState == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا يوجد مشروع مفتوح", color = IdeColors.TextMuted, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .background(IdeColors.GutterBackground)
            ) {
                val items = mutableListOf<Pair<Int, File>>()
                fun walk(file: File, depth: Int) {
                    items.add(depth to file)
                    if (file.isDirectory && treeState.isExpanded(file)) {
                        file.listFiles()?.sortedBy { it.name }?.forEach { walk(it, depth + 1) }
                    }
                }
                walk(project.rootDir, 0)

                items(items) { (depth, file) ->
                    ProjectTreeItem(
                        depth = depth,
                        file = file,
                        isExpanded = treeState.isExpanded(file),
                        onToggle = { treeState.toggle(file) },
                        onFileSelected = onFileSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectTreeItem(
    depth: Int,
    file: File,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onFileSelected: (File) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    Row(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .hoverable(interactionSource)
            .background(if (isHovered) Color.White.copy(alpha = 0.05f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { 
                    if (file.isDirectory) onToggle()
                    else onFileSelected(file)
                }
            )
            .padding(start = (8 + depth * 12).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (file.isDirectory) {
            val chevron = if (isExpanded) {
                Icons.Default.KeyboardArrowDown
            } else {
                if (LocalLayoutDirection.current == LayoutDirection.Rtl) Icons.Default.ChevronLeft else Icons.Default.ChevronRight
            }
            Icon(
                imageVector = chevron,
                contentDescription = null,
                tint = IdeColors.TextMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = Color(0xFF62B1EE),
                modifier = Modifier.size(16.dp)
            )
        } else {
            Spacer(Modifier.width(18.dp)) // Chevron space + folder space offset
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = IdeColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
        
        Spacer(Modifier.width(6.dp))
        
        Text(
            text = file.name,
            color = IdeColors.TextDefault,
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif,
        )
    }
}
