package rtlide.lang.tokenizer

import rtlide.lang.schema.Grammar

data class Token(val start: Int, val end: Int, val scope: String)

/**
 * Single-line, grammar-driven scanner. It classifies identifiers by set
 * membership rather than \b regex boundaries, which is important for Arabic:
 * Java's default \b/\w are ASCII-only and would mis-tokenize Arabic keywords.
 *
 * (Block comments / strings spanning multiple lines are intentionally out of
 * scope for this scaffold; add a carried LineState to support them.)
 */
class Tokenizer(private val g: Grammar) {

    private val numberRegex = Regex(g.numberPattern)
    private val controls = g.controlKeywords.toHashSet()
    private val storage = g.keywords.toHashSet()
    private val builtins = g.builtins.toHashSet()
    private val constants = g.constants.toHashSet()

    fun tokenize(line: String): List<Token> {
        val out = ArrayList<Token>()
        val n = line.length
        var i = 0
        while (i < n) {
            val c = line[i]

            if (c.isWhitespace()) { i++; continue }

            // Line comment: consumes the rest of the line.
            if (g.lineComment.isNotEmpty() && line.startsWith(g.lineComment, i)) {
                out += Token(i, n, "comment.line")
                break
            }

            // String literal.
            var consumed = false
            for (rule in g.strings) {
                if (rule.begin.isNotEmpty() && line.startsWith(rule.begin, i)) {
                    var j = i + rule.begin.length
                    while (j < n) {
                        if (rule.escape.isNotEmpty() && line.startsWith(rule.escape, j)) { j += rule.escape.length + 1; continue }
                        if (line.startsWith(rule.end, j)) { j += rule.end.length; break }
                        j++
                    }
                    val end = j.coerceAtMost(n)
                    out += Token(i, end, "string.quoted")
                    i = end
                    consumed = true
                    break
                }
            }
            if (consumed) continue

            // Number (ASCII or Arabic-Indic digits).
            val m = numberRegex.matchAt(line, i)
            if (m != null && m.range.first == i) {
                val end = m.range.last + 1
                out += Token(i, end, "constant.numeric")
                i = end
                continue
            }

            // Identifier / keyword (Unicode letters, so Arabic words are one token).
            if (c.isLetter() || c == '_') {
                var j = i + 1
                while (j < n && (line[j].isLetter() || line[j].isDigit() || line[j] == '_')) j++
                var word = line.substring(i, j)

                // Multi-word keyword support: check for "إن كان" or "ما دام"
                if (word == "إن" && j < n && line[j] == ' ') {
                    val k = j + 1
                    if (line.substring(k).startsWith("كان")) {
                        val endOfKan = k + 3
                        if (endOfKan == n || !(line[endOfKan].isLetter() || line[endOfKan].isDigit() || line[endOfKan] == '_')) {
                            word = "إن كان"
                            j = endOfKan
                        }
                    }
                } else if (word == "ما" && j < n && line[j] == ' ') {
                    val k = j + 1
                    if (line.substring(k).startsWith("دام")) {
                        val endOfDam = k + 3
                        if (endOfDam == n || !(line[endOfDam].isLetter() || line[endOfDam].isDigit() || line[endOfDam] == '_')) {
                            word = "ما دام"
                            j = endOfDam
                        }
                    }
                }

                val scope = when (word) {
                    in controls -> "keyword.control.arabic"
                    in storage -> "storage.type.arabic"
                    in constants -> "constant.language.arabic"
                    in builtins -> "support.function.builtin"
                    else -> {
                        var k = j
                        while (k < n && line[k].isWhitespace()) k++
                        if (k < n && line[k] == '(') "entity.name.function" else "identifier"
                    }
                }
                out += Token(i, j, scope)
                i = j
                continue
            }

            // Operator / punctuation — single char, left with the default color.
            i++
        }
        return out
    }
}
