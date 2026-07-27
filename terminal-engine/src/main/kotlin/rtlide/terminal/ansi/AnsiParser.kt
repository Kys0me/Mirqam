package rtlide.terminal.ansi

import androidx.compose.ui.graphics.Color

/** A run of terminal text sharing one visual style. */
data class TermSpan(val text: String, val fg: Color, val bg: Color, val bold: Boolean)

/**
 * Minimal VT/ANSI SGR parser (the ESC[…m color/style sequences). Stateful, so
 * colors set on one feed() carry to the next. A full terminal also interprets
 * cursor-movement CSI (CUP/ED/EL) to drive a screen buffer — out of scope here.
 */
class AnsiParser {

    private val defFg = Color(0xFFCCCCCC)
    private val defBg = Color(0xFF1E1E1E)

    private var fg = defFg
    private var bg = defBg
    private var bold = false

    fun feed(text: String): List<TermSpan> {
        val spans = ArrayList<TermSpan>()
        val sb = StringBuilder()
        var i = 0

        fun flush() {
            if (sb.isNotEmpty()) {
                spans += TermSpan(sb.toString(), fg, bg, bold)
                sb.clear()
            }
        }

        while (i < text.length) {
            val c = text[i]
            if (c == '\u001B' && i + 1 < text.length && text[i + 1] == '[') {
                flush()
                val end = indexOfCsiFinal(text, i + 2)
                if (end == -1) break
                if (text[end] == 'm') applySgr(text.substring(i + 2, end).split(';'))
                // (non-SGR CSI sequences are consumed and ignored)
                i = end + 1
            } else {
                sb.append(c)
                i++
            }
        }
        flush()
        return spans
    }

    private fun indexOfCsiFinal(text: String, from: Int): Int {
        var j = from
        while (j < text.length) {
            // CSI final byte is in the range 0x40..0x7E.
            if (text[j].code in 0x40..0x7E) return j
            j++
        }
        return -1
    }

    private fun applySgr(codes: List<String>) {
        for (code in codes) {
            when (val n = code.toIntOrNull() ?: 0) {
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
        // Standard xterm 16-color palette.
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
