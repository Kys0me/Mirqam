package rtlide.lang.analysis

import rtlide.lang.sakhr.Lexer
import rtlide.lang.sakhr.Parser
import rtlide.lang.sakhr.TypeChecker

class SakhrAnalyzer {
    fun analyze(source: String): List<Diagnostic> {
        val collector = DiagnosticCollector()
        
        try {
            val lexer = Lexer(source, collector)
            val tokens = lexer.scanTokens()
            
            // Even if there are lexer errors, we try to parse
            val parser = Parser(tokens, collector)
            val statements = parser.parse()
            
            // If there are syntax errors, we might still want to type check what we have,
            // but usually it's better to stop or have a more robust type checker.
            // For now, let's proceed to catch semantic warnings like unused vars.
            val typeChecker = TypeChecker(collector)
            typeChecker.check(statements)
            
        } catch (_: Exception) {
            // Internal error or unhandled parse error
            // collector.reportError("خطأ داخلي في المحلل: ${e.message}", Location(1, 1))
        }
        
        return collector.diagnostics
    }
}
