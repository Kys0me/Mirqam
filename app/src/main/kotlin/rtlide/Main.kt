package rtlide

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import rtlide.shell.IdeFrame
import rtlide.shell.keymap.KeymapController
import rtlide.shell.theme.IdeTheme

fun main() = application {
    val keymap = remember { KeymapController.intellijDefaults() }
    val windowState = rememberWindowState(size = DpSize(1360.dp, 860.dp))

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "مٍرقام",
        // Global shortcuts (Alt+1, Alt+F12, double-Shift…) are seen first here.
        onPreviewKeyEvent = { keymap.dispatch(it) },
    ) {
        // The one switch that makes the entire IDE right-to-left.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            IdeTheme(dark = true) {
                IdeFrame(keymap)
            }
        }
    }
}
