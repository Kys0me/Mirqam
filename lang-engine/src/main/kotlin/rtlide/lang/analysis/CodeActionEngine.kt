package rtlide.lang.analysis

import rtlide.core.document.Caret
import rtlide.core.document.TextEdit

class CodeActionEngine {
    
    fun getActionsForDiagnostic(diagnostic: Diagnostic, lineText: String): List<CodeAction> {
        val location = diagnostic.location
        val length = diagnostic.length
        
        return diagnostic.fixes.mapNotNull { fix ->
            resolveAction(fix, location, length, lineText)
        }
    }

    private fun resolveAction(fix: QuickFix, location: Location, length: Int, lineText: String): CodeAction? {
        val lineIndex = location.line - 1
        val colIndex = location.column - 1
        val replacement = fix.replacement

        return when {
            replacement == "CHANGE_TO_VAR" -> {
                val startOfLine = lineText.substring(0, colIndex)
                val constIndex = startOfLine.lastIndexOf("ألزم")
                if (constIndex != -1) {
                    QuickFixAction("تحويل إلى متغير (ليكن)", listOf(
                        TextEdit(Caret(lineIndex, constIndex), Caret(lineIndex, constIndex + 4), "ليكن")
                    ))
                } else null
            }
            replacement == "ألزم" -> {
                val startOfLine = lineText.substring(0, colIndex)
                val letIndex = startOfLine.lastIndexOf("ليكن")
                if (letIndex != -1) {
                    QuickFixAction("تحويل إلى ثابت (ألزم)", listOf(
                        TextEdit(Caret(lineIndex, letIndex), Caret(lineIndex, letIndex + 4), "ألزم")
                    ))
                } else null
            }
            replacement.startsWith("ADD_TYPE:") -> {
                val typeName = replacement.removePrefix("ADD_TYPE:")
                QuickFixAction("إضافة نوع: $typeName", listOf(
                    TextEdit(Caret(lineIndex, colIndex + length), Caret(lineIndex, colIndex + length), ": $typeName")
                ))
            }
            replacement == "REMOVE_TYPE" -> {
                val afterId = if (colIndex + length < lineText.length) lineText.substring(colIndex + length) else ""
                val colonIndex = afterId.indexOf(':')
                if (colonIndex != -1) {
                    val fromColon = afterId.substring(colonIndex)
                    var typeEnd = 1
                    while (typeEnd < fromColon.length && (fromColon[typeEnd].isLetter() || fromColon[typeEnd].isWhitespace())) {
                        typeEnd++
                    }
                    if (typeEnd > 1 && fromColon[typeEnd - 1].isWhitespace()) {
                        typeEnd--
                    }
                    QuickFixAction("إزالة النوع", listOf(
                        TextEdit(Caret(lineIndex, colIndex + length + colonIndex), Caret(lineIndex, colIndex + length + colonIndex + typeEnd), "")
                    ))
                } else null
            }
            replacement.startsWith("ADD_RETURN_TYPE:") -> {
                val typeName = replacement.removePrefix("ADD_RETURN_TYPE:")
                val afterFn = lineText.substring(colIndex)
                val closingParen = afterFn.indexOf(')')
                if (closingParen != -1) {
                    QuickFixAction("إضافة نوع إرجاع: $typeName", listOf(
                        TextEdit(Caret(lineIndex, colIndex + closingParen + 1), Caret(lineIndex, colIndex + closingParen + 1), ": $typeName")
                    ))
                } else null
            }
            replacement == "REMOVE_RETURN_TYPE" -> {
                val afterFn = lineText.substring(colIndex)
                val closingParen = afterFn.indexOf(')')
                if (closingParen != -1) {
                    val afterParen = afterFn.substring(closingParen + 1)
                    val colonIndex = afterParen.indexOf(':')
                    if (colonIndex != -1) {
                         val fromColon = afterParen.substring(colonIndex)
                         var typeEnd = 1
                         while (typeEnd < fromColon.length && (fromColon[typeEnd].isLetter() || fromColon[typeEnd].isWhitespace())) {
                             typeEnd++
                         }
                         QuickFixAction("إزالة نوع الإرجاع", listOf(
                             TextEdit(Caret(lineIndex, colIndex + closingParen + 1 + colonIndex), Caret(lineIndex, colIndex + closingParen + 1 + colonIndex + typeEnd), "")
                         ))
                    } else null
                } else null
            }
            replacement == "SAFE_DELETE_VAR" || replacement == "SAFE_DELETE_FUNCTION" -> {
                val (start, end) = getDeleteRange(fix, lineIndex, colIndex, length)
                QuickFixAction("حذف آمن", listOf(TextEdit(start, end, "")))
            }
            replacement == "SAFE_DELETE_PARAM" -> {
                val startParen = lineText.lastIndexOf('(', colIndex)
                val endParen = lineText.indexOf(')', colIndex) // Note: simple search, doesn't handle nested parens
                if (startParen != -1 && endParen != -1 && endParen > startParen) {
                    val (unitStartCaret, unitEndCaret) = getDeleteRange(fix, lineIndex, colIndex, length)
                    val unitStart = unitStartCaret.col
                    val unitEnd = unitEndCaret.col
                    
                    if (unitStart > startParen && unitEnd <= lineText.length) {
                        var deleteStart = unitStart
                        var deleteEnd = unitEnd
                        val beforeUnit = lineText.substring(startParen + 1, unitStart)
                        val afterUnit = lineText.substring(unitEnd).let { if (it.indexOf(')') != -1) it.substring(0, it.indexOf(')')) else it }
                        
                        if (afterUnit.contains('،')) {
                            val commaPos = afterUnit.indexOf('،')
                            deleteEnd = unitEnd + commaPos + 1
                            while (deleteEnd < lineText.length && lineText[deleteEnd].isWhitespace()) deleteEnd++
                        } else if (beforeUnit.contains('،')) {
                            val commaPos = beforeUnit.lastIndexOf('،')
                            deleteStart = startParen + 1 + commaPos
                        } else {
                            deleteStart = (startParen + 1 + beforeUnit.takeWhile { it.isWhitespace() }.length).coerceAtMost(unitStart)
                            // If it's the only param, we might want to keep spaces or delete them all.
                            // The current logic deletes surrounding whitespace.
                            val trailingSpaces = lineText.substring(unitEnd).takeWhile { it.isWhitespace() }.length
                            deleteEnd = unitEnd + trailingSpaces
                        }
                        QuickFixAction("حذف آمن", listOf(
                            TextEdit(Caret(lineIndex, deleteStart), Caret(lineIndex, deleteEnd), "")
                        ))
                    } else null
                } else null
            }
            // Other cases like ADD_INITIALIZER require UI input, so they might stay in EditorTab for now
            // or we return a special Action type that UI handles.
            replacement.contains("_") -> null // Probably an internal command
            else -> {
                // Default simple replacement
                QuickFixAction(fix.label, listOf(
                    TextEdit(Caret(lineIndex, colIndex), Caret(lineIndex, colIndex + length), replacement)
                ))
            }
        }
    }

    private fun getDeleteRange(fix: QuickFix, lineIndex: Int, colIndex: Int, length: Int): Pair<Caret, Caret> {
        val startCaret = Caret(lineIndex, colIndex + fix.startColOffset)
        val endLine = lineIndex + fix.endLineOffset
        val endCol = if (fix.endColOffset != null) {
            if (fix.endLineOffset == 0) startCaret.col + fix.endColOffset else fix.endColOffset - 1
        } else {
            colIndex + length
        }
        return startCaret to Caret(endLine, endCol)
    }
}
