package rtlide.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import rtlide.components.filepicker.ComposeFileDialog
import rtlide.components.filepicker.FilePickerState
import rtlide.core.SakhrConfig
import rtlide.core.project.Project
import rtlide.core.project.RecentProjectsStore
import rtlide.core.theme.IdeColors
import rtlide.editor.EditorArea
import rtlide.shell.frame.HorizontalResizer
import rtlide.shell.frame.MainToolbar
import rtlide.shell.frame.StatusBar
import rtlide.shell.frame.VerticalResizer
import rtlide.shell.frame.WelcomeView
import rtlide.shell.keymap.KeymapController
import rtlide.shell.toolwindow.IdeLayoutState
import rtlide.shell.toolwindow.ProjectToolWindow
import rtlide.shell.toolwindow.RunPanel
import rtlide.shell.toolwindow.Stripe
import rtlide.shell.toolwindow.ToolWindowId
import rtlide.shell.toolwindow.ToolWindowStripe

/**
 * The IntelliJ-style IDE frame.
 */
@Composable
fun IdeFrame(keymap: KeymapController, modifier: Modifier = Modifier) {
    val layout = remember { IdeLayoutState() }
    val scope = rememberCoroutineScope()
    val vm = remember { ShellViewModel(scope) }

    var showFileDialog by remember { mutableStateOf(false) }
    val filePickerState = remember {
        FilePickerState(onFileSelected = { file ->
            if (file != null) {
                if (file.isDirectory) {
                    val project = Project(file.name, file.absolutePath)
                    RecentProjectsStore.addProject(project)
                    vm.openProject(project)
                } else {
                    vm.openFile(file)
                }
            }
            showFileDialog = false
        })
    }

    LaunchedEffect(keymap, layout, vm) {
        keymap.bind("ToggleProjectView") { layout.toggle(ToolWindowId.Project) }
        keymap.bind("ToggleTerminal") { layout.toggle(ToolWindowId.Run) }
        keymap.bind("HideAllWindows") { layout.hideAllToolWindows() }
        
        keymap.bind("Undo") { vm.editorState.activeTab?.document?.undo() }
        keymap.bind("Redo") { vm.editorState.activeTab?.document?.redo() }
        keymap.bind("CloseTab") { vm.editorState.closeActiveTab() }

        keymap.bind("NextProblem") { vm.editorState.gotoNextProblem() }
        keymap.bind("PrevProblem") { vm.editorState.gotoPreviousProblem() }
        keymap.bind("ShowIntentionActions") { vm.editorState.activeTab?.showIntentionActions() }
        keymap.bind("ReformatCode") { vm.editorState.activeTab?.reformat() }
    }

    if (vm.currentProject == null && vm.editorState.tabs.isEmpty()) {
        WelcomeView(
            onNewProject = { showFileDialog = true },
            onOpenProject = { showFileDialog = true },
            onProjectSelected = { vm.openProject(it) },
            modifier = modifier
        )
    } else {
        Column(modifier.fillMaxSize()) {
            val activeRunTab = vm.activeRunTab
            val isRunning by (activeRunTab?.backend?.isAlive ?: MutableStateFlow(false)).collectAsState()

            MainToolbar(
                onOpenFile = { showFileDialog = true },
                onOpenProject = { showFileDialog = true },
                onRun = {
                    val tab = vm.editorState.activeTab
                    if (tab != null) {
                        val file = tab.file
                        val cmd = if (file.extension.lowercase() in listOf("صخر", "sakhr")) {
                            arrayOf(SakhrConfig.COMPILER_NAME, "شغل", file.absolutePath)
                        } else {
                            arrayOf("echo", "لا توجد تهيئة تشغيل لهذه اللغة حالياً.")
                        }
                        layout.show(ToolWindowId.Run)
                        vm.runCommand(cmd, "تشغيل")
                    }
                },
                onStop = { vm.activeRunTab?.let { vm.stopProcess(it) } },
                onRerun = { vm.activeRunTab?.let { vm.rerunProcess(it) } },
                isRunning = isRunning,
                canRerun = vm.activeRunTab?.lastCommand != null,
                Modifier.fillMaxWidth().height(36.dp)
            )

            Row(Modifier.weight(1f).fillMaxWidth()) {
                ToolWindowStripe(Stripe.Start, layout)

                if (layout.isVisible(ToolWindowId.Project)) {
                    ProjectToolWindow(
                        project = vm.currentProject,
                        onFileSelected = { file ->
                            vm.openFile(file)
                        },
                        modifier = Modifier.width(layout.startWidth)
                    )
                    VerticalResizer(onDrag = { layout.resizeStart(it) })
                }

                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        EditorArea(vm.editorState, Modifier.fillMaxSize())
                        
                        if (vm.editorState.showZoomPopup) {
                            ZoomPopup(
                                fontSize = vm.editorState.fontSize.toInt(),
                                onUndo = { vm.editorState.undoZoom() },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                            )
                        }
                    }

                    if (layout.isVisible(ToolWindowId.Run) || layout.isVisible(ToolWindowId.Problems)) {
                        HorizontalResizer(onDrag = { layout.resizeBottom(it) })
                        Box(Modifier.height(layout.bottomHeight).fillMaxWidth()) {
                            if (layout.isVisible(ToolWindowId.Run)) {
                                RunPanel(vm, Modifier.fillMaxSize())
                            }
                            if (layout.isVisible(ToolWindowId.Problems)) {
                                rtlide.shell.toolwindow.ProblemsPanel(vm.editorState, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }

            ToolWindowStripe(Stripe.Bottom, layout)
            
            val activeTab = vm.editorState.activeTab
            val line = activeTab?.document?.caret?.line ?: 0
            val col = activeTab?.document?.caret?.col ?: 0
            val langName = activeTab?.lang?.displayName ?: "لا يوجد"
            
            StatusBar(line, col, langName, Modifier.fillMaxWidth().height(24.dp))
        }
    }
    
    if (showFileDialog) {
        ComposeFileDialog(
            state = filePickerState,
            onDismiss = { showFileDialog = false }
        )
    }
}

@Composable
fun ZoomPopup(fontSize: Int, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(IdeColors.TabInactiveBackground, RoundedCornerShape(4.dp))
            .border(1.dp, IdeColors.BorderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "حجم الخط: $fontSize%",
                color = IdeColors.TextDefault,
                fontSize = 13.sp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "تراجع",
                color = Color(0xFF4EA9FF),
                fontSize = 13.sp,
                modifier = Modifier.clickable { onUndo() }
            )
        }
    }
}
