package com.zeroxare.claudemobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroxare.claudemobile.ui.theme.*
import com.zeroxare.claudemobile.viewmodel.TerminalViewModel

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onOpenSettings: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.startSession() }
    LaunchedEffect(viewModel.lines.size) {
        if (viewModel.lines.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.lines.size - 1)
        }
    }

    val mono = remember {
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = TerminalText)
    }
    val density = LocalDensity.current
    // Monospace cell size (approx): advance ~0.6em, line height ~1.4em.
    val cellW = with(density) { 13.sp.toPx() } * 0.6f
    val cellH = with(density) { 13.sp.toPx() } * 1.4f

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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Claude Terminal",
                    style = TextStyle(color = TerminalText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                )
                Text(viewModel.status.value, style = TextStyle(color = TerminalDim, fontSize = 10.sp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip("Setup Claude") { viewModel.setupClaude() }
                ActionChip("Login") { viewModel.login() }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TerminalDim, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Output
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .onSizeChanged { size ->
                    if (cellW > 0f && cellH > 0f) {
                        val cols = (size.width / cellW).toInt().coerceAtLeast(10)
                        val rows = (size.height / cellH).toInt().coerceAtLeast(4)
                        viewModel.resize(rows, cols)
                    }
                }
        ) {
            items(viewModel.lines) { line ->
                BasicText(text = line, style = mono)
            }
            item {
                BasicText(text = viewModel.currentLine.value, style = mono)
            }
        }

        // Special keys
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(TerminalSurface)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyCap("ESC") { viewModel.sendRaw("") }
            KeyCap("TAB") { viewModel.sendRaw("\t") }
            KeyCap("^C") { viewModel.sendRaw("") }
            KeyCap("^D") { viewModel.sendRaw("") }
            KeyCap("^L") { viewModel.sendRaw("") }
            KeyCap("↑") { viewModel.sendRaw("[A") }
            KeyCap("↓") { viewModel.sendRaw("[B") }
            KeyCap("←") { viewModel.sendRaw("[D") }
            KeyCap("→") { viewModel.sendRaw("[C") }
            KeyCap("|") { viewModel.sendRaw("|") }
            KeyCap("/") { viewModel.sendRaw("/") }
            KeyCap("-") { viewModel.sendRaw("-") }
            KeyCap("~") { viewModel.sendRaw("~") }
        }

        // Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("$", style = TextStyle(color = TerminalGreen, fontFamily = FontFamily.Monospace, fontSize = 14.sp))
            BasicTextField(
                value = viewModel.input.value,
                onValueChange = { viewModel.input.value = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TerminalText, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { viewModel.submitInput() }),
                decorationBox = { inner ->
                    if (viewModel.input.value.isEmpty()) {
                        Text("type a command…", style = TextStyle(color = TerminalDim.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace, fontSize = 13.sp))
                    }
                    inner()
                }
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ClaudeOrange)
                    .clickable { viewModel.submitInput() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Run", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalBorder)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = TerminalText, fontSize = 12.sp)
    }
}

@Composable
private fun KeyCap(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalBorder)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = TerminalText, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}
