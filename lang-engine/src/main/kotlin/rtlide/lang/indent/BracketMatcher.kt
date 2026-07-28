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

// ---------------------------------------------------------------------------
// Token-aware line scanning
// ---------------------------------------------------------------------------

/**
 * The result of masking a text: string-literal contents and line comments are
 * replaced by spaces so that trigger words inside them are invisible to the
 * indent logic, while every line keeps its original length and the string
 * delimiters themselves stay in place.
 */
internal class MaskedText(
    val lines: List<String>,
    /** inString[i] == true means line i STARTS inside a multi-line string.
     *  Size is lines.size + 1; the last entry is the state after the final line. */
    val inString: List<Boolean>,
)

/** Masks string contents and line comments with spaces, tracking multi-line
 *  strings across lines (Sakhr strings may span newlines and have no escapes). */
internal fun maskText(
    lines: List<String>,
    lineComment: String = "//",
    stringDelimiters: List<Char> = listOf('"'),
): MaskedText {
    val masked = ArrayList<String>(lines.size)
    val inString = ArrayList<Boolean>(lines.size + 1)
    var stringChar: Char? = null // non-null while inside a (possibly multi-line) string
    for (line in lines) {
        inString.add(stringChar != null)
        val sb = StringBuilder(line.length)
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (stringChar != null) {
                if (ch == stringChar) { stringChar = null; sb.append(ch) } else sb.append(' ')
                i++
                continue
            }
            if (lineComment.isNotEmpty() && line.startsWith(lineComment, i)) {
                repeat(line.length - i) { sb.append(' ') }
                break
            }
            if (ch in stringDelimiters) {
                stringChar = ch
                sb.append(ch)
                i++
                continue
            }
            sb.append(ch)
            i++
        }
        masked.add(sb.toString())
    }
    inString.add(stringChar != null)
    return MaskedText(masked, inString)
}

private fun Char.isWordChar(): Boolean = this == '_' || isLetterOrDigit()

/** Counts whole-word occurrences of [word] in [text]; identifiers that merely
 *  contain the word (e.g. "ابدأها" vs "ابدأ") do not match. */
internal fun countWordOccurrences(text: String, word: String): Int {
    if (word.isEmpty()) return 0
    var count = 0
    var idx = text.indexOf(word)
    while (idx >= 0) {
        val before = text.getOrNull(idx - 1)
        val after = text.getOrNull(idx + word.length)
        if ((before == null || !before.isWordChar()) && (after == null || !after.isWordChar())) count++
        idx = text.indexOf(word, idx + 1)
    }
    return count
}

internal fun startsWithWord(text: String, word: String): Boolean {
    if (!text.startsWith(word)) return false
    val after = text.getOrNull(word.length)
    return after == null || !after.isWordChar()
}

internal fun endsWithWord(text: String, word: String): Boolean {
    val t = text.trimEnd()
    if (!t.endsWith(word)) return false
    val before = t.getOrNull(t.length - word.length - 1)
    return before == null || !before.isWordChar()
}

// ---------------------------------------------------------------------------
// Smart Enter
// ---------------------------------------------------------------------------

/**
 * Data class representing the result of a "Smart Enter" operation.
 * [text] is the full string to insert at the current caret position.
 * [caretLineOffset] is how many lines to move the caret relative to its current line.
 * [caretColOffset] is the absolute column index for the caret in its new line.
 */
data class SmartEnterResult(
    val text: String,
    val caretLineOffset: Int,
    val caretColOffset: Int
)

/**
 * Combines indentation and auto-closing into a single atomic result to prevent
 * transient document states that could trigger false positive analysis errors.
 *
 * Token-aware: trigger words inside strings, comments, or larger identifiers
 * never fire. Auto-close is decided by the whole-document open/close balance,
 * so nested blocks whose closer belongs to an outer block still auto-close.
 */
