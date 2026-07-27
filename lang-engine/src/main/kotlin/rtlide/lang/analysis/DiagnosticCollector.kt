package rtlide.lang.analysis

class DiagnosticCollector {
    private val _diagnostics = mutableListOf<Diagnostic>()
    val diagnostics: List<Diagnostic> get() = _diagnostics

    fun report(message: String, location: Location, severity: Severity, length: Int = 1, fixes: List<QuickFix> = emptyList()) {
        _diagnostics.add(Diagnostic(message, location, severity, length, fixes))
    }

    fun reportError(message: String, location: Location, length: Int = 1, fixes: List<QuickFix> = emptyList()) {
        report(message, location, Severity.Error, length, fixes)
    }

    fun reportWarning(message: String, location: Location, length: Int = 1, fixes: List<QuickFix> = emptyList()) {
        report(message, location, Severity.Warning, length, fixes)
    }
    
    fun clear() {
        _diagnostics.clear()
    }

    companion object {
        fun findClosest(target: String, candidates: Collection<String>): String? {
            if (candidates.isEmpty()) return null
            return candidates.minByOrNull { levenshtein(target, it) }
                ?.takeIf { levenshtein(target, it) <= 2 }
        }

        private fun levenshtein(s1: String, s2: String): Int {
            val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
            for (i in 0..s1.length) dp[i][0] = i
            for (j in 0..s2.length) dp[0][j] = j
            for (i in 1..s1.length) {
                for (j in 1..s2.length) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
                }
            }
            return dp[s1.length][s2.length]
        }
    }
}
