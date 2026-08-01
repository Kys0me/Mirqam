package rtlide.editor

import rtlide.lang.analysis.CodeActionEngine
import rtlide.lang.analysis.SakhrAnalyzer

/**
 * Central access point for IDE-wide services.
 * In a larger project, this would be managed by a DI framework.
 */
object IdeServices {
    val analyzer = SakhrAnalyzer()
    val codeActionEngine = CodeActionEngine()
    
    // Future services will be added here:
    // val completionProvider = CompletionProvider()
}
