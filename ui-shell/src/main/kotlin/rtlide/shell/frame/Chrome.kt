package rtlide.shell.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.theme.IdeColors
import java.awt.Cursor

@Composable
fun MainToolbar(
    onOpenFile: () -> Unit,
    onOpenProject: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit = {},
    onRerun: () -> Unit = {},
    isRunning: Boolean = false,
    canRerun: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.background(IdeColors.ToolbarBackground).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "RTL IDE",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.width(12.dp))

        IdeMenuBar(onOpenFile, onOpenProject)

        Spacer(Modifier.width(16.dp))

        // Run Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onRun() }.padding(horizontal = 4.dp)
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xFF4EC9B0),
                modifier = Modifier.height(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("تشغيل", color = Color(0xFF4EC9B0), fontSize = 13.sp)
        }

        // Rerun Button
        IconButton(onClick = onRerun, enabled = canRerun, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Rerun",
                tint = if (canRerun) Color(0xFF59A275) else IdeColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }

        // Stop Button
        IconButton(onClick = onStop, enabled = isRunning, modifier = Modifier.size(28.dp)) {
            Box(
                Modifier.size(10.dp)
                    .background(if (isRunning) Color(0xFFCF5B56) else IdeColors.TextMuted)
            )
        }

        Spacer(Modifier.weight(1f))
        Text("العربية البرمجية", color = IdeColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
fun IdeMenuBar(onOpenFile: () -> Unit, onOpenProject: () -> Unit) {
    Row {
        IdeMenuCategory("ملف") {
            IdeMenuItem(
                "فتح ملف...",
                icon = { Icon(Icons.Default.FileOpen, null, Modifier.size(16.dp)) },
                onClick = onOpenFile
            )
            IdeMenuItem(
                "فتح مشروع...",
                icon = { Icon(Icons.Default.CreateNewFolder, null, Modifier.size(16.dp)) },
                onClick = onOpenProject
            )
            HorizontalDivider(
                color = IdeColors.BorderColor,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            IdeMenuItem(
                "خروج",
                onClick = { /* exitApplication handles this in Main */ })
        }
        IdeMenuCategory("تحرير") {
            IdeMenuItem(
                "تراجع",
                icon = { Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(16.dp)) }
            )
            IdeMenuItem(
                "إعادة",
                icon = { Icon(Icons.AutoMirrored.Filled.Redo, null, Modifier.size(16.dp)) }
            )
            HorizontalDivider(
                color = IdeColors.BorderColor,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            IdeMenuItem(
                "قص",
                icon = { Icon(Icons.Default.ContentCut, null, Modifier.size(16.dp)) }
            )
            IdeMenuItem(
                "نسخ",
                icon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) }
            )
            IdeMenuItem(
                "لصق",
                icon = { Icon(Icons.Default.ContentPaste, null, Modifier.size(16.dp)) }
            )
        }
        IdeMenuCategory("عرض") {
            IdeMenuItem("ملء الشاشة")
        }
    }
}

@Composable
fun IdeMenuCategory(title: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = title,
            color = if (expanded) Color.White else IdeColors.TextDefault,
            fontSize = 13.sp,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(IdeColors.TabInactiveBackground)
                .border(1.dp, IdeColors.BorderColor)
        ) {
            content()
        }
    }
}

@Composable
fun IdeMenuItem(
    text: String,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    DropdownMenuItem(
        text = { Text(text, fontSize = 13.sp, color = IdeColors.TextDefault) },
        leadingIcon = icon,
        onClick = onClick,
        colors = MenuDefaults.itemColors(
            textColor = IdeColors.TextDefault,
        )
    )
}

@Composable
fun StatusBar(caretLine: Int, caretCol: Int, langName: String, modifier: Modifier = Modifier) {
    Row(
        modifier.background(IdeColors.StatusbarBackground).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("RTL ⇄", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(20.dp))
        Text(
            "السطر ${caretLine + 1}، العمود ${caretCol + 1}",
            color = Color.White,
            fontSize = 11.sp
        )
        Spacer(Modifier.weight(1f))
        Text(langName, color = Color.White, fontSize = 11.sp)
        Spacer(Modifier.width(20.dp))
        Text("UTF-8", color = Color.White, fontSize = 11.sp)
    }
}

/** Draggable divider between the project panel and the editor. */
@Composable
fun VerticalResizer(onDrag: (Float) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(IdeColors.BorderColor)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) { detectDragGestures { _, drag -> onDrag(drag.x) } }
    )
}

@Composable
fun HorizontalResizer(onDrag: (Float) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(IdeColors.BorderColor)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
            .pointerInput(Unit) { detectDragGestures { _, drag -> onDrag(drag.y) } }
    )
}
