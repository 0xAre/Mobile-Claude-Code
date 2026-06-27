package com.zeroxare.claudemobile.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroxare.claudemobile.data.api.ClaudeApiService
import com.zeroxare.claudemobile.data.api.models.ClaudeMessage
import com.zeroxare.claudemobile.data.prefs.AppPreferences
import com.zeroxare.claudemobile.terminal.TerminalLine
import com.zeroxare.claudemobile.terminal.TerminalManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatMessage(
    val role: String,
    val content: String,
    val isLoading: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)
    private val claudeApi = ClaudeApiService()
    val terminalManager = TerminalManager(application.filesDir)

    val terminalLines = mutableStateListOf<TerminalLine>()
    val chatMessages = mutableStateListOf<ChatMessage>()
    val inputText = mutableStateOf("")
    val chatInput = mutableStateOf("")
    val isClaudePanelOpen = mutableStateOf(false)
    val isClauldLoading = mutableStateOf(false)
    val currentPrompt = mutableStateOf(terminalManager.getPrompt())
    val errorMessage = mutableStateOf<String?>(null)

    val apiKey = prefs.apiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val fontSize = prefs.fontSize.stateIn(viewModelScope, SharingStarted.Eagerly, "14")
    val selectedModel = prefs.model.stateIn(viewModelScope, SharingStarted.Eagerly, "claude-sonnet-4-6")

    init {
        terminalLines.add(TerminalLine("[36m╔══════════════════════════════════╗[0m"))
        terminalLines.add(TerminalLine("[36m║    Claude Terminal  v1.0.0       ║[0m"))
        terminalLines.add(TerminalLine("[36m║    Built by 0xAre                ║[0m"))
        terminalLines.add(TerminalLine("[36m╚══════════════════════════════════╝[0m"))
        terminalLines.add(TerminalLine("Type [33mhelp[0m for available commands."))
        terminalLines.add(TerminalLine("Tap [35m✦[0m to chat with Claude."))
        terminalLines.add(TerminalLine(""))
    }

    fun executeCommand(command: String) {
        terminalLines.add(TerminalLine("${terminalManager.getPrompt()}$command", isCommand = true))
        inputText.value = ""

        if (command.trim() == "clear") {
            terminalLines.clear()
            currentPrompt.value = terminalManager.getPrompt()
            return
        }

        viewModelScope.launch {
            val output = terminalManager.execute(command)
            terminalLines.addAll(output)
            currentPrompt.value = terminalManager.getPrompt()
        }
    }

    fun sendToClaudeWithTerminalContext(question: String) {
        val key = apiKey.value
        if (key.isBlank()) {
            errorMessage.value = "API key not set. Go to Settings to add your Anthropic API key."
            return
        }

        val recentTerminalOutput = terminalLines.takeLast(30)
            .joinToString("\n") { it.text }
            .let { com.zeroxare.claudemobile.terminal.AnsiParser.stripAnsi(it) }

        val userMessage = buildString {
            if (recentTerminalOutput.isNotBlank()) {
                append("[Recent terminal output]\n```\n")
                append(recentTerminalOutput.take(2000))
                append("\n```\n\n")
            }
            append(question)
        }

        chatMessages.add(ChatMessage("user", question))
        isClauldLoading.value = true
        chatInput.value = ""

        viewModelScope.launch {
            val history = chatMessages.filter { !it.isLoading }
                .dropLast(1)
                .takeLast(10)
                .map { ClaudeMessage(it.role, it.content) }

            val messages = history + listOf(ClaudeMessage("user", userMessage))

            claudeApi.sendMessage(apiKey.first(), messages).fold(
                onSuccess = { response ->
                    chatMessages.add(ChatMessage("assistant", response))
                },
                onFailure = { error ->
                    chatMessages.add(ChatMessage("assistant", "Error: ${error.message}"))
                }
            )
            isClauldLoading.value = false
        }
    }

    fun clearChat() {
        chatMessages.clear()
    }

    fun saveApiKey(key: String) = viewModelScope.launch {
        prefs.setApiKey(key.trim())
    }

    fun saveFontSize(size: String) = viewModelScope.launch {
        prefs.setFontSize(size)
    }

    fun saveModel(model: String) = viewModelScope.launch {
        prefs.setModel(model)
    }

    fun historyUp() {
        val cmd = terminalManager.historyUp()
        if (cmd != null) inputText.value = cmd
    }

    fun historyDown() {
        val cmd = terminalManager.historyDown() ?: ""
        inputText.value = cmd
    }
}
