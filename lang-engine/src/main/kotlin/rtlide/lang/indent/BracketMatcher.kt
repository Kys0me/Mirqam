package rtlide.lang.indent

import rtlide.lang.schema.BracketPair
import rtlide.lang.schema.IndentRules

/**
 * Bracket matching operates on the LOGICAL character stream, so the exact same
 * code is correct for LTR and RTL. The only RTL concern is VISUAL: to highlight
 * the matched bracket, feed the logical offset returned here into
 * TextLayoutResult.getBoundingBox(offset) — that rectangle is already
 * Bidi-reordered and glyph-mirrored. Never swap the characters yourself.
 */
object Brackets {

    /** Given a caret column, look at the char under and just before the caret;
     *  if it is a bracket, return the logical column of its match on this line
     *  (or null). Same-line only in this scaffold. */
    fun matchOnLine(line: String, caret: Int, pairs: List<BracketPair>): Int? {
        for (pos in listOf(caret, caret - 1)) {
            if (pos !in line.indices) continue
            val r = match(line, pos, pairs)
            if (r != null) return r
        }
        return null
    }

    private fun match(text: String, index: Int, pairs: List<BracketPair>): Int? {
        val opens = pairs.associate { it.open.first() to it.close.first() }
        val closes = pairs.associate { it.close.first() to it.open.first() }
        val ch = text[index]
        when {
            ch in opens -> {
                val close = opens.getValue(ch)
                var depth = 0
                for (j in index until text.length) {
                    when (text[j]) {
                        ch -> depth++
                        close -> { depth--; if (depth == 0) return j }
                    }
                }
            }
            ch in closes -> {
                val open = closes.getValue(ch)
                var depth = 0
                for (j in index downTo 0) {
                    when (text[j]) {
                        ch -> depth++
                        open -> { depth--; if (depth == 0) return j }
                    }
                }
            }
        }
        return null
    }
}

/** RTL-agnostic auto-indent: preserve the current line's leading whitespace and
 *  add one level after an indent trigger (e.g. "{"). Returns the string to
 *  insert in place of a bare newline. */
fun newlineIndent(currentLine: String, rules: IndentRules): String {
    val leading = currentLine.takeWhile { it == ' ' || it == '\t' }
    val trimmed = currentLine.trimEnd()
    val addLevel = rules.indentTriggers.any { trimmed.endsWith(it) }
    val unit = if (rules.useSpaces) " ".repeat(rules.indentSize) else "\t"
    return "\n" + leading + (if (addLevel) unit else "")
}

fun getAutoCloseTrigger(currentLine: String, rules: IndentRules): String? {
    val trimmed = currentLine.trim()
    if (rules.indentTriggers.any { trimmed.endsWith(it) }) {
        return rules.dedentTriggers.firstOrNull()
    }
    return null
}
