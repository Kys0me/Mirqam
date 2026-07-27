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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.theme.IdeColors

@Composable
fun MainToolbar(
    onOpenFile: () -> Unit,
    onOpenProject: () -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.background(IdeColors.ToolbarBackground).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("RTL IDE", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.width(12.dp))
        
        IdeMenuBar(onOpenFile, onOpenProject)
        
        Spacer(Modifier.width(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onRun() }) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF4EC9B0), modifier = Modifier.height(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("تشغيل", color = Color(0xFF4EC9B0), fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        Text("العربية البرمجية", color = IdeColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
fun IdeMenuBar(onOpenFile: () -> Unit, onOpenProject: () -> Unit) {
    Row {
        IdeMenuCategory("ملف") {
            IdeMenuItem("فتح ملف...", onClick = onOpenFile)
            IdeMenuItem("فتح مشروع...", onClick = onOpenProject)
            HorizontalDivider(color = IdeColors.BorderColor, modifier = Modifier.padding(vertical = 4.dp))
            IdeMenuItem("خروج", onClick = { /* exitApplication handles this in Main, we might need to pass a callback */ })
        }
        IdeMenuCategory("تحرير") {
            IdeMenuItem("تراجع")
            IdeMenuItem("إعادة")
            HorizontalDivider(color = IdeColors.BorderColor, modifier = Modifier.padding(vertical = 4.dp))
            IdeMenuItem("قص")
            IdeMenuItem("نسخ")
            IdeMenuItem("لصق")
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
            modifier = Modifier.background(IdeColors.TabInactiveBackground).border(1.dp, IdeColors.BorderColor)
        ) {
            content()
        }
    }
}

@Composable
fun IdeMenuItem(text: String, onClick: () -> Unit = {}) {
    DropdownMenuItem(
        text = { Text(text, fontSize = 13.sp, color = IdeColors.TextDefault) },
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
        Text("السطر ${caretLine + 1}، العمود ${caretCol + 1}", color = Color.White, fontSize = 11.sp)
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
        modifier.fillMaxHeight().width(1.dp).background(IdeColors.BorderColor)
            .pointerInput(Unit) { detectDragGestures { _, drag -> onDrag(drag.x) } }
    )
}

@Composable
fun HorizontalResizer(onDrag: (Float) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(1.dp).background(IdeColors.BorderColor)
            .pointerInput(Unit) { detectDragGestures { _, drag -> onDrag(drag.y) } }
    )
}
