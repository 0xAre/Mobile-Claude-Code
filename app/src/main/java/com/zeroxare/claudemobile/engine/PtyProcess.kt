package com.zeroxare.claudemobile.engine

import android.system.Os
import java.io.FileDescriptor

/**
 * Thin JNI bridge to a native PTY, modeled on Termux's terminal-emulator. A real
 * pseudo-terminal is required for interactive programs (bash, vim, and crucially
 * the `claude` CLI's OAuth login prompt) — Android's ProcessBuilder cannot do
 * this because it provides pipes, not a controlling terminal.
 *
 * The native side (`app/src/main/cpp/pty.c`) calls forkpty(3), exec's the target,
 * and returns the master fd + child pid. Reading/writing the returned
 * [FileDescriptor] is the terminal I/O channel; [setWindowSize] keeps the TTY
 * size in sync with the on-screen rows/cols.
 *
 * ## Fase 2 scaffold status
 * The Kotlin bridge is defined and compiles. The native library is committed
 * under cpp/ but is NOT yet wired into the Gradle build (no externalNativeBuild),
 * so [isAvailable] returns false until that is enabled (see docs/FASE2.md).
 * Until then callers fall back to a non-interactive engine.
 */
object PtyProcess {

    @Volatile private var loaded = false

    /** True once the native terminal library is present and loaded. */
    fun isAvailable(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("claudepty")
            loaded = true
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Holds the result of starting a subprocess on a PTY. */
    class Subprocess(
        val fd: FileDescriptor,
        val pid: Int
    )

    /**
     * Fork a child on a new PTY and exec [command] with [args] and [env].
     * @return master fd + child pid, or null if the native lib is unavailable.
     */
    fun start(
        command: String,
        args: Array<String>,
        env: Array<String>,
        cwd: String,
        rows: Int,
        cols: Int
    ): Subprocess? {
        if (!isAvailable()) return null
        val pid = IntArray(1)
        val fd = nativeCreateSubprocess(command, cwd, args, env, pid, rows, cols)
        if (fd < 0) return null
        return Subprocess(intToFd(fd), pid[0])
    }

    fun setWindowSize(fd: FileDescriptor, rows: Int, cols: Int) {
        if (!isAvailable()) return
        nativeSetWinSize(fdToInt(fd), rows, cols)
    }

    fun waitFor(pid: Int): Int = if (isAvailable()) nativeWaitFor(pid) else -1

    // --- native declarations (implemented in cpp/pty.c) ---
    private external fun nativeCreateSubprocess(
        cmd: String, cwd: String, args: Array<String>, env: Array<String>,
        outPid: IntArray, rows: Int, cols: Int
    ): Int
    private external fun nativeSetWinSize(fd: Int, rows: Int, cols: Int)
    private external fun nativeWaitFor(pid: Int): Int

    // FileDescriptor <-> int helpers (Os.dup gives us a real FD object).
    private fun intToFd(fd: Int): FileDescriptor =
        FileDescriptor().also { setFdInt(it, fd) }

    private fun fdToInt(fd: FileDescriptor): Int =
        runCatching { getFdInt(fd) }.getOrDefault(-1)

    private fun setFdInt(fileDescriptor: FileDescriptor, fd: Int) {
        val f = FileDescriptor::class.java.getDeclaredField("descriptor")
        f.isAccessible = true
        f.setInt(fileDescriptor, fd)
    }

    private fun getFdInt(fileDescriptor: FileDescriptor): Int {
        val f = FileDescriptor::class.java.getDeclaredField("descriptor")
        f.isAccessible = true
        return f.getInt(fileDescriptor)
    }

    // Referenced so the import is used even before native wiring; harmless.
    @Suppress("unused")
    private val osRef = Os::class
}
