package rtlide.lang.highlight

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import rtlide.lang.schema.Theme
import rtlide.lang.tokenizer.Tokenizer

/**
 * Turns a raw source line into a Bidi-neutral AnnotatedString with color spans.
 * The editor measures this AnnotatedString directly, so highlighting and Bidi
 * shaping compose naturally.
 */
class Highlighter(
    private val tokenizer: Tokenizer,
    private val theme: Theme,
) {
    /** Bump to invalidate the editor's cached line layouts (e.g. theme change). */
    var version by mutableStateOf(0)
        private set

    fun highlight(line: String): AnnotatedString = buildAnnotatedString {
        append(line)
        for (t in tokenizer.tokenize(line)) {
            val style = theme.tokenColors[t.scope] ?: continue
            addStyle(
                SpanStyle(
                    color = hexToColor(style.color),
                    fontWeight = if (style.bold) FontWeight.Bold else null,
                    fontStyle = if (style.italic) FontStyle.Italic else null,
                ),
                t.start.coerceIn(0, line.length),
                t.end.coerceIn(0, line.length),
            )
        }
    }

    fun invalidate() { version++ }
}

/** Parse "#RRGGBB" or "#AARRGGBB" into a Compose Color. */
fun hexToColor(hex: String): Color {
    val h = hex.removePrefix("#")
    if (h.length < 6) return Color(0xFFD4D4D4)
    val r = h.substring(0, 2).toInt(16)
    val g = h.substring(2, 4).toInt(16)
    val b = h.substring(4, 6).toInt(16)
    val a = if (h.length >= 8) h.substring(6, 8).toInt(16) else 255
    return Color(red = r, green = g, blue = b, alpha = a)
}
