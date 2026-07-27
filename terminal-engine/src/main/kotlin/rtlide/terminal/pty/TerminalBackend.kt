package rtlide.terminal.pty

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Abstraction so the UI does not care whether it talks to ProcessBuilder or a
 *  real PTY. Implement this with pty4j for full terminal semantics. */
interface TerminalBackend {
    val output: SharedFlow<String>
    val isAlive: StateFlow<Boolean>
    
    fun start(command: Array<String>? = null)
    fun write(input: String)
    fun resize(cols: Int, rows: Int)
    fun close()
    fun clear()
}
