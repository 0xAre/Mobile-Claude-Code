package com.zeroxare.claudemobile.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
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
            val count = context.assets.open(assetName).use { raw ->
                extractZip(raw.buffered())
            }
            finishInstall()
            Result.Installed(count)
        } catch (t: Throwable) {
            Result.Failed(t)
        }
    }

    /**
     * Download a bootstrap archive from [url] and install it. Used when the
     * archive isn't bundled in assets (keeps the APK small). Default URL points
     * at this repo's GitHub Releases — publish `bootstrap-<abi>.zip` there.
     */
    suspend fun installFromUrl(url: String): Result = withContext(Dispatchers.IO) {
        if (env.isBootstrapInstalled) return@withContext Result.AlreadyInstalled
        env.ensureDirs()
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext if (resp.code == 404) Result.MissingArchive
                    else Result.Failed(IOException("HTTP ${resp.code} fetching bootstrap"))
                }
                val stream = resp.body?.byteStream()
                    ?: return@withContext Result.Failed(IOException("empty response body"))
                val count = extractZip(stream.buffered())
                finishInstall()
                Result.Installed(count)
            }
        } catch (t: Throwable) {
            Result.Failed(t)
        }
    }

    private fun extractZip(input: InputStream): Int {
        var count = 0
        ZipInputStream(input).use { zip ->
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
        return count
    }

    private fun finishInstall() {
        applySymlinks()
        relocatePaths()
        env.bootstrapMarker.writeText(BOOTSTRAP_VERSION)
    }

    /**
     * Termux binaries/scripts are built for the prefix `/data/data/com.termux/
     * files/...`. This app uses a different package, so rewrite those hardcoded
     * paths (script shebangs, configs, wrappers) to our prefix. ELF/binary files
     * are skipped — they resolve their libs via LD_LIBRARY_PATH (set in
     * [LinuxEnvironment.buildEnv]); only text files are relocated.
     */
    private fun relocatePaths() {
        val ourUsr = env.prefixDir.absolutePath
        val ourHome = env.homeDir.absolutePath
        env.prefixDir.walkTopDown().filter { it.isFile && !isSymlink(it) }.forEach { f ->
            if (f.length() == 0L || f.length() > 4_000_000L) return@forEach
            val head = ByteArray(minOf(8192, f.length().toInt()))
            val read = try { f.inputStream().use { it.read(head) } } catch (_: Exception) { return@forEach }
            if (read <= 0) return@forEach
            // Skip binaries: ELF magic or any NUL byte in the sampled head.
            val isElf = read >= 4 && head[0] == 0x7F.toByte() &&
                head[1] == 'E'.code.toByte() && head[2] == 'L'.code.toByte() && head[3] == 'F'.code.toByte()
            if (isElf || head.take(read).any { it.toInt() == 0 }) return@forEach
            val text = try { f.readText() } catch (_: Exception) { return@forEach }
            if (!text.contains(TERMUX_PREFIX) && !text.contains(TERMUX_HOME)) return@forEach
            val fixed = text.replace(TERMUX_PREFIX, ourUsr).replace(TERMUX_HOME, ourHome)
            if (fixed != text) {
                val wasExec = f.canExecute()
                try { f.writeText(fixed) } catch (_: Exception) { return@forEach }
                if (wasExec) f.setExecutable(true, false)
            }
        }
    }

    private fun isSymlink(f: File): Boolean =
        runCatching { f.canonicalFile != f.absoluteFile }.getOrDefault(false)

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
        private const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
        private const val TERMUX_HOME = "/data/data/com.termux/files/home"
    }
}
