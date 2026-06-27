package com.zeroxare.claudemobile.engine

import android.content.Context
import java.io.File

/**
 * Describes the on-device Linux-like environment that the real terminal + Claude
 * Code CLI run inside. Mirrors Termux's layout (a self-contained prefix under the
 * app's private data dir) so prebuilt packages that expect `/data/data/<pkg>/...`
 * style absolute paths work after the bootstrap is installed.
 *
 * NOTE (Fase 2 scaffold): paths and helpers are defined here; the binaries that
 * populate [prefixDir] (busybox/bash, node, npm, claude) are supplied by the
 * bootstrap archive — see [BootstrapInstaller] and docs/FASE2.md.
 */
class LinuxEnvironment(context: Context) {

    /** Root of the app's private files dir, e.g. /data/data/<pkg>/files */
    val filesDir: File = context.filesDir

    /** Equivalent of Termux's $PREFIX (/usr): bin, lib, etc. live here. */
    val prefixDir: File = File(filesDir, "usr")

    /** $HOME inside the environment. */
    val homeDir: File = File(filesDir, "home")

    /** Where the bootstrap archive is extracted / version-stamped. */
    val bootstrapMarker: File = File(prefixDir, ".bootstrap_version")

    val binDir: File get() = File(prefixDir, "bin")
    val tmpDir: File get() = File(prefixDir, "tmp")

    /** Login shell to launch; falls back to the system shell pre-bootstrap. */
    val loginShell: File
        get() = File(binDir, "bash").takeIf { it.exists() }
            ?: File(binDir, "sh").takeIf { it.exists() }
            ?: File("/system/bin/sh")

    /** Is a usable environment already installed? */
    val isBootstrapInstalled: Boolean
        get() = bootstrapMarker.exists() && binDir.isDirectory

    /**
     * Environment variables for spawned processes. Matches what Claude Code and
     * node expect on a Termux-style prefix.
     */
    fun buildEnv(extra: Map<String, String> = emptyMap()): Array<String> {
        val env = linkedMapOf(
            "HOME" to homeDir.absolutePath,
            "PREFIX" to prefixDir.absolutePath,
            "PATH" to "${binDir.absolutePath}:/system/bin:/system/xbin",
            "TMPDIR" to tmpDir.absolutePath,
            "LANG" to "en_US.UTF-8",
            "TERM" to "xterm-256color",
            // Claude Code authenticates with your Claude account via OAuth; no API
            // key is injected here. The CLI stores its own credentials under HOME.
            "LD_LIBRARY_PATH" to File(prefixDir, "lib").absolutePath
        )
        env.putAll(extra)
        return env.map { (k, v) -> "$k=$v" }.toTypedArray()
    }

    fun ensureDirs() {
        listOf(prefixDir, homeDir, binDir, tmpDir).forEach { it.mkdirs() }
    }
}