fun calculateSmartEnter(
    currentLine: String,
    rules: IndentRules,
    fullText: String = "",
    lineIndex: Int = -1,
    lineComment: String = "//",
    stringDelimiters: List<Char> = listOf('"'),
): SmartEnterResult {
    val leading = currentLine.takeWhile { it == ' ' || it == '\t' }
    val unit = if (rules.useSpaces) " ".repeat(rules.indentSize) else "\t"

    val maskedCurrent = maskText(listOf(currentLine), lineComment, stringDelimiters).lines[0]
    val isTriggered = rules.indentTriggers.any { endsWithWord(maskedCurrent, it) }
    if (!isTriggered) {
        return SmartEnterResult("\n$leading", caretLineOffset = 1, caretColOffset = leading.length)
    }

    var autoClose: String? = rules.dedentTriggers.firstOrNull()
    if (autoClose != null && fullText.isNotEmpty() && lineIndex >= 0) {
        // Whole-document balance: the just-typed opener is already in fullText,
        // so a positive balance means one closer is genuinely missing.
        val masked = maskText(fullText.split('\n'), lineComment, stringDelimiters)
        var balance = 0
        for (l in masked.lines) {
            balance += rules.indentTriggers.sumOf { countWordOccurrences(l, it) }
            balance -= rules.dedentTriggers.sumOf { countWordOccurrences(l, it) }
        }
        if (balance <= 0) autoClose = null
    }

    return if (autoClose != null) {
        // Atomic block creation: indent + caret line + closing line
        val text = "\n$leading$unit\n$leading$autoClose"
        SmartEnterResult(text, caretLineOffset = 1, caretColOffset = (leading + unit).length)
    } else {
        val text = "\n$leading$unit"
        SmartEnterResult(text, caretLineOffset = 1, caretColOffset = (leading + unit).length)
    }
}

// ---------------------------------------------------------------------------
// Reformat
// ---------------------------------------------------------------------------

/**
 * Reformats the entire text based on IndentRules.
 *
 * Token-aware line re-indenter:
 *  - trigger words inside strings, line comments, or larger identifiers are ignored;
 *  - lines inside a multi-line string are emitted verbatim;
 *  - end/begin chains like "انتهى وإلا ابدأ" keep the parent indent;
 *  - lines inside an unclosed "(" or "[" get one continuation indent level.
 *
 * Output always has the same number of lines as the input.
 */
fun reformat(
    text: String,
    rules: IndentRules,
    lineComment: String = "//",
    stringDelimiters: List<Char> = listOf('"'),
): String {
    val lines = text.split('\n')
    val masked = maskText(lines, lineComment, stringDelimiters)
    val unit = if (rules.useSpaces) " ".repeat(rules.indentSize) else "\t"
    val result = ArrayList<String>(lines.size)
    var depth = 0
    var bracketDepth = 0

    fun updateBracketDepth(maskedLine: String) {
        for (ch in maskedLine) when (ch) {
            '(', '[' -> bracketDepth++
            ')', ']' -> bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
        }
    }

    for ((i, line) in lines.withIndex()) {
        // Lines that begin inside a multi-line string must not be touched.
        if (masked.inString[i]) {
            result.add(line)
            updateBracketDepth(masked.lines[i])
            continue
        }

        val stringOpenAtEnd = masked.inString[i + 1]
        // Trailing whitespace of a line that opens a multi-line string belongs
        // to the string literal — only strip the leading indentation then.
        val body = if (stringOpenAtEnd) line.trimStart() else line.trim()
        if (body.isEmpty()) {
            result.add("")
            continue
        }

        val maskedTrimmed = masked.lines[i].trim()
        val opens = rules.indentTriggers.sumOf { countWordOccurrences(maskedTrimmed, it) }
        val closes = rules.dedentTriggers.sumOf { countWordOccurrences(maskedTrimmed, it) }

        // Count dedent triggers leading the line ("انتهى" or "انتهى وإلا ابدأ"):
        // they pull THIS line back, the rest of the change applies after it.
        var leadingDedents = 0
        var rest = maskedTrimmed
        while (true) {
            val w = rules.dedentTriggers.firstOrNull { startsWithWord(rest, it) } ?: break
            leadingDedents++
            rest = rest.substring(w.length).trimStart()
        }

        val lineDepth = (depth - leadingDedents).coerceAtLeast(0)
        // Continuation indent inside an unclosed ( or [ — unless the line
        // itself starts by closing it.
        val startsWithCloser = maskedTrimmed.firstOrNull() == ')' || maskedTrimmed.firstOrNull() == ']'
        val effectiveBracket = if (startsWithCloser) bracketDepth - 1 else bracketDepth
        val continuation = if (effectiveBracket > 0) 1 else 0

        result.add(unit.repeat(lineDepth + continuation) + body)

        depth = (depth + opens - closes).coerceAtLeast(0)
        updateBracketDepth(masked.lines[i])
    }

    return result.joinToString("\n")
}
