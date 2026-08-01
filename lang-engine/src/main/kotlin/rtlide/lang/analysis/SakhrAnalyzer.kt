package rtlide.lang.analysis

import rtlide.lang.intelligence.CompletionModel
import rtlide.lang.intelligence.ScopedSymbolExtractor
import rtlide.lang.sakhr.Lexer
import rtlide.lang.sakhr.Parser
import rtlide.lang.sakhr.SakhrModuleResolver
import rtlide.lang.sakhr.SymbolExtractor
import rtlide.lang.sakhr.TypeChecker
import java.io.File

class SakhrAnalyzer(private val stdLibPath: String? = null) {
    fun analyze(source: String, file: File? = null): AnalysisResult {
        val collector = DiagnosticCollector()
        var symbols = emptyList<String>()
        
        try {
            val projectRoot = file?.let { SakhrModuleResolver.findProjectRoot(it) }
            val moduleResolver = SakhrModuleResolver(collector, projectRoot, stdLibPath)
            
            val lexer = Lexer(source, collector)
            val tokens = lexer.scanTokens()
            
            val parser = Parser(tokens, collector)
            val statements = parser.parse()
            
            val extractor = SymbolExtractor()
            symbols = extractor.extract(statements)

            val typeChecker = TypeChecker(collector, moduleResolver)
            typeChecker.check(statements)

            val totalLines = source.count { it == '\n' } + 1
            val scoped = ScopedSymbolExtractor().extract(statements, totalLines)
            // The AST only carries explicit annotations; fill in the types the
            // checker inferred so completion can show them and resolve members.
            val enriched = scoped.symbols.map { sym ->
                if (sym.detail.isEmpty()) {
                    typeChecker.declaredTypes[sym.declLocation]
                        ?.takeIf { it.lexeme != "مجهول" && it.lexeme != "عدم" }
                        ?.let { sym.copy(detail = it.toString()) } ?: sym
                } else sym
            }
            val completion = CompletionModel(
                symbols = enriched,
                structFields = typeChecker.allStructFields,
                extensionMethods = typeChecker.extensionMethods,
                functionRanges = scoped.functionRanges,
                loopRanges = scoped.loopRanges,
                structRanges = scoped.structRanges,
                typeAtLocation = typeChecker.typeAtLocation
            )

            return AnalysisResult(
                collector.diagnostics, 
                symbols, 
                typeChecker.typeAtLocation, 
                typeChecker.allStructFields,
                completion
            )
            
        } catch (_: Exception) {
            // Internal error
        }
        
        return AnalysisResult(collector.diagnostics, symbols)
    }
}
