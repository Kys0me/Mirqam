package rtlide.terminal.pb

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
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * A lightweight backend using java.lang.ProcessBuilder.
 */
class ProcessBackend(
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

    private var process: Process? = null
    private var jobs = mutableListOf<Job>()

    override fun start(command: Array<String>?) {
        close()
        val cmd = command ?: defaultShell()
        
        try {
            val builder = ProcessBuilder(*cmd)
                .directory(File(workDir))
                .redirectErrorStream(true) // Merge stdout and stderr
            
            println("Starting Process: ${cmd.joinToString(" ")} in $workDir")
            val proc = builder.start()
            process = proc
            _isAlive.value = true

            // Reader job
            jobs.add(scope.launch(Dispatchers.IO) {
                val decoder = Utf8StreamDecoder()
                val buffer = ByteArray(8192)
                val inputStream = proc.inputStream
                try {
                    while (isActive) {
                        val n = inputStream.read(buffer)
                        if (n < 0) break
                        if (n > 0) {
                            _output.emit(decoder.decode(buffer, n))
                        }
                    }
                } catch (_: Exception) {
                    // Stream closed
                } finally {
                    _isAlive.value = false
                    _output.emit("\n\u001B[33m[انتهت العملية]\u001B[0m\n")
                }
            })

        } catch (e: Exception) {
            val msg = "خطأ في تشغيل العملية: ${e.message}"
            _output.tryEmit("\u001B[31m$msg\u001B[0m\n")
            _isAlive.value = false
        }
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
        // ProcessBuilder doesn't support terminal resizing as there is no PTY.
        // We can ignore this or set an environment variable if needed.
    }

    override fun close() {
        jobs.forEach { it.cancel() }
        jobs.clear()
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
            arrayOf("cmd.exe", "/c")
        } else {
            arrayOf("/bin/sh", "-c")
        }
    }
}
