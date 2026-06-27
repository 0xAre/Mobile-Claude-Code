package com.zeroxare.claudemobile.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * A single interactive shell session. Prefers a real PTY ([PtyProcess]); if the
 * native library is not yet wired in, falls back to a plain process with merged
 * stdout/stderr (non-interactive — enough to validate the UI before the native
 * layer lands).
 *
 * Output bytes are emitted on [output] as they arrive; UI feeds them to the ANSI
 * renderer. Call [write] to send keystrokes, [resize] on layout changes, and
 * [close] to terminate.
 */
class TerminalSession(
    private val env: LinuxEnvironment,
    private val scope: CoroutineScope
) {
    private val _output = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    val output: SharedFlow<ByteArray> = _output

    private var stdin: OutputStream? = null
    private var pid: Int = -1
    var isInteractive: Boolean = false
        private set

    fun start(
        command: String = env.loginShell.absolutePath,
        args: Array<String> = arrayOf(command),
        rows: Int = 24,
        cols: Int = 80
    ) {
        val pty = PtyProcess.start(
            command = command,
            args = args,
            env = env.buildEnv(),
            cwd = env.homeDir.absolutePath.takeIf { env.homeDir.exists() }
                ?: env.filesDir.absolutePath,
            rows = rows,
            cols = cols
        )
        if (pty != null) {
            isInteractive = true
            pid = pty.pid
            stdin = FileOutputStream(pty.fd)
            pumpReader(FileInputStream(pty.fd))
        } else {
            startFallback(command, args)
        }
    }

    /** Non-interactive fallback used until the native PTY is enabled. */
    private fun startFallback(command: String, args: Array<String>) {
        isInteractive = false
        scope.launch(Dispatchers.IO) {
            runCatching {
                val pb = ProcessBuilder(listOf(command) + args.drop(1))
                    .directory(env.filesDir)
                    .redirectErrorStream(true)
                pb.environment().putAll(
                    env.buildEnv().associate { it.substringBefore('=') to it.substringAfter('=') }
                )
                val proc = pb.start()
                stdin = proc.outputStream
                pumpReader(proc.inputStream)
                proc.waitFor()
            }.onFailure {
                _output.tryEmit(("\r\n[session error: ${it.message}]\r\n").toByteArray())
            }
        }
    }

    private fun pumpReader(input: java.io.InputStream) {
        scope.launch(Dispatchers.IO) {
            val buf = ByteArray(4096)
            try {
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    _output.emit(buf.copyOf(n))
                }
            } catch (_: Throwable) {
                // stream closed
            }
        }
    }

    fun write(text: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                stdin?.apply { write(text.toByteArray()); flush() }
            }
        }
    }

    fun resize(rows: Int, cols: Int) {
        // Only meaningful for the PTY path.
    }

    fun close() {
        runCatching { stdin?.close() }
        if (pid > 0) runCatching { android.os.Process.killProcess(pid) }
    }
}
