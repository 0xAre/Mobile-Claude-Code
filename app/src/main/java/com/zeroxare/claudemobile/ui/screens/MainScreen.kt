package com.zeroxare.claudemobile.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroxare.claudemobile.ui.components.ClaudePanel
import com.zeroxare.claudemobile.ui.components.TerminalOutput
import com.zeroxare.claudemobile.ui.components.TerminalPromptLine
import com.zeroxare.claudemobile.ui.theme.*
import com.zeroxare.claudemobile.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit
) {
    val terminalListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val fontSize = viewModel.fontSize.collectAsState()
    val fontSizeSp = (fontSize.value.toIntOrNull() ?: 13).sp

    LaunchedEffect(viewModel.terminalLines.size) {
        if (viewModel.terminalLines.isNotEmpty()) {
            terminalListState.animateScrollToItem(viewModel.terminalLines.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize().background(TerminalBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Traffic-light dots
                    Box(Modifier.size(10.dp).clip(CircleShape).background(TerminalRed))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(TerminalYellow))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(TerminalGreen))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "claude-terminal",
                        style = TextStyle(color = TerminalDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TerminalDim, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Terminal output + Claude panel side-by-side
            Row(modifier = Modifier.weight(1f)) {
                // Terminal output
                Box(
                    modifier = Modifier
                        .weight(if (viewModel.isClaudePanelOpen.value) 0.4f else 1f)
                        .fillMaxHeight()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TerminalOutput(
                            lines = viewModel.terminalLines,
                            listState = terminalListState,
                            fontSize = fontSizeSp,
                            modifier = Modifier.weight(1f)
                        )
                        TerminalPromptLine(
                            prompt = viewModel.currentPrompt.value,
                            input = viewModel.inputText.value,
                            fontSize = fontSizeSp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                // Claude panel
                AnimatedVisibility(
                    visible = viewModel.isClaudePanelOpen.value,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxHeight().width(1.dp).background(TerminalBorder)
                        )
                        ClaudePanel(
                            messages = viewModel.chatMessages,
                            input = viewModel.chatInput.value,
                            isLoading = viewModel.isClauldLoading.value,
                            onInputChange = { viewModel.chatInput.value = it },
                            onSend = { viewModel.sendToClaudeWithTerminalContext(it) },
                            onClear = { viewModel.clearChat() },
                            onClose = { viewModel.isClaudePanelOpen.value = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalSurface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // History navigation
                IconButton(onClick = { viewModel.historyUp() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "History up", tint = TerminalDim, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { viewModel.historyDown() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "History down", tint = TerminalDim, modifier = Modifier.size(16.dp))
                }

                // Command input
                BasicTextField(
                    value = viewModel.inputText.value,
                    onValueChange = { viewModel.inputText.value = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        color = TerminalText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val cmd = viewModel.inputText.value
                            if (cmd.isNotBlank()) {
                                viewModel.executeCommand(cmd)
                                scope.launch {
                                    terminalListState.animateScrollToItem(
                                        viewModel.terminalLines.size.coerceAtLeast(0)
                                    )
                                }
                            }
                        }
                    ),
                    decorationBox = { inner ->
                        if (viewModel.inputText.value.isEmpty()) {
                            Text("Enter command...", style = TextStyle(color = TerminalDim.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace, fontSize = fontSizeSp))
                        }
                        inner()
                    }
                )

                // Claude toggle button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (viewModel.isClaudePanelOpen.value) ClaudeOrange else TerminalBorder)
                        .clickable { viewModel.isClaudePanelOpen.value = !viewModel.isClaudePanelOpen.value },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✦", fontSize = 16.sp, color = Color.White)
                }
            }
        }

        // Error snackbar
        viewModel.errorMessage.value?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.errorMessage.value = null }) {
                        Text("OK", color = ClaudeOrange)
                    }
                },
                containerColor = Color(0xFF2D1117)
            ) {
                Text(error, style = TextStyle(color = TerminalText, fontSize = 12.sp))
            }
        }
    }
}
