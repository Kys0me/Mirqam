package rtlide.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import rtlide.core.theme.IdeColors

@Composable
fun IdeDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: IntOffset = IntOffset.Zero,
    minWidth: Dp = 180.dp,
    maxWidth: Dp = 480.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded)
        return

    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = true

    val positionProvider = remember(offset) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                var x = anchorBounds.left + offset.x
                var y = anchorBounds.top + offset.y

                if (x + popupContentSize.width > windowSize.width) {
                    x = (anchorBounds.left - popupContentSize.width + offset.x)
                        .coerceAtLeast(0)
                }
                if (y + popupContentSize.height > windowSize.height) {
                    y = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
                }
                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = fadeIn(tween(90)) + scaleIn(
                initialScale = 0.96f,
                animationSpec = tween(90),
                transformOrigin = TransformOrigin(0f, 0f)
            ),
            exit = fadeOut(tween(60))
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = minWidth, maxWidth)
                    .shadow(4.dp, RoundedCornerShape(2.dp), clip = false)
                    .background(IdeColors.TabInactiveBackground, RoundedCornerShape(2.dp))
                    .border(1.dp, IdeColors.BorderColor, RoundedCornerShape(2.dp))
                    .padding(vertical = 4.dp),
                content = content
            )
        }
    }
}

@Composable
fun IdeMenuItem(
    text: String,
    shortcut: String? = null,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val highlighted = isHovered && enabled

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .hoverable(interactionSource)
            .background(if (highlighted) IdeColors.SelectionBackground else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            icon?.invoke()
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = when {
                !enabled -> IdeColors.TextDisabled
                highlighted -> IdeColors.MenuSelectionText
                else -> IdeColors.TextDefault
            },
            modifier = Modifier.weight(1f)
        )
        if (shortcut != null) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = shortcut,
                fontSize = 12.sp,
                color = when {
                    !enabled -> IdeColors.TextDisabled
                    highlighted -> IdeColors.MenuSelectionText
                    else -> IdeColors.TextSecondary
                }
            )
        }
    }
}

@Composable
fun IdeMenuDivider() {
    HorizontalDivider(
        color = IdeColors.BorderColor,
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 0.dp, vertical = 4.dp)
    )
}
