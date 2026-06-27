package com.zeroxare.claudemobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroxare.claudemobile.ui.theme.*
import com.zeroxare.claudemobile.viewmodel.ChatMessage
@Composable
fun ClaudePanel(
    messages: List<ChatMessage>,
    input: String,
    isLoading: Boolean,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalSurface)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C2128))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ClaudeOrange)
                )
                Text(
                    "Claude Terminal",
                    style = TextStyle(
                        color = TerminalText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                )
            }
            Row {
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = TerminalDim, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TerminalDim, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("✦", fontSize = 36.sp, color = ClaudeOrange)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ask Claude about your code,\ncommands, or terminal output",
                            style = TextStyle(color = TerminalDim, fontSize = 13.sp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            items(messages) { msg ->
                ChatBubble(message = msg)
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = ClaudeOrange,
                            strokeWidth = 1.5.dp
                        )
                        Text("Claude is thinking...", style = TextStyle(color = TerminalDim, fontSize = 12.sp))
                    }
                }
            }
        }

        // Input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C2128))
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Claude...", style = TextStyle(color = TerminalDim, fontSize = 13.sp)) },
                textStyle = TextStyle(color = TerminalText, fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClaudeOrange,
                    unfocusedBorderColor = TerminalBorder,
                    cursorColor = ClaudeOrange
                ),
                shape = RoundedCornerShape(8.dp),
                maxLines = 4,
                singleLine = false
            )
            IconButton(
                onClick = { if (input.isNotBlank()) onSend(input) },
                enabled = input.isNotBlank() && !isLoading,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (input.isNotBlank() && !isLoading) ClaudeOrange else TerminalBorder)
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(ClaudeOrange),
                contentAlignment = Alignment.Center
            ) {
                Text("✦", fontSize = 12.sp, color = Color.White)
            }
            Spacer(Modifier.width(6.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (isUser) 12.dp else 2.dp,
                        topEnd = if (isUser) 2.dp else 12.dp,
                        bottomStart = 12.dp,
                        bottomEnd = 12.dp
                    )
                )
                .background(if (isUser) Color(0xFF3D2314) else Color(0xFF1C2128))
                .padding(10.dp)
        ) {
            SelectionContainer {
                if (isUser) {
                    Text(
                        text = message.content,
                        style = TextStyle(
                            color = TerminalText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    )
                } else {
                    // Render markdown for Claude responses
                    Text(
                        text = message.content,
                        style = TextStyle(
                            color = TerminalText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = FontFamily.Default
                        )
                    )
                }
            }
        }
    }
}
