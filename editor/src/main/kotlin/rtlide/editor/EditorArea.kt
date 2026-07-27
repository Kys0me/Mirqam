package rtlide.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.theme.IdeColors
import rtlide.editor.render.EditorCanvas

/** The center editor area: a simple tab strip plus the custom RTL canvas. */
@Composable
fun EditorArea(
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val activeTab = state.activeTab
    Column(modifier.background(IdeColors.GutterBackground)) {
        EditorTabBar(state)
        if (activeTab != null) {
            EditorCanvas(
                state = state,
                tab = activeTab,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("لا توجد ملفات مفتوحة", color = IdeColors.TextMuted)
            }
        }
    }
}

@Composable
private fun EditorTabBar(state: EditorState) {
    Row(
        Modifier.fillMaxWidth().background(IdeColors.TabInactiveBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.tabs.forEachIndexed { index, tab ->
            val isActive = state.activeTabIndex == index
            Box(
                Modifier
                    .background(if (isActive) IdeColors.TabActiveBackground else IdeColors.TabInactiveBackground)
                    .border(width = 1.dp, color = IdeColors.BorderColor, shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .clickable { state.activeTabIndex = index }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tab.file.name,
                        color = if (isActive) IdeColors.TextDefault else IdeColors.TextMuted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "×",
                        color = IdeColors.TextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { state.closeTab(index) }
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
