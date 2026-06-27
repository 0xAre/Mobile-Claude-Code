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
import com.zeroxare.claudemobile.terminal.AnsiParser
import kotlinx.coroutines.launch

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

    private val lineBuf = StringBuilder()

    fun startSession() {
        if (session != null) return
        env.ensureDirs()
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

    private fun appendBytes(bytes: ByteArray) = appendText(String(bytes, Charsets.UTF_8))

    private fun appendText(text: String) {
        for (ch in text) {
            when (ch) {
                '\n' -> flushLine()
                '\r' -> lineBuf.setLength(0)
                else -> lineBuf.append(ch)
            }
        }
        currentLine.value = AnsiParser.parse(lineBuf.toString())
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

    /** Install the bootstrap (Node + Claude Code), then run the CLI setup. */
    fun setupClaude() {
        viewModelScope.launch {
            appendSystem("Memeriksa environment…")
            when (val r = bootstrap.install()) {
                is BootstrapInstaller.Result.AlreadyInstalled,
                is BootstrapInstaller.Result.Installed -> {
                    appendSystem("Bootstrap siap. Menjalankan setup Claude Code…")
                    ClaudeLauncher.setupCommands.forEach { session?.write("$it\n") }
                }
                BootstrapInstaller.Result.MissingArchive ->
                    appendSystem(
                        "Bootstrap (Node + Claude Code) belum tersedia di build ini.\n" +
                        "Lihat docs/FASE2.md untuk menambahkan bootstrap-<abi>.zip\n" +
                        "atau URL unduhan rilis."
                    )
                is BootstrapInstaller.Result.Failed ->
                    appendSystem("Gagal memasang bootstrap: ${r.error.message}")
            }
        }
    }

    /** Start the official Claude Code login (OAuth with your Claude account). */
    fun login() {
        appendSystem("Menjalankan: claude (login akun)…")
        session?.write(ClaudeLauncher.launchCommand() + "\n")
    }

    override fun onCleared() {
        session?.close()
        super.onCleared()
    }
}
