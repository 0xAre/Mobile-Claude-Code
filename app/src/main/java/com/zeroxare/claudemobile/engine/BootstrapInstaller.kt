package com.zeroxare.claudemobile.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Installs the Linux userland ("bootstrap") into [LinuxEnvironment.prefixDir] on
 * first launch, Termux-style: extract a per-ABI archive, recreate symlinks, set
 * the executable bit on binaries.
 *
 * ## The bootstrap archive (the one external dependency)
 * The archive contains a minimal userland plus Node.js and the Claude Code CLI.
 * It is CPU-architecture specific (arm64-v8a, armeabi-v7a, x86_64) and is NOT
 * committed to git because of its size. It is provided at build/release time in
 * one of two ways (see docs/FASE2.md):
 *   1. Bundled as `assets/bootstrap-<abi>.zip` (offline install), or
 *   2. Downloaded on first run from a release URL ([downloadUrlFor]).
 *
 * This installer reads from assets when present; otherwise it reports
 * [Result.MissingArchive] so the UI can trigger a download or show guidance.
 */
class BootstrapInstaller(
    private val context: Context,
    private val env: LinuxEnvironment
) {
    sealed interface Result {
        data object AlreadyInstalled : Result
        data class Installed(val files: Int) : Result
        data object MissingArchive : Result
        data class Failed(val error: Throwable) : Result
    }

    private val abi: String
        get() = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    private val assetName: String get() = "bootstrap-$abi.zip"

    /** Where a release-hosted bootstrap would be fetched from (Fase 5 wires this). */
    fun downloadUrlFor(version: String): String =
        "https://github.com/0xAre/Mobile-Claude-Code/releases/download/$version/$assetName"

    suspend fun install(): Result = withContext(Dispatchers.IO) {
        if (env.isBootstrapInstalled) return@withContext Result.AlreadyInstalled
        env.ensureDirs()

        val available = runCatching { context.assets.list("")?.contains(assetName) == true }
            .getOrDefault(false)
        if (!available) return@withContext Result.MissingArchive

        try {
            var count = 0
            context.assets.open(assetName).use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val out = File(env.prefixDir, entry.name)
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zip.copyTo(it) }
                            // Binaries under bin/ and lib/ must be executable.
                            if (out.parentFile?.name in setOf("bin", "lib", "libexec")) {
                                out.setExecutable(true, false)
                            }
                            count++
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            applySymlinks()
            env.bootstrapMarker.writeText(BOOTSTRAP_VERSION)
            Result.Installed(count)
        } catch (t: Throwable) {
            Result.Failed(t)
        }
    }

    /**
     * Termux ships a SYMLINKS.txt listing symlinks to recreate after extraction
     * (zip can't store them portably). Parsed here if present.
     */
    private fun applySymlinks() {
        val manifest = File(env.prefixDir, "SYMLINKS.txt")
        if (!manifest.exists()) return
        manifest.forEachLine { line ->
            val (target, linkPath) = line.split("←", "<-").let {
                if (it.size == 2) it[0].trim() to it[1].trim() else return@forEachLine
            }
            val link = File(env.prefixDir, linkPath)
            runCatching {
                link.parentFile?.mkdirs()
                if (link.exists()) link.delete()
                android.system.Os.symlink(target, link.absolutePath)
            }
        }
    }

    companion object {
        const val BOOTSTRAP_VERSION = "1"
    }
}
