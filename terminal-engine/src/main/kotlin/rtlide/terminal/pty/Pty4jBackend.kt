package rtlide.terminal.pty

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rtlide.terminal.io.Utf8StreamDecoder
import java.nio.charset.StandardCharsets

class Pty4jBackend(
    private val scope: CoroutineScope,
    private val workDir: String = System.getProperty("user.home")
) : TerminalBackend {

    private val _output = MutableSharedFlow<String>(
        replay = 200,
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val output: SharedFlow<String> = _output.asSharedFlow()

    private val _isAlive = MutableStateFlow(false)
    override val isAlive: StateFlow<Boolean> = _isAlive.asStateFlow()

    private var process: PtyProcess? = null
    private var readerJob: Job? = null

    override fun start(command: Array<String>?) {
        close()

        val cmd = command ?: defaultShell()
        val env = HashMap(System.getenv())
        env["TERM"] = "xterm-256color"

        try {
            val builder = PtyProcessBuilder()
                .setCommand(cmd)
                .setEnvironment(env)
                .setDirectory(workDir)
            
            println("Starting PTY process: ${cmd.joinToString(" ")} in $workDir")
            process = builder.start()
            _isAlive.value = true
        } catch (e: Exception) {
            val msg = "خطأ في بدء العملية: ${e.message}"
            System.err.println(msg)
            e.printStackTrace()
            _output.tryEmit("\u001B[31m$msg\u001B[0m\n")
            _isAlive.value = false
            return
        }

        val proc = process!!
        val currentJob = scope.launch(Dispatchers.IO) {
            val decoder = Utf8StreamDecoder()
            val buffer = ByteArray(8192)
            val inputStream = proc.inputStream
            try {
                while (isActive) {
                    val n = inputStream.read(buffer)
                    if (n < 0) {
                        _output.emit("\n\u001B[33m[تم اكتمال العملية]\u001B[0m\n")
                        break
                    }
                    if (n > 0) {
                        _output.emit(decoder.decode(buffer, n))
                    }
                }
            } catch (e: Exception) {
                _output.emit("\n\u001B[31m[تم إغلاق التدفق: ${e.message}]\u001B[0m\n")
            } finally {
                if (readerJob == coroutineContext[Job]) {
                    _isAlive.value = false
                }
            }
        }
        readerJob = currentJob
    }

    override fun write(input: String) {
        val proc = process ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                proc.outputStream.write(input.toByteArray(StandardCharsets.UTF_8))
                proc.outputStream.flush()
            }
        }
    }

    override fun resize(cols: Int, rows: Int) {
        process?.winSize = WinSize(cols, rows)
    }

    override fun close() {
        readerJob?.cancel()
        process?.destroy()
        process = null
        _isAlive.value = false
    }

    override fun clear() {
        _output.tryEmit("\u000C")
    }

    private fun defaultShell(): Array<String> {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("win")) {
            arrayOf("powershell.exe")
        } else {
            val shell = System.getenv("SHELL") ?: "/bin/bash"
            arrayOf(shell, "-i")
        }
    }
}
