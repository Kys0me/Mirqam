package rtlide.editor.analysis

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import rtlide.editor.EditorTab
import kotlin.time.Duration.Companion.milliseconds

/**
 * Orchestrates background analysis for an editor tab.
 */
object AnalysisPipeline {
    
    @OptIn(FlowPreview::class)
    fun attach(tab: EditorTab, scope: CoroutineScope) {
        scope.launch {
            snapshotFlow { tab.document.lines }
                .debounce(500.milliseconds)
                .collectLatest {
                    tab.requestAnalysis(scope)
                }
        }
    }
}
