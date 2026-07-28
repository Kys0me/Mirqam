package rtlide.lang.analysis

data class Location(val line: Int, val column: Int)

enum class Severity {
    Error,
    Warning,
    Information,
    Hint
}

data class QuickFix(
    val label: String, 
    val replacement: String,
    val startColOffset: Int = 0, 
    val endLineOffset: Int = 0,
    val endColOffset: Int? = null 
)

data class Diagnostic(
    val message: String,
    val location: Location,
    val severity: Severity,
    val length: Int = 1,
    val fixes: List<QuickFix> = emptyList()
)
