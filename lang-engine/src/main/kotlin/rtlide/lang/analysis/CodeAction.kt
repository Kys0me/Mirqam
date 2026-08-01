package rtlide.lang.analysis

import rtlide.core.document.Caret
import rtlide.core.document.TextEdit

/**
 * A code action (Quick Fix or Intention) that can be applied to the document.
 */
interface CodeAction {
    val title: String
    val edits: List<TextEdit>
}

data class QuickFixAction(
    override val title: String,
    override val edits: List<TextEdit>
) : CodeAction
