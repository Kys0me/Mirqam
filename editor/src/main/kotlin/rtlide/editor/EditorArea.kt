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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.document.Document
import rtlide.core.theme.IdeColors
import rtlide.editor.render.EditorCanvas
import rtlide.lang.highlight.Highlighter
import rtlide.lang.schema.LanguageDefinition

/** The center editor area: a simple tab strip plus the custom RTL canvas. */
@Composable
fun EditorArea(
    fileName: String,
    doc: Document,
    highlighter: Highlighter,
    lang: LanguageDefinition,
    modifier: Modifier = Modifier,
) {
    val keywords = remember(lang) {
        (lang.grammar.controlKeywords + lang.grammar.keywords + lang.grammar.builtins + lang.grammar.constants)
            .distinct()
    }
    Column(modifier.background(IdeColors.GutterBackground)) {
        EditorTabBar(fileName)
        EditorCanvas(
            doc = doc,
            highlighter = highlighter,
            keywords = keywords,
            brackets = lang.brackets,
            indent = lang.indent,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun EditorTabBar(fileName: String) {
    Row(
        Modifier.fillMaxWidth().background(IdeColors.TabInactiveBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .background(IdeColors.TabActiveBackground)
                .border(width = 1.dp, color = IdeColors.BorderColor, shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fileName, color = IdeColors.TextDefault, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                Text("×", color = IdeColors.TextMuted, fontSize = 16.sp, modifier = Modifier.clickable {  })
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
