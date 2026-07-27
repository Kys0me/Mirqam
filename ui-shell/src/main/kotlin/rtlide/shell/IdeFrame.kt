package rtlide.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rtlide.components.filepicker.ComposeFileDialog
import rtlide.components.filepicker.FilePickerState
import rtlide.core.document.Document
import rtlide.core.project.Project
import rtlide.core.project.RecentProjectsStore
import rtlide.editor.EditorArea
import rtlide.lang.SakhrLang
import rtlide.lang.highlight.Highlighter
import rtlide.lang.tokenizer.Tokenizer
import rtlide.shell.frame.HorizontalResizer
import rtlide.shell.frame.MainToolbar
import rtlide.shell.frame.StatusBar
import rtlide.shell.frame.VerticalResizer
import rtlide.shell.frame.WelcomeView
import rtlide.shell.keymap.KeymapController
import rtlide.shell.toolwindow.IdeLayoutState
import rtlide.shell.toolwindow.ProjectToolWindow
import rtlide.shell.toolwindow.Stripe
import rtlide.shell.toolwindow.ToolWindowId
import rtlide.shell.toolwindow.ToolWindowStripe
import rtlide.terminal.pty.ShellProcessBackend
import rtlide.terminal.ui.TerminalPanel
import java.io.File

/**
 * The IntelliJ-style IDE frame. Because the whole tree runs under
 * LayoutDirection.Rtl (set in Main), the project panel resolves to the RIGHT,
 * the editor centers, and the terminal docks at the bottom — a mirrored
 * IntelliJ, achieved by layout semantics rather than hard-coded coordinates.
 */
@Composable
fun IdeFrame(keymap: KeymapController, modifier: Modifier = Modifier) {
    val layout = remember { IdeLayoutState() }
    val scope = rememberCoroutineScope()
    val terminalBackend = remember { ShellProcessBackend(scope) }

    var currentProject by remember { mutableStateOf<Project?>(null) }
    var activeFile by remember { mutableStateOf<File?>(null) }

    val lang = remember(activeFile) {
        // Currently only Sakhr is supported as a first-class citizen.
        SakhrLang.definition()
    }
    val theme = remember(activeFile) {
        SakhrLang.theme()
    }
    val highlighter = remember(lang, theme) {
        Highlighter(Tokenizer(lang.grammar), theme)
    }
    
    val doc = remember { Document(SakhrLang.SAMPLE_CODE) }
    
    var showFileDialog by remember { mutableStateOf(false) }
    val filePickerState = remember {
        FilePickerState(onFileSelected = { file ->
            if (file != null) {
                if (file.isDirectory) {
                    val project = Project(file.name, file.absolutePath)
                    RecentProjectsStore.addProject(project)
                    currentProject = project
                } else {
                    activeFile = file
                    doc.setText(file.readText())
                }
            }
            showFileDialog = false
        })
    }

    LaunchedEffect(Unit) {
        terminalBackend.start()
    }

    LaunchedEffect(keymap, layout) {
        keymap.bind("ToggleProjectView") { layout.toggle(ToolWindowId.Project) }
        keymap.bind("ToggleTerminal") { layout.toggle(ToolWindowId.Terminal) }
        keymap.bind("HideAllWindows") { layout.hideAllToolWindows() }
    }

    if (currentProject == null && activeFile == null) {
        WelcomeView(
            onNewProject = { showFileDialog = true },
            onOpenProject = { showFileDialog = true },
            onProjectSelected = { 
                println("Project selected from welcome: ${it.path}")
                currentProject = it 
            },
            modifier = modifier
        )
    } else {
        Column(modifier.fillMaxSize()) {
            MainToolbar(
                onOpenFile = { showFileDialog = true },
                onOpenProject = { showFileDialog = true },
                onRun = {
                    val file = activeFile
                    if (file != null) {
                        val cmd = if (file.extension.lowercase() in listOf("صخر", "sakhr")) {
                            "sakhr شغل \"${file.absolutePath}\""
                        } else {
                            "echo \"لا توجد تهيئة تشغيل لهذه اللغة حالياً.\""
                        }
                        layout.show(ToolWindowId.Terminal)
                        terminalBackend.write(cmd + "\n")
                    }
                },
                Modifier.fillMaxWidth().height(36.dp)
            )

            Row(Modifier.weight(1f).fillMaxWidth()) {
                ToolWindowStripe(Stripe.Start, layout)

                if (layout.isVisible(ToolWindowId.Project)) {
                    ProjectToolWindow(currentProject, onFileSelected = { file ->
                        println("File selected from tree: ${file.absolutePath}")
                        activeFile = file
                        val content = file.readText()
                        println("File content length: ${content.length}")
                        doc.setText(content)
                    }, Modifier.width(layout.startWidth))
                    VerticalResizer(onDrag = { layout.resizeStart(it) })
                }

                Column(Modifier.weight(1f).fillMaxHeight()) {
                    EditorArea(activeFile?.name ?: "بدون عنوان", doc, highlighter, lang, Modifier.weight(1f).fillMaxWidth())

                    if (layout.isVisible(ToolWindowId.Terminal)) {
                        HorizontalResizer(onDrag = { layout.resizeBottom(it) })
                        TerminalPanel(terminalBackend, Modifier.height(layout.bottomHeight).fillMaxWidth())
                    }
                }
            }

            ToolWindowStripe(Stripe.Bottom, layout)
            StatusBar(doc.caret.line, doc.caret.col, lang.displayName, Modifier.fillMaxWidth().height(24.dp))
        }
    }
    
    if (showFileDialog) {
        ComposeFileDialog(
            state = filePickerState,
            onDismiss = { showFileDialog = false }
        )
    }
}
