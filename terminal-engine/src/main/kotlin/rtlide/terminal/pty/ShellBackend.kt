package rtlide.terminal.pty

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rtlide.terminal.io.Utf8StreamDecoder

/** Abstraction so the UI does not care whether it talks to ProcessBuilder or a
 *  real PTY. Implement this with pty4j for full terminal semantics. */
interface TerminalBackend {
    val output: SharedFlow<String>
    fun start()
    fun write(input: String)
    fun resize(cols: Int, rows: Int)
    fun close()
}

/**
 * Pure-JVM backend using ProcessBuilder — zero native dependencies, so the
 * project builds and runs anywhere a JDK is present. Streams stdout+stderr
 * through the UTF-8 stream decoder so multi-byte Arabic output is never split.
 *
 * Caveat: this is NOT a controlling tty. Programs that check isatty() or need
 * job control behave differently. For a real terminal, provide a
 * Pty4jBackend : TerminalBackend and swap it in TerminalPanel.
 */
class ShellProcessBackend(private val scope: CoroutineScope) : TerminalBackend {

    private val _output = MutableSharedFlow<String>(
        replay = 200,
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val output: SharedFlow<String> = _output.asSharedFlow()

    private var process: Process? = null
    private var readerJob: Job? = null

    override fun start() {
        if (process != null) return
        val builder = ProcessBuilder(*defaultShell()).redirectErrorStream(true)
        builder.environment()["TERM"] = "xterm-256color"
        val proc = try {
            builder.start()
        } catch (ex: Exception) {
            _output.tryEmit("\u001B[31mتعذّر تشغيل الصدفة: ${ex.message}\u001B[0m\n")
            return
        }
        process = proc
        readerJob = scope.launch(Dispatchers.IO) {
            val decoder = Utf8StreamDecoder()
            val buffer = ByteArray(8192)
            val stream = proc.inputStream
            try {
                while (isActive) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    if (n > 0) _output.emit(decoder.decode(buffer, n))
                }
            } catch (_: Exception) {
                // stream closed on process exit
            }
        }
    }

    override fun write(input: String) {
        val proc = process ?: return
        scope.launch(Dispatchers.IO) {
            try {
                proc.outputStream.apply {
                    write(input.toByteArray(Charsets.UTF_8))
                    flush()
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun resize(cols: Int, rows: Int) {
        // No-op: a real PTY (pty4j) is required to change the window size.
    }

    override fun close() {
        readerJob?.cancel()
        process?.destroy()
        process = null
    }

    private fun defaultShell(): Array<String> {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("win")) arrayOf("cmd.exe")
        else arrayOf(System.getenv("SHELL") ?: "/bin/sh", "-i")
    }
}
