package com.zeroxare.claudemobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroxare.claudemobile.terminal.AnsiParser
import com.zeroxare.claudemobile.terminal.TerminalLine
import com.zeroxare.claudemobile.ui.theme.TerminalRed
import com.zeroxare.claudemobile.ui.theme.TerminalText

@Composable
fun TerminalOutput(
    lines: List<TerminalLine>,
    listState: LazyListState,
    fontSize: TextUnit = 13.sp,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(lines) { line ->
            TerminalLineRow(line = line, fontSize = fontSize)
        }
    }
}

@Composable
fun TerminalLineRow(
    line: TerminalLine,
    fontSize: TextUnit = 13.sp
) {
    val scrollState = rememberScrollState()
    val annotated = AnsiParser.parse(line.text)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        BasicText(
            text = annotated,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize,
                color = if (line.isError) TerminalRed else TerminalText,
                lineHeight = (fontSize.value * 1.4).sp
            ),
            modifier = Modifier.padding(vertical = 0.5.dp)
        )
    }
}

@Composable
fun TerminalPromptLine(
    prompt: String,
    input: String,
    fontSize: TextUnit = 13.sp,
    modifier: Modifier = Modifier
) {
    val promptAnnotated = AnsiParser.parse("$prompt$input█")
    Row(modifier = modifier.padding(horizontal = 8.dp)) {
        BasicText(
            text = promptAnnotated,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize,
                color = TerminalText,
                lineHeight = (fontSize.value * 1.4).sp
            )
        )
    }
}
