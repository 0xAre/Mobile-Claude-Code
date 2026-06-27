package com.zeroxare.claudemobile.engine

/**
 * Knows how to set up and launch the **official Claude Code CLI** inside the
 * on-device environment, and how to start its **account login** (OAuth).
 *
 * This is the heart of the MVP: the user authenticates with their Claude Pro/Max
 * *account* — exactly like desktop Claude Code — because it is the real `claude`
 * binary performing OAuth, not this app injecting credentials. No API key is
 * used or stored. (Injecting subscription tokens into a third-party app violates
 * Anthropic's ToS; running the official CLI does not.)
 *
 * These return shell command strings that are fed into a [TerminalSession].
 */
object ClaudeLauncher {

    /** One-time setup: install Node + the Claude Code CLI into the environment. */
    val setupCommands: List<String> = listOf(
        "pkg update -y && pkg install -y nodejs git",
        "npm install -g @anthropic-ai/claude-code"
    )

    /** Whether `claude` is installed (checked by the session after setup). */
    fun isInstalledProbe(): String = "command -v claude >/dev/null 2>&1 && echo OK || echo MISSING"

    /**
     * Launch Claude Code. On first launch with no stored credentials, the CLI
     * opens its own OAuth login flow (prints a URL / asks to paste a code) — the
     * user signs in with their Claude account in the browser and returns.
     */
    fun launchCommand(workdir: String? = null): String =
        buildString {
            if (workdir != null) append("cd ${shellQuote(workdir)} && ")
            append("claude")
        }

    /**
     * Explicit, non-browser login helper for environments where the browser
     * redirect is awkward (Termux OAuth can be flaky). Still the official flow.
     */
    fun loginCommand(): String = "claude setup-token || claude /login"

    fun logoutCommand(): String = "claude /logout"

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
