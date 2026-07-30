package rtlide.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val scrollState = rememberScrollState()

    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(IdeColors.TabInactiveBackground)
            .border(width = 1.dp, color = IdeColors.BorderColor)
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.tabs.forEachIndexed { index, tab ->
            val isActive = state.activeTabIndex == index
            EditorTab(
                label = tab.file.name,
                isActive = isActive,
                isDirty = tab.isDirty,
                onSelect = { state.activeTabIndex = index },
                onClose = { state.closeTab(index) }
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun EditorTab(
    label: String,
    isActive: Boolean,
    isDirty: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        Modifier
            .fillMaxHeight()
            .background(if (isActive) IdeColors.TabActiveBackground else Color.Transparent)
            .border(width = 1.dp, color = IdeColors.BorderColor)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxHeight()) {
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Unsaved-changes dot — shown instead of the close 'x' until hovered,
                // matching IntelliJ's dirty-indicator swap behavior
                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                    if (isDirty && !isHovered) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(IdeColors.TextDefault)
                        )
                    } else if (isHovered || isActive) {
                        TabCloseButton(onClose = onClose)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    color = if (isActive) IdeColors.TextDefault else IdeColors.TextMuted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
            Spacer(Modifier.weight(1f))
            if (isActive) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(IdeColors.AccentBlue)
                )
            } else {
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun TabCloseButton(onClose: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(if (isHovered) IdeColors.BorderColor else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClose
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close tab",
            tint = IdeColors.TextDefault,
            modifier = Modifier.size(12.dp)
        )
    }
}
