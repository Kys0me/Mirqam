package rtlide.editor.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.theme.IdeColors

/**
 * Line-number gutter styled after IntelliJ.
 * In RTL, this sits on the right. It uses Canvas to ensure exact alignment with EditorCanvas.
 */
@Composable
fun Gutter(
    count: Int,
    caretLine: Int,
    lineHeight: Dp,
    width: Dp,
    fontSize: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    
    val lineHeightPx = with(density) { lineHeight.toPx() }
    val widthPx = with(density) { width.toPx() }

    val numberStyle = remember(fontSize) {
        TextStyle(
            color = IdeColors.GutterForeground,
            fontSize = (fontSize * 0.85f).sp,
            fontFamily = FontFamily.Monospace,
            textAlign = if (isRtl) TextAlign.Left else TextAlign.Right
        )
    }

    val activeNumberStyle = numberStyle.copy(color = Color(0xFFAEB3BC))

    Row {
        Canvas(
            modifier
                .width(width)
                .fillMaxHeight()
                .background(IdeColors.GutterBackground)
        ) {
            // Draw background highlight for current line
            drawRect(
                color = IdeColors.LineHighlight,
                topLeft = Offset(0f, caretLine * lineHeightPx),
                size = Size(widthPx, lineHeightPx)
            )

            // Draw numbers
            for (i in 0 until count) {
                val isCurrent = i == caretLine
                val text = (i + 1).toString()
                val layout = measurer.measure(
                    text = text,
                    style = if (isCurrent) activeNumberStyle else numberStyle,
                    maxLines = 1
                )

                // Padding from the edge and the separator
                val horizontalPadding = 8.dp.toPx()

                val x = if (isRtl) {
                    // In RTL, gutter is on the right. separator is on the LEFT of the gutter panel.
                    // Numbers align LEFT (towards the separator/code).
                    horizontalPadding
                } else {
                    // In LTR, gutter is on the left. separator is on the RIGHT.
                    // Numbers align RIGHT (towards the separator/code).
                    widthPx - layout.size.width - horizontalPadding
                }

                val y = i * lineHeightPx + (lineHeightPx - layout.size.height) / 2f

                drawText(layout, topLeft = Offset(x, y))
            }

            // Draw separator line
            val separatorX = if (isRtl) 0f else widthPx
            drawLine(
                color = IdeColors.GutterSeparator,
                start = Offset(separatorX, 0f),
                end = Offset(separatorX, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }

    }
}
