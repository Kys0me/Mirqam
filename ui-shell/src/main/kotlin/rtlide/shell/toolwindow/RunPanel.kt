package rtlide.shell.toolwindow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import rtlide.core.theme.IdeColors
import rtlide.shell.ShellViewModel
import rtlide.terminal.ui.IntegratedTerminalView
import rtlide.terminal.ui.TerminalTabItem

@Composable
fun RunPanel(vm: ShellViewModel, modifier: Modifier = Modifier) {
    val activeTab = vm.activeRunTab
    val isAlive by (activeTab?.backend?.isAlive ?: MutableStateFlow(false)).collectAsState()

    Column(modifier.background(IdeColors.GutterBackground).border(1.dp, IdeColors.BorderColor)) {
        // Tab Bar
        Row(
            Modifier.fillMaxWidth().background(IdeColors.TabInactiveBackground),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            vm.runTabs.forEachIndexed { index, tab ->
                TerminalTabItem(
                    name = tab.name,
                    active = index == vm.activeRunTabIndex,
                    onClick = { vm.activeRunTabIndex = index },
                    onClose = { vm.closeRunTab(index) }
                )
            }
        }

        Row(Modifier.fillMaxSize()) {
            // Toolbar
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(32.dp)
                    .background(IdeColors.TabInactiveBackground)
                    .border(1.dp, IdeColors.BorderColor),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = { activeTab?.let { vm.rerunProcess(it) } },
                    enabled = activeTab?.lastCommand != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rerun", tint = if (activeTab?.lastCommand != null) Color(0xFF59A275) else IdeColors.TextMuted, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { activeTab?.let { vm.stopProcess(it) } },
                    enabled = isAlive,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(
                        Modifier.size(12.dp).background(if (isAlive) Color(0xFFCF5B56) else IdeColors.TextMuted)
                    )
                }
                IconButton(
                    onClick = { activeTab?.backend?.clear() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear All", tint = IdeColors.TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            // Console View
            if (activeTab != null) {
                IntegratedTerminalView(activeTab.backend, Modifier.weight(1f))
            } else {
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد عمليات تشغيل جارية", color = IdeColors.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
