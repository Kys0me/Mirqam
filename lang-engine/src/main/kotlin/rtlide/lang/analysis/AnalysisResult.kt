package rtlide.lang.analysis

data class AnalysisResult(
    val diagnostics: List<Diagnostic>,
    val symbols: List<String>
)
