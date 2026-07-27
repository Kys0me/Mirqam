package rtlide.editor.intelligence

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small state holder for the code-completion popup. */
class CompletionState {
    var visible by mutableStateOf(false)
        private set
    var items by mutableStateOf<List<String>>(emptyList())
        private set
    var selected by mutableStateOf(0)

    fun show(list: List<String>) {
        items = list
        visible = list.isNotEmpty()
        selected = 0
    }

    fun hide() {
        visible = false
        items = emptyList()
        selected = 0
    }
}

/** The list itself. Positioning (anchoring to the RTL caret) is handled by the
 *  caller in EditorCanvas; this composable only renders. */
@Composable
fun CompletionList(state: CompletionState, onPick: (String) -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF252526))
            .width(220.dp)
            .padding(vertical = 4.dp)
    ) {
        state.items.forEachIndexed { index, item ->
            val bg = if (index == state.selected) Color(0xFF094771) else Color.Transparent
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .clickable { onPick(item) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("λ", color = Color(0xFF569CD6), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                Text(item, color = Color(0xFFD4D4D4), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
