package com.zeroxare.claudemobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TerminalBg = Color(0xFF0D1117)
val TerminalSurface = Color(0xFF161B22)
val TerminalBorder = Color(0xFF30363D)
val TerminalGreen = Color(0xFF39D353)
val TerminalCyan = Color(0xFF58A6FF)
val TerminalPurple = Color(0xFFBC8CFF)
val TerminalYellow = Color(0xFFE3B341)
val TerminalRed = Color(0xFFF85149)
val TerminalText = Color(0xFFE6EDF3)
val TerminalDim = Color(0xFF8B949E)
val ClaudeOrange = Color(0xFFD97757)
val ClaudeAccent = Color(0xFFCC785C)

private val DarkColorScheme = darkColorScheme(
    primary = ClaudeOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D1F14),
    secondary = TerminalCyan,
    onSecondary = Color.White,
    background = TerminalBg,
    onBackground = TerminalText,
    surface = TerminalSurface,
    onSurface = TerminalText,
    surfaceVariant = Color(0xFF1C2128),
    onSurfaceVariant = TerminalDim,
    outline = TerminalBorder,
    error = TerminalRed,
    onError = Color.White
)

@Composable
fun ClaudeMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
