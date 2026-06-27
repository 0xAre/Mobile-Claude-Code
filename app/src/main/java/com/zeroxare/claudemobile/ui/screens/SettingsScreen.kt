package com.zeroxare.claudemobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroxare.claudemobile.ui.theme.*
import com.zeroxare.claudemobile.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val apiKey by viewModel.apiKey.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val model by viewModel.selectedModel.collectAsState()

    var apiKeyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var fontSizeInput by remember(fontSize) { mutableStateOf(fontSize) }
    var showApiKey by remember { mutableStateOf(false) }
    var selectedModel by remember(model) { mutableStateOf(model) }
    var saved by remember { mutableStateOf(false) }

    val availableModels = listOf(
        "claude-sonnet-4-6" to "Claude Sonnet 4.6 (Recommended)",
        "claude-haiku-4-5-20251001" to "Claude Haiku 4.5 (Faster)",
        "claude-opus-4-8" to "Claude Opus 4.8 (Most capable)"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBg)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalSurface)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TerminalText)
            }
            Text(
                "Settings",
                style = TextStyle(color = TerminalText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Key section
            SettingsSection(title = "Anthropic API Key") {
                Text(
                    "Get your key at console.anthropic.com",
                    style = TextStyle(color = TerminalDim, fontSize = 12.sp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key", style = TextStyle(color = TerminalDim, fontSize = 12.sp)) },
                    textStyle = TextStyle(color = TerminalText, fontSize = 13.sp),
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TerminalDim
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClaudeOrange,
                        unfocusedBorderColor = TerminalBorder,
                        cursorColor = ClaudeOrange
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    placeholder = {
                        Text("sk-ant-...", style = TextStyle(color = TerminalDim.copy(alpha = 0.5f), fontSize = 13.sp))
                    }
                )
            }

            // Model selection
            SettingsSection(title = "Claude Model") {
                availableModels.forEach { (id, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedModel == id,
                            onClick = { selectedModel = id },
                            colors = RadioButtonDefaults.colors(selectedColor = ClaudeOrange)
                        )
                        Column {
                            Text(label, style = TextStyle(color = TerminalText, fontSize = 13.sp))
                            Text(id, style = TextStyle(color = TerminalDim, fontSize = 11.sp))
                        }
                    }
                }
            }

            // Font size
            SettingsSection(title = "Terminal Font Size") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("11", "13", "14", "16", "18").forEach { size ->
                        FilterChip(
                            selected = fontSizeInput == size,
                            onClick = { fontSizeInput = size },
                            label = { Text("${size}sp", style = TextStyle(fontSize = 12.sp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ClaudeOrange,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Save button
            Button(
                onClick = {
                    viewModel.saveApiKey(apiKeyInput)
                    viewModel.saveFontSize(fontSizeInput)
                    viewModel.saveModel(selectedModel)
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ClaudeOrange),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (saved) "Saved ✓" else "Save Settings", style = TextStyle(fontWeight = FontWeight.SemiBold))
            }

            // Info box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C2128), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ℹ️  About Claude Terminal", style = TextStyle(color = TerminalText, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    Text(
                        "A mobile terminal emulator with Claude AI integration.\n" +
                        "Commands run in Android's sandboxed shell.\n" +
                        "Claude can see your terminal output to give contextual help.\n\n" +
                        "Built by 0xAre · github.com/0xAre",
                        style = TextStyle(color = TerminalDim, fontSize = 12.sp, lineHeight = 18.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalSurface, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title.uppercase(),
            style = TextStyle(
                color = ClaudeOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        )
        Divider(color = TerminalBorder, modifier = Modifier.padding(vertical = 4.dp))
        content()
    }
}
