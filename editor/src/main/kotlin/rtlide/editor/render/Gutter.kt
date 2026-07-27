package rtlide.editor.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.theme.IdeColors

/** Line-number gutter. Under LayoutDirection.Rtl this Row-child sits on the
 *  RIGHT automatically — the mirror of IntelliJ's left gutter. */
@Composable
fun Gutter(
    count: Int,
    caretLine: Int,
    lineHeight: Dp,
    width: Dp,
    fontSize: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .width(width)
            .fillMaxHeight()
            .background(IdeColors.GutterBackground)
            .padding(horizontal = 8.dp)
    ) {
        for (n in 1..count.coerceAtLeast(1)) {
            val isCurrentLine = (n - 1) == caretLine
            Box(
                Modifier
                    .height(lineHeight)
                    .fillMaxWidth()
                    .background(if (isCurrentLine) IdeColors.LineHighlight else Color.Transparent)
            ) {
                Text(
                    text = n.toString(),
                    color = if (isCurrentLine) Color.White else IdeColors.GutterForeground,
                    fontSize = (fontSize * 0.8f).sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
    }
}
