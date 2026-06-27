package com.zeroxare.claudemobile.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class TerminalLine(
    val text: String,
    val isCommand: Boolean = false,
    val isError: Boolean = false
)

class TerminalManager(private val filesDir: File) {

    var currentDir: File = filesDir
    val commandHistory = mutableListOf<String>()
    private var historyIndex = -1

    suspend fun execute(command: String): List<TerminalLine> = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        commandHistory.add(trimmed)
        historyIndex = commandHistory.size

        val parts = trimmed.split("\\s+".toRegex())
        val cmd = parts[0]
        val args = parts.drop(1)

        when (cmd) {
            "cd" -> handleCd(args)
            "pwd" -> listOf(TerminalLine(currentDir.absolutePath))
            "ls" -> handleLs(args)
            "mkdir" -> handleMkdir(args)
            "rm" -> handleRm(args)
            "cat" -> handleCat(args)
            "echo" -> listOf(TerminalLine(args.joinToString(" ")))
            "clear" -> listOf(TerminalLine("[2J[H"))
            "help" -> showHelp()
            "touch" -> handleTouch(args)
            "cp" -> handleCp(args)
            "mv" -> handleMv(args)
            "head" -> handleHead(args)
            "tail" -> handleTail(args)
            "wc" -> handleWc(args)
            "find" -> handleFind(args)
            "grep" -> handleGrep(args)
            else -> runShellCommand(trimmed)
        }
    }

    private fun handleCd(args: List<String>): List<TerminalLine> {
        val target = args.firstOrNull() ?: filesDir.absolutePath
        val newDir = when {
            target == "~" || target.isEmpty() -> filesDir
            target == ".." -> currentDir.parentFile ?: currentDir
            target.startsWith("/") -> File(target)
            else -> File(currentDir, target)
        }
        return if (newDir.exists() && newDir.isDirectory) {
            currentDir = newDir.canonicalFile
            emptyList()
        } else {
            listOf(TerminalLine("cd: $target: No such directory", isError = true))
        }
    }

    private fun handleLs(args: List<String>): List<TerminalLine> {
        val showHidden = args.contains("-a") || args.contains("-la") || args.contains("-al")
        val longFormat = args.contains("-l") || args.contains("-la") || args.contains("-al")
        val targetArg = args.firstOrNull { !it.startsWith("-") }
        val target = if (targetArg != null) File(currentDir, targetArg) else currentDir

        if (!target.exists()) return listOf(TerminalLine("ls: $target: No such file or directory", isError = true))

        val files = target.listFiles() ?: return listOf(TerminalLine("ls: cannot read directory", isError = true))
        val filtered = if (showHidden) files else files.filter { !it.name.startsWith(".") }
        val sorted = filtered.sortedWith(compareBy({ !it.isDirectory }, { it.name }))

        return if (longFormat) {
            sorted.map { f ->
                val type = if (f.isDirectory) "d" else "-"
                val size = if (f.isDirectory) "     -" else "%6d".format(f.length())
                val name = if (f.isDirectory) "[34m${f.name}/[0m" else f.name
                TerminalLine("$type $size  $name")
            }
        } else {
            val names = sorted.joinToString("  ") { f ->
                if (f.isDirectory) "[34m${f.name}/[0m" else f.name
            }
            listOf(TerminalLine(names))
        }
    }

    private fun handleMkdir(args: List<String>): List<TerminalLine> {
        if (args.isEmpty()) return listOf(TerminalLine("mkdir: missing operand", isError = true))
        return args.flatMap { name ->
            val dir = File(currentDir, name)
            if (dir.mkdirs()) emptyList()
            else listOf(TerminalLine("mkdir: cannot create '$name'", isError = true))
        }
    }

    private fun handleRm(args: List<String>): List<TerminalLine> {
        val recursive = args.contains("-r") || args.contains("-rf") || args.contains("-fr")
        val targets = args.filter { !it.startsWith("-") }
        return targets.flatMap { name ->
            val file = File(currentDir, name)
            when {
                !file.exists() -> listOf(TerminalLine("rm: $name: No such file", isError = true))
                file.isDirectory && !recursive -> listOf(TerminalLine("rm: $name: is a directory (use -r)", isError = true))
                else -> { file.deleteRecursively(); emptyList() }
            }
        }
    }

    private fun handleCat(args: List<String>): List<TerminalLine> {
        if (args.isEmpty()) return listOf(TerminalLine("cat: missing file operand", isError = true))
        return args.flatMap { name ->
            val file = File(currentDir, name)
            if (!file.exists()) listOf(TerminalLine("cat: $name: No such file", isError = true))
            else if (file.length() > 1_000_000) listOf(TerminalLine("cat: $name: File too large", isError = true))
            else file.readLines().map { TerminalLine(it) }
        }
    }

    private fun handleTouch(args: List<String>): List<TerminalLine> {
        if (args.isEmpty()) return listOf(TerminalLine("touch: missing operand", isError = true))
        return args.flatMap { name ->
            val file = File(currentDir, name)
            if (!file.exists()) file.createNewFile()
            else file.setLastModified(System.currentTimeMillis())
            emptyList()
        }
    }

    private fun handleCp(args: List<String>): List<TerminalLine> {
        if (args.size < 2) return listOf(TerminalLine("cp: missing operand", isError = true))
        val src = File(currentDir, args[0])
        val dst = File(currentDir, args[1])
        return if (!src.exists()) listOf(TerminalLine("cp: ${args[0]}: No such file", isError = true))
        else { src.copyTo(dst, overwrite = true); emptyList() }
    }

    private fun handleMv(args: List<String>): List<TerminalLine> {
        if (args.size < 2) return listOf(TerminalLine("mv: missing operand", isError = true))
        val src = File(currentDir, args[0])
        val dst = File(currentDir, args[1])
        return if (!src.exists()) listOf(TerminalLine("mv: ${args[0]}: No such file", isError = true))
        else { src.renameTo(dst); emptyList() }
    }

    private fun handleHead(args: List<String>): List<TerminalLine> {
        val n = if (args.contains("-n")) args[args.indexOf("-n") + 1].toIntOrNull() ?: 10 else 10
        val file = File(currentDir, args.last())
        return if (!file.exists()) listOf(TerminalLine("head: ${args.last()}: No such file", isError = true))
        else file.readLines().take(n).map { TerminalLine(it) }
    }

    private fun handleTail(args: List<String>): List<TerminalLine> {
        val n = if (args.contains("-n")) args[args.indexOf("-n") + 1].toIntOrNull() ?: 10 else 10
        val file = File(currentDir, args.last())
        return if (!file.exists()) listOf(TerminalLine("tail: ${args.last()}: No such file", isError = true))
        else file.readLines().takeLast(n).map { TerminalLine(it) }
    }

    private fun handleWc(args: List<String>): List<TerminalLine> {
        val file = File(currentDir, args.last())
        return if (!file.exists()) listOf(TerminalLine("wc: ${args.last()}: No such file", isError = true))
        else {
            val lines = file.readLines()
            val words = lines.sumOf { it.split("\\s+".toRegex()).filter(String::isNotBlank).size }
            val chars = file.length()
            listOf(TerminalLine("${lines.size}\t$words\t$chars\t${args.last()}"))
        }
    }

    private fun handleFind(args: List<String>): List<TerminalLine> {
        val nameIdx = args.indexOf("-name")
        val pattern = if (nameIdx >= 0 && nameIdx + 1 < args.size) args[nameIdx + 1] else "*"
        val regex = pattern.replace(".", "\\.").replace("*", ".*").toRegex()
        return currentDir.walkTopDown()
            .filter { regex.matches(it.name) }
            .map { TerminalLine(it.relativeTo(currentDir).path) }
            .take(100)
            .toList()
    }

    private fun handleGrep(args: List<String>): List<TerminalLine> {
        if (args.size < 2) return listOf(TerminalLine("grep: missing operand", isError = true))
        val pattern = args[0]
        val file = File(currentDir, args[1])
        return if (!file.exists()) listOf(TerminalLine("grep: ${args[1]}: No such file", isError = true))
        else {
            val regex = try { pattern.toRegex() } catch (e: Exception) { pattern.toRegex(RegexOption.LITERAL) }
            file.readLines().mapIndexedNotNull { i, line ->
                if (regex.containsMatchIn(line)) TerminalLine("${i + 1}: $line") else null
            }
        }
    }

    private suspend fun runShellCommand(command: String): List<TerminalLine> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(currentDir)
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream))
                .readLines()

            val exitCode = process.waitFor()
            val lines = output.map { TerminalLine(it, isError = exitCode != 0) }

            if (lines.isEmpty() && exitCode != 0) {
                listOf(TerminalLine("Command exited with code $exitCode", isError = true))
            } else {
                lines
            }
        } catch (e: Exception) {
            listOf(TerminalLine("Error: ${e.message}", isError = true))
        }
    }

    private fun showHelp(): List<TerminalLine> {
        return listOf(
            TerminalLine("[36mClaude Terminal — Built-in commands:[0m"),
            TerminalLine("  ls [-l] [-a]  — list directory contents"),
            TerminalLine("  cd <dir>       — change directory"),
            TerminalLine("  pwd            — print working directory"),
            TerminalLine("  cat <file>     — display file contents"),
            TerminalLine("  mkdir <dir>    — create directory"),
            TerminalLine("  rm [-r] <path> — remove file or directory"),
            TerminalLine("  touch <file>   — create empty file"),
            TerminalLine("  cp <src> <dst> — copy file"),
            TerminalLine("  mv <src> <dst> — move/rename file"),
            TerminalLine("  head/tail <f>  — show first/last lines"),
            TerminalLine("  grep <pat> <f> — search in file"),
            TerminalLine("  find -name <p> — find files"),
            TerminalLine("  echo <text>    — print text"),
            TerminalLine("  wc <file>      — word/line count"),
            TerminalLine("  clear          — clear screen"),
            TerminalLine("  help           — show this help"),
            TerminalLine(""),
            TerminalLine("[33mTap the ✦ button to ask Claude for help![0m")
        )
    }

    fun historyUp(): String? {
        if (commandHistory.isEmpty()) return null
        historyIndex = (historyIndex - 1).coerceAtLeast(0)
        return commandHistory.getOrNull(historyIndex)
    }

    fun historyDown(): String? {
        historyIndex = (historyIndex + 1).coerceAtMost(commandHistory.size)
        return commandHistory.getOrNull(historyIndex)
    }

    fun getPrompt(): String {
        val rel = currentDir.relativeTo(filesDir).path
        val displayPath = if (rel.isEmpty()) "~" else "~/$rel"
        return "[32muser[0m:[34m$displayPath[0m$ "
    }
}
