package com.zeroxare.claudemobile.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

private const val ESC = ''

object AnsiParser {

    private val ansi16Colors = mapOf(
        0 to Color(0xFF1E1E1E),
        1 to Color(0xFFCC0000),
        2 to Color(0xFF39D353),
        3 to Color(0xFFE3B341),
        4 to Color(0xFF58A6FF),
        5 to Color(0xFFBC8CFF),
        6 to Color(0xFF56D364),
        7 to Color(0xFFD4D4D4),
        8 to Color(0xFF8B949E),
        9 to Color(0xFFF85149),
        10 to Color(0xFF56D364),
        11 to Color(0xFFFFFF55),
        12 to Color(0xFF79C0FF),
        13 to Color(0xFFD2A8FF),
        14 to Color(0xFF56D364),
        15 to Color(0xFFFFFFFF)
    )

    fun parse(input: String): AnnotatedString {
        return buildAnnotatedString {
            var currentColor = Color(0xFFD4D4D4)
            var bold = false
            var i = 0
            var styleOpen = false

            fun openStyle() {
                if (styleOpen) pop()
                pushStyle(SpanStyle(
                    color = currentColor,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
                ))
                styleOpen = true
            }

            while (i < input.length) {
                val ch = input[i]
                if (ch == ESC && i + 1 < input.length && input[i + 1] == '[') {
                    val end = input.indexOf('m', i + 2)
                    if (end == -1) { append(ch); i++; continue }

                    val codes = input.substring(i + 2, end).split(";")
                    i = end + 1

                    var j = 0
                    while (j < codes.size) {
                        when (val code = codes[j].toIntOrNull() ?: 0) {
                            0 -> { currentColor = Color(0xFFD4D4D4); bold = false }
                            1 -> bold = true
                            22 -> bold = false
                            in 30..37 -> currentColor = ansi16Colors[code - 30] ?: currentColor
                            38 -> {
                                if (j + 2 < codes.size && codes[j + 1] == "5") {
                                    currentColor = ansi256Color(codes[j + 2].toIntOrNull() ?: 7)
                                    j += 2
                                }
                            }
                            39 -> currentColor = Color(0xFFD4D4D4)
                            in 90..97 -> currentColor = ansi16Colors[code - 90 + 8] ?: currentColor
                        }
                        j++
                    }
                    openStyle()
                } else if (ch == ESC) {
                    i += 2
                } else {
                    append(ch)
                    i++
                }
            }
        }
    }

    private fun ansi256Color(index: Int): Color {
        if (index < 16) return ansi16Colors[index] ?: Color.White
        if (index >= 232) {
            val v = (index - 232) * 10 + 8
            return Color(0xFF000000L or (v.toLong() shl 16) or (v.toLong() shl 8) or v.toLong())
        }
        val idx = index - 16
        val r = (idx / 36) * 51
        val g = ((idx % 36) / 6) * 51
        val b = (idx % 6) * 51
        return Color(0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
    }

    fun stripAnsi(input: String): String {
        return input.replace(Regex("\[[0-9;]*[A-Za-z]"), "")
    }
}
