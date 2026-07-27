package rtlide.lang.analysis

import rtlide.lang.sakhr.Lexer
import rtlide.lang.sakhr.Parser
import rtlide.lang.sakhr.SymbolExtractor
import rtlide.lang.sakhr.TypeChecker

class SakhrAnalyzer {
    fun analyze(source: String): AnalysisResult {
        val collector = DiagnosticCollector()
        var symbols = emptyList<String>()
        
        try {
            val lexer = Lexer(source, collector)
            val tokens = lexer.scanTokens()
            
            val parser = Parser(tokens, collector)
            val statements = parser.parse()
            
            val extractor = SymbolExtractor()
            symbols = extractor.extract(statements)

            val typeChecker = TypeChecker(collector)
            typeChecker.check(statements)
            
        } catch (_: Exception) {
            // Internal error
        }
        
        return AnalysisResult(collector.diagnostics, symbols)
    }
}
