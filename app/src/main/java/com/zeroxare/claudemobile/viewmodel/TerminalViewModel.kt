package com.zeroxare.claudemobile.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroxare.claudemobile.engine.BootstrapInstaller
import com.zeroxare.claudemobile.engine.ClaudeLauncher
import com.zeroxare.claudemobile.engine.LinuxEnvironment
import com.zeroxare.claudemobile.engine.TerminalSession
import com.zeroxare.claudemobile.service.TerminalService
import com.zeroxare.claudemobile.terminal.AnsiParser
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.CharBuffer

/**
 * Drives the real (PTY-backed) terminal screen: starts a shell session, renders
 * its byte stream into colored lines, sends keystrokes, and orchestrates the
 * Claude Code setup + account-login flow.
 */
class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    private val env = LinuxEnvironment(app)
    private val bootstrap = BootstrapInstaller(app, env)
    private var session: TerminalSession? = null

    /** ANSI escape (ESC) as a Kotlin-safe constant. */
    private val esc = '\u001B'

    /** Finalized scrollback lines. */
    val lines = mutableStateListOf<AnnotatedString>()
    /** The in-progress (not yet newline-terminated) line. */
    val currentLine = mutableStateOf(AnnotatedString(""))
    val input = mutableStateOf("")
    val interactive = mutableStateOf(false)
    val status = mutableStateOf("starting…")
    val fontSize = mutableStateOf(13)

    fun fontInc() { fontSize.value = (fontSize.value + 1).coerceAtMost(28) }
    fun fontDec() { fontSize.value = (fontSize.value - 1).coerceAtLeast(8) }

    private val lineBuf = StringBuilder()

    private companion object {
        /** GitHub Release tag that hosts the bootstrap-<abi>.zip archives. */
        const val BOOTSTRAP_TAG = "bootstrap-v1"
    }

    fun startSession() {
        if (session != null) return
        env.ensureDirs()
        TerminalService.start(getApplication())
        val s = TerminalSession(env, viewModelScope)
        session = s
        // Begin collecting before start() so we don't miss early output.
        viewModelScope.launch { s.output.collect { appendBytes(it) } }
        s.start(rows = 24, cols = 80)
        interactive.value = s.isInteractive
        status.value = if (s.isInteractive) "interactive (PTY)" else "basic (no PTY yet)"
        appendSystem(
            "Claude Terminal — Fase 2\n" +
            "Shell siap. Tekan 'Setup Claude' untuk memasang Node + Claude Code,\n" +
            "lalu 'Login' untuk masuk dengan akun Claude-mu.\n"
        )
    }

    // Incremental UTF-8 decoder state: bytes of an incomplete trailing
    // multi-byte sequence are carried over to the next chunk so characters
    // split across reads don't get corrupted.
    private var carry = ByteArray(0)

    private fun appendBytes(bytes: ByteArray) {
        val combined = carry + bytes
        val bb = ByteBuffer.wrap(combined)
        val cb = CharBuffer.allocate(combined.size + 1)
        Charsets.UTF_8.newDecoder().decode(bb, cb, false)
        cb.flip()
        carry = ByteArray(bb.remaining()).also { bb.get(it) }
        appendText(cb.toString())
    }

    // Minimal ANSI state machine (persists across chunks, since an escape
    // sequence can be split between reads). Handles SGR colors (kept for the
    // renderer), erase-line/clear-screen, backspace and tabs; other cursor-
    // movement sequences are consumed so they don't leak as garbage text.
    private var escState = 0 // 0 = normal, 1 = saw ESC, 2 = inside CSI
    private val csi = StringBuilder()

    private fun appendText(text: String) {
        for (ch in text) {
            when (escState) {
                0 -> when {
                    ch == esc -> escState = 1
                    ch == '\n' -> flushLine()
                    ch == '\r' -> lineBuf.setLength(0)
                    ch == '\b' -> if (lineBuf.isNotEmpty()) lineBuf.deleteCharAt(lineBuf.length - 1)
                    ch == '\t' -> lineBuf.append("    ")
                    ch.code < 32 -> { /* ignore other C0 control chars */ }
                    else -> lineBuf.append(ch)
                }
                1 -> {
                    if (ch == '[') { escState = 2; csi.setLength(0) }
                    else escState = 0 // ignore non-CSI escapes (e.g. ESC ] OSC)
                }
                2 -> {
                    csi.append(ch)
                    if (ch in '@'..'~') { // final byte of the CSI sequence
                        handleCsi(csi.toString())
                        escState = 0
                    }
                }
            }
        }
        currentLine.value = AnsiParser.parse(lineBuf.toString())
    }

    private fun handleCsi(seq: String) {
        when {
            // SGR (colors/styles): keep so AnsiParser can render it.
            seq.endsWith('m') -> lineBuf.append(esc).append('[').append(seq)
            // Erase in display: 2J clears the screen.
            seq.endsWith('J') -> if (seq.startsWith("2")) { lines.clear(); lineBuf.setLength(0) }
            // Erase in line: clear the current line buffer.
            seq.endsWith('K') -> lineBuf.setLength(0)
            // Cursor movement / others: consume without emitting.
            else -> { /* ignored */ }
        }
    }

    private fun flushLine() {
        lines.add(AnsiParser.parse(lineBuf.toString()))
        lineBuf.setLength(0)
        if (lines.size > 5000) lines.removeAt(0)
    }

    private fun appendSystem(msg: String) = appendText("$esc[36m$msg$esc[0m\n")

    /** Send the current input line to the shell. */
    fun submitInput() {
        val text = input.value
        session?.write(text + "\n")
        if (session?.isInteractive != true) {
            // No TTY echo in fallback mode — echo locally so the user sees it.
            appendText("$text\n")
        }
        input.value = ""
    }

    /** Send a raw control/escape sequence (special-key bar). */
    fun sendRaw(seq: String) {
        session?.write(seq)
    }

    /** Update the PTY window size when the on-screen terminal area changes. */
    fun resize(rows: Int, cols: Int) {
        session?.resize(rows, cols)
    }

    /** Clear the on-screen scrollback (does not affect the shell). */
    fun clear() {
        lines.clear()
        lineBuf.setLength(0)
        currentLine.value = AnnotatedString("")
    }

    /** Install the bootstrap (Node + Claude Code), then run the CLI setup. */
    fun setupClaude() {
        viewModelScope.launch {
            appendSystem("Memeriksa environment…")
            when (val r = bootstrap.install()) {
                is BootstrapInstaller.Result.AlreadyInstalled,
                is BootstrapInstaller.Result.Installed -> runClaudeSetup()
                BootstrapInstaller.Result.MissingArchive -> {
                    appendSystem("Bootstrap tidak ada di APK — mencoba mengunduh dari Releases…")
                    when (val d = bootstrap.installFromUrl(bootstrap.downloadUrlFor(BOOTSTRAP_TAG))) {
                        is BootstrapInstaller.Result.AlreadyInstalled,
                        is BootstrapInstaller.Result.Installed -> runClaudeSetup()
                        BootstrapInstaller.Result.MissingArchive ->
                            appendSystem(
                                "Bootstrap belum dipublikasikan di Releases (tag '$BOOTSTRAP_TAG').\n" +
                                "Lihat docs/FASE2.md untuk membuat & meng-upload bootstrap-<abi>.zip."
                            )
                        is BootstrapInstaller.Result.Failed ->
                            appendSystem("Gagal mengunduh bootstrap: ${d.error.message}")
                    }
                }
                is BootstrapInstaller.Result.Failed ->
                    appendSystem("Gagal memasang bootstrap: ${r.error.message}")
            }
        }
    }

    private fun runClaudeSetup() {
        appendSystem("Bootstrap siap. Menjalankan setup Claude Code…")
        ClaudeLauncher.setupCommands.forEach { session?.write("$it\n") }
    }

    /** Start the official Claude Code login (OAuth with your Claude account). */
    fun login() {
        appendSystem("Menjalankan: claude (login akun)…")
        session?.write(ClaudeLauncher.launchCommand() + "\n")
    }

    override fun onCleared() {
        session?.close()
        TerminalService.stop(getApplication())
        super.onCleared()
    }
}
