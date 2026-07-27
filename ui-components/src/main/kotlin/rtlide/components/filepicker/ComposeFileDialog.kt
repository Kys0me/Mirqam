package rtlide.components.filepicker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.theme.IdeColors
import java.io.File

@Composable
fun ComposeFileDialog(
    state: FilePickerState,
    onDismiss: () -> Unit
) {
    // Overlay background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = true, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        // Dialog container
        Column(
            modifier = Modifier
                .width(700.dp)
                .height(500.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(IdeColors.GutterBackground)
                .border(1.dp, IdeColors.BorderColor, RoundedCornerShape(8.dp))
                .clickable(enabled = false) {} // Prevent clicks from dismissing
        ) {
            // Header
            DialogHeader(state)
            
            HorizontalDivider(color = IdeColors.BorderColor)
            
            Row(modifier = Modifier.weight(1f)) {
                // Sidebar (Favorites/Quick Access)
                DialogSidebar(state, modifier = Modifier.width(180.dp).fillMaxHeight())
                
                VerticalDivider(color = IdeColors.BorderColor)
                
                // File List
                DialogFileList(state, modifier = Modifier.weight(1f).fillMaxHeight())
            }
            
            HorizontalDivider(color = IdeColors.BorderColor)
            
            // Footer
            DialogFooter(state, onDismiss)
        }
    }
}

@Composable
private fun DialogHeader(state: FilePickerState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.KeyboardArrowUp,
            contentDescription = "Up",
            tint = IdeColors.TextDefault,
            modifier = Modifier.size(24.dp).clickable { state.navigateUp() }
        )
        
        Spacer(Modifier.width(8.dp))
        
        // Breadcrumbs
        Box(modifier = Modifier.weight(1f).background(IdeColors.TabInactiveBackground, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                state.currentDirectory.absolutePath,
                color = IdeColors.TextDefault,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(Modifier.width(12.dp))
        
        // Search
        TextField(
            value = state.searchQuery,
            onValueChange = { state.searchQuery = it },
            modifier = Modifier.width(150.dp).height(32.dp),
            placeholder = { Text("بحث", fontSize = 12.sp) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = IdeColors.TabInactiveBackground,
                unfocusedContainerColor = IdeColors.TabInactiveBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = IdeColors.TextDefault,
                unfocusedTextColor = IdeColors.TextDefault
            ),
            singleLine = true,
            shape = RoundedCornerShape(4.dp)
        )
    }
}

@Composable
private fun DialogSidebar(state: FilePickerState, modifier: Modifier = Modifier) {
    val home = File(System.getProperty("user.home"))
    val roots = File.listRoots()
    
    Column(modifier = modifier.background(IdeColors.TabInactiveBackground).padding(vertical = 8.dp)) {
        SidebarItem("الرئيسية", Icons.Default.Home) { state.navigateTo(home) }
        SidebarItem("المستندات", Icons.Default.Menu) { state.navigateTo(File(home, "Documents")) }
        SidebarItem("سطح المكتب", Icons.Default.Star) { state.navigateTo(File(home, "Desktop")) }
        
        Spacer(Modifier.height(16.dp))
        Text("الأقراص", color = IdeColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        
        roots.forEach { root ->
            SidebarItem(root.absolutePath, Icons.Default.Build) { state.navigateTo(root) }
        }
    }
}

@Composable
private fun SidebarItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = IdeColors.TextMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = IdeColors.TextDefault, fontSize = 13.sp)
    }
}

@Composable
private fun DialogFileList(state: FilePickerState, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(state.filteredFiles) { file ->
            FileRow(file, isSelected = state.selectedFile == file, onClick = {
                state.selectedFile = file
            }, onDoubleClick = {
                if (file.isDirectory) {
                    state.navigateTo(file)
                } else {
                    state.onFileSelected(file)
                }
            })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: File,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    val icon = if (file.isDirectory) Icons.AutoMirrored.Filled.List else Icons.Default.Edit // Should use better icons
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) IdeColors.SelectionBackground else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onDoubleClick = onDoubleClick
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (file.isDirectory) Color(0xFFEBCB8B) else IdeColors.TextMuted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            file.name.ifEmpty { file.absolutePath },
            color = IdeColors.TextDefault,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DialogFooter(state: FilePickerState, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            state.selectedFile?.name ?: "",
            color = IdeColors.TextDefault,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text("إلغاء", color = IdeColors.TextDefault)
        }
        
        Spacer(Modifier.width(8.dp))
        
        Button(
            onClick = {
                val selected = state.selectedFile ?: state.currentDirectory
                state.onFileSelected(selected)
            },
            colors = ButtonDefaults.buttonColors(containerColor = IdeColors.StatusbarBackground),
            contentPadding = PaddingValues(horizontal = 24.dp),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text("فتح", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
