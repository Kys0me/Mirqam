package rtlide.terminal.ansi

import androidx.compose.ui.graphics.Color

data class TermSpan(val text: String, val fg: Color, val bg: Color, val bold: Boolean)

data class StyledChar(val char: Char, val fg: Color, val bg: Color, val bold: Boolean)

class AnsiParser {

    private val defFg = Color(0xFFCCCCCC)
    private val defBg = Color(0xFF1E1E1E)

    private var fg = defFg
    private var bg = defBg
    private var bold = false

    private val currentLine = mutableListOf<StyledChar>()
    private var cursorX = 0

    data class ProcessResult(val completedLines: List<List<TermSpan>>, val cleared: Boolean = false)

    fun process(chunk: String): ProcessResult {
        val completedLines = mutableListOf<List<TermSpan>>()
        var cleared = false

        var i = 0
        while (i < chunk.length) {
            when (val c = chunk[i]) {
                '\u001B' -> {
                    if (i + 1 < chunk.length && chunk[i + 1] == '[') {
                        val end = indexOfCsiFinal(chunk, i + 2)
                        if (end != -1) {
                            val sequence = chunk.substring(i + 2, end)
                            when (chunk[end]) {
                                'm' -> applySgr(sequence.split(';'))
                                'J' -> if (sequence == "2") {
                                    completedLines.clear()
                                    currentLine.clear()
                                    cursorX = 0
                                    cleared = true
                                }
                                'K' -> {
                                    // EL - Erase in Line
                                    if (sequence == "" || sequence == "0") {
                                        // Erase from cursor to end of line
                                        if (cursorX < currentLine.size) {
                                            val toRemove = currentLine.size - cursorX
                                            repeat(toRemove) { currentLine.removeAt(cursorX) }
                                        }
                                    } else if (sequence == "2") {
                                        // Erase entire line
                                        currentLine.clear()
                                        cursorX = 0
                                    }
                                }
                            }
                            i = end + 1
                            continue
                        }
                    }
                    i++
                }
                '\n' -> {
                    completedLines.add(getLineSpans())
                    currentLine.clear()
                    cursorX = 0
                    i++
                }
                '\r' -> {
                    cursorX = 0
                    i++
                }
                '\b' -> {
                    cursorX = (cursorX - 1).coerceAtLeast(0)
                    i++
                }
                '\u000C' -> {
                    completedLines.clear()
                    currentLine.clear()
                    cursorX = 0
                    fg = defFg
                    bg = defBg
                    bold = false
                    cleared = true
                    i++
                }
                '\t' -> {
                    val spaces = 8 - (cursorX % 8)
                    repeat(spaces) {
                        writeChar(' ')
                    }
                    i++
                }
                else -> {
                    if (c.code >= 32 || c == '\u0000') {
                        writeChar(c)
                    }
                    i++
                }
            }
        }
        return ProcessResult(completedLines, cleared)
    }

    private fun writeChar(c: Char) {
        val styled = StyledChar(c, fg, bg, bold)
        if (cursorX < currentLine.size) {
            currentLine[cursorX] = styled
        } else {
            currentLine.add(styled)
        }
        cursorX++
    }

    fun getLineSpans(): List<TermSpan> {
        if (currentLine.isEmpty()) return emptyList()
        val spans = mutableListOf<TermSpan>()
        var lastStyled = currentLine[0]
        val sb = StringBuilder()

        for (sc in currentLine) {
            if (sc.fg != lastStyled.fg || sc.bg != lastStyled.bg || sc.bold != lastStyled.bold) {
                if (sb.isNotEmpty()) {
                    spans.add(TermSpan(sb.toString(), lastStyled.fg, lastStyled.bg, lastStyled.bold))
                }
                sb.setLength(0)
                lastStyled = sc
            }
            sb.append(sc.char)
        }
        if (sb.isNotEmpty()) {
            spans.add(TermSpan(sb.toString(), lastStyled.fg, lastStyled.bg, lastStyled.bold))
        }
        return spans
    }

    private fun indexOfCsiFinal(text: String, from: Int): Int {
        var j = from
        while (j < text.length) {
            if (text[j].code in 0x40..0x7E) return j
            j++
        }
        return -1
    }

    private fun applySgr(codes: List<String>) {
        for (code in codes) {
            val n = code.toIntOrNull() ?: 0
            when (n) {
                0 -> { fg = defFg; bg = defBg; bold = false }
                1 -> bold = true
                22 -> bold = false
                in 30..37 -> fg = PALETTE[n - 30]
                in 90..97 -> fg = PALETTE_BRIGHT[n - 90]
                in 40..47 -> bg = PALETTE[n - 40]
                in 100..107 -> bg = PALETTE_BRIGHT[n - 100]
                39 -> fg = defFg
                49 -> bg = defBg
            }
        }
    }

    companion object {
        val PALETTE: List<Color> = listOf(
            0xFF000000, 0xFFCD3131, 0xFF0DBC79, 0xFFE5E510,
            0xFF2472C8, 0xFFBC3FBC, 0xFF11A8CD, 0xFFE5E5E5,
        ).map { Color(it) }

        val PALETTE_BRIGHT: List<Color> = listOf(
            0xFF666666, 0xFFF14C4C, 0xFF23D18B, 0xFFF5F543,
            0xFF3B8EEA, 0xFFD670D6, 0xFF29B8DB, 0xFFFFFFFF,
        ).map { Color(it) }
    }
}
