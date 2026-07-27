package rtlide.shell.toolwindow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.theme.IdeColors
import rtlide.editor.EditorState
import rtlide.lang.analysis.Severity
import java.io.File

@Composable
fun ProblemsPanel(state: EditorState, modifier: Modifier = Modifier) {
    val allProblems = state.tabs.flatMap { tab ->
        tab.diagnostics.map { tab.file to it }
    }.sortedByDescending { it.second.severity }

    Column(modifier.fillMaxSize().background(IdeColors.GutterBackground)) {
        Row(
            Modifier.fillMaxWidth().background(IdeColors.TabInactiveBackground).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("المشاكل", color = IdeColors.TextDefault, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            val errorCount = allProblems.count { it.second.severity == Severity.Error }
            val warningCount = allProblems.count { it.second.severity == Severity.Warning }
            
            if (errorCount > 0) {
                Text("$errorCount خطأ", color = Color.Red, fontSize = 11.sp)
                Spacer(Modifier.width(4.dp))
            }
            if (warningCount > 0) {
                Text("$warningCount تنبيه", color = Color(0xFFEBCB8B), fontSize = 11.sp)
            }
        }

        if (allProblems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد مشاكل مكتشفة", color = IdeColors.TextMuted, fontSize = 12.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(allProblems) { (file, diag) ->
                    ProblemItem(file, diag) {
                        val index = state.tabs.indexOfFirst { it.file.absolutePath == file.absolutePath }
                        if (index != -1) {
                            state.activeTabIndex = index
                            state.tabs[index].document.caret = rtlide.core.document.Caret(diag.location.line - 1, diag.location.column - 1)
                        }
                    }
                    HorizontalDivider(color = IdeColors.BorderColor, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun ProblemItem(file: File, diag: rtlide.lang.analysis.Diagnostic, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = when (diag.severity) {
            Severity.Error -> Color.Red
            Severity.Warning -> Color(0xFFEBCB8B)
            else -> Color.Gray
        }
        
        Box(Modifier.size(8.dp).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(diag.message, color = IdeColors.TextDefault, fontSize = 13.sp)
            Text("${file.name}:${diag.location.line}:${diag.location.column}", color = IdeColors.TextMuted, fontSize = 11.sp)
        }
    }
}
