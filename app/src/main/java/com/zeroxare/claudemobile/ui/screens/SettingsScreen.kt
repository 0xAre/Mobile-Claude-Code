package com.zeroxare.claudemobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroxare.claudemobile.ui.theme.*

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBg)
    ) {
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
                "Settings & Help",
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
            Section("Cara pakai") {
                Step("1", "Terminal sudah interaktif — ketik perintah (mis. ls, pwd) lalu tap ➜.")
                Step("2", "Tap “Setup Claude” untuk memasang Node + Claude Code (butuh arsip bootstrap).")
                Step("3", "Tap “Login” untuk masuk dengan akun Claude (OAuth) — tanpa API key.")
                Step("4", "Atur ukuran teks dengan A- / A+ di bar tombol.")
            }

            Section("Tombol khusus") {
                Body("Bar di atas keyboard mengirim tombol yang tak ada di keyboard layar: " +
                        "ESC, TAB, Ctrl (^C/^D/^L), panah, | / - ~, dan A-/A+ untuk ukuran font.")
            }

            Section("Status login akun") {
                Body("Login akun Claude memerlukan arsip bootstrap (Node + Claude Code) yang " +
                        "dipublikasikan ke Release ‘bootstrap-v1’. Selama belum ada, tombol Setup/Login " +
                        "akan memberi tahu bootstrap belum tersedia. Lihat docs/FASE2.md.")
            }

            Section("About") {
                Body("Claude Terminal — terminal Android (PTY) untuk menjalankan Claude Code CLI " +
                        "asli dengan login akun Claude-mu. Lisensi GPLv3.")
                Body("Dibuat oleh 0xAre · github.com/0xAre")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalSurface, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            title.uppercase(),
            style = TextStyle(color = ClaudeOrange, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        )
        Divider(color = TerminalBorder, modifier = Modifier.padding(vertical = 2.dp))
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = TextStyle(color = TerminalDim, fontSize = 13.sp, lineHeight = 19.sp))
}

@Composable
private fun Step(num: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(num, style = TextStyle(color = ClaudeOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Text(text, style = TextStyle(color = TerminalText, fontSize = 13.sp, lineHeight = 19.sp))
    }
}
