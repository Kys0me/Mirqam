package rtlide.shell.keymap

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** A key chord. Bindings stay direction-neutral so IntelliJ muscle memory
 *  survives the RTL layout flip — only the UI mirrors, not the shortcuts. */
data class Shortcut(
    val key: Key,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
)

class KeymapController(private val bindings: Map<Shortcut, String>) {

    private val actions = HashMap<String, () -> Unit>()
    private var lastShiftAt = 0L

    fun bind(actionId: String, handler: () -> Unit) {
        actions[actionId] = handler
    }

    /** Returns true if the event was consumed. Wire to Window.onPreviewKeyEvent. */
    fun dispatch(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false

        // Double-Shift => Search Everywhere (tap detection).
        if (e.key == Key.ShiftLeft || e.key == Key.ShiftRight) {
            val now = System.currentTimeMillis()
            val isDouble = now - lastShiftAt < 300
            lastShiftAt = now
            if (isDouble) {
                val handler = actions["SearchEverywhere"]
                if (handler != null) { handler(); return true }
            }
            return false
        }

        val chord = Shortcut(e.key, e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, e.isMetaPressed)
        val actionId = bindings[chord] ?: return false
        val handler = actions[actionId] ?: return false
        handler()
        return true
    }

    companion object {
        fun intellijDefaults(): KeymapController = KeymapController(
            mapOf(
                Shortcut(Key.One, alt = true) to "ToggleProjectView",   // Alt+1
                Shortcut(Key.F12, alt = true) to "ToggleTerminal",       // Alt+F12
                Shortcut(Key.Escape, shift = true) to "HideAllWindows",  // Shift+Esc
                Shortcut(Key.A, ctrl = true, shift = true) to "FindAction",   // Ctrl+Shift+A
                Shortcut(Key.F, ctrl = true, shift = true) to "FindInFiles",  // Ctrl+Shift+F
                Shortcut(Key.N, ctrl = true) to "GoToFile",              // Ctrl+N
            )
        )
    }
}
