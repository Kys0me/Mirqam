package rtlide.lang.highlight

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
class VisualLine(
    val annotatedString: AnnotatedString,
    val numberRanges: List<IntRange> = emptyList(),
    val isRtl: Boolean = false
) {
    fun docToLayout(docOffset: Int): Int {
        if (!isRtl) return docOffset
        var shift = 0
        for (range in numberRanges) {
            if (range.first <= docOffset) shift++
            if (range.last <= docOffset) shift++
        }
        return docOffset + shift
    }

    fun layoutToDoc(layoutOffset: Int): Int {
        if (!isRtl) return layoutOffset
        var shift = 0
        for (range in numberRanges) {
            val rloPos = range.first + shift
            if (layoutOffset > rloPos) shift++
            val pdfPos = range.last + shift
            if (layoutOffset > pdfPos) shift++
        }
        return (layoutOffset - shift).coerceAtLeast(0)
    }
}

class Highlighter(
    private val tokenizer: Tokenizer,
    private val theme: Theme,
) {
    fun highlight(line: String, isRtl: Boolean = false): VisualLine {
        val tokens = tokenizer.tokenize(line)
        val numberRanges = if (isRtl) {
            tokens.filter { it.scope == "constant.numeric" }.map { it.start..it.end }
        } else emptyList()

        val annotatedString = buildAnnotatedString {
            if (!isRtl || numberRanges.isEmpty()) {
                append(line)
            } else {
                var last = 0
                for (range in numberRanges) {
                    append(line.substring(last, range.first))
                    append("\u202E") // RLO: Right-to-Left Override
                    append(line.substring(range.first, range.last))
                    append("\u202C") // PDF: Pop Directional Format
                    last = range.last
                }
                append(line.substring(last))
            }

            for (t in tokens) {
                val style = theme.tokenColors[t.scope] ?: continue

                val start = if (isRtl) {
                    var shift = 0
                    for (r in numberRanges) {
                        if (r.first <= t.start) shift++
                        if (r.last <= t.start) shift++
                    }
                    t.start + shift
                } else t.start

                val end = if (isRtl) {
                    var shift = 0
                    for (r in numberRanges) {
                        if (r.first <= t.end) shift++
                        if (r.last <= t.end) shift++
                    }
                    t.end + shift
                } else t.end

                addStyle(
                    SpanStyle(
                        color = hexToColor(style.color),
                        fontWeight = if (style.bold) FontWeight.Bold else null,
                        fontStyle = if (style.italic) FontStyle.Italic else null,
                    ),
                    start.coerceIn(0, length),
                    end.coerceIn(0, length),
                )
            }
        }
        return VisualLine(annotatedString, numberRanges, isRtl)
    }
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
