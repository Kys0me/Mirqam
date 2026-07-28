package rtlide.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.theme.IdeColors

@Composable
fun TerminalTabItem(
    name: String,
    active: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        Modifier
            .background(if (active) IdeColors.TabActiveBackground else Color.Transparent)
            .selectable(selected = active, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            color = if (active) IdeColors.TextDefault else IdeColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Default.Close,
            contentDescription = "Close",
            tint = IdeColors.TextMuted,
            modifier = Modifier.size(12.dp).clickable { onClose() }
        )
    }
}
