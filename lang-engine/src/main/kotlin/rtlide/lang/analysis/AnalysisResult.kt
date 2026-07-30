package rtlide.lang.analysis

import rtlide.lang.intelligence.CompletionModel
import rtlide.lang.sakhr.SakhrType

data class AnalysisResult(
    val diagnostics: List<Diagnostic>,
    val symbols: List<String>,
    val typeAtLocation: Map<Location, SakhrType> = emptyMap(),
    val structFields: Map<String, List<String>> = emptyMap(),
    val completion: CompletionModel = CompletionModel()
)
