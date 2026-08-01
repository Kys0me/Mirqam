package rtlide.lang.sakhr

import rtlide.lang.analysis.DiagnosticCollector
import rtlide.lang.analysis.Location
import java.io.File

class SakhrModuleResolver(
    private val diagnostics: DiagnosticCollector,
    private val projectRoot: File? = null,
    private val stdLibPath: String? = null
) {
    private val moduleCache = mutableMapOf<String, SakhrModule>()
    private val loadingStack = mutableListOf<String>()

    fun resolve(import: Stmt.Import): SakhrModule? {
        val pathString = import.path.joinToString(".") { it.lexeme }
        val key = if (import.isStdLib) "الأم.$pathString" else pathString
        
        if (moduleCache.containsKey(key)) return moduleCache[key]

        if (loadingStack.contains(key)) {
            diagnostics.reportError("تم اكتشاف حلقة استجلاب دائرية: ${loadingStack.joinToString(" -> ")} -> $key", import.path.first().location)
            return null
        }

        val file = getFile(import)
        if (file == null || !file.exists()) {
            diagnostics.reportError("تعذر العثور على الوحدة '$pathString' في المسار المتوقع.", import.path.first().location)
            return null
        }

        loadingStack.add(key)
        val module = loadModule(file, import.path.last().lexeme, import.isStdLib)
        loadingStack.removeAt(loadingStack.size - 1)

        if (module != null) {
            moduleCache[key] = module
        }
        return module
    }

    private fun loadModule(file: File, name: String, isStdLib: Boolean): SakhrModule? {
        return try {
            val source = file.readText()
            val collector = DiagnosticCollector()
            val lexer = Lexer(source, collector)
            val tokens = lexer.scanTokens()
            
            val parser = Parser(tokens, collector)
            val statements = parser.parse()
            
            // Merge diagnostics from imported module into the main collector
            // In an IDE, we might want to keep them separate, but for type checking
            // we at least need to know if the imported module failed to parse.
            if (collector.diagnostics.any { it.severity == rtlide.lang.analysis.Severity.Error }) {
                diagnostics.reportError("فشل تحليل الوحدة المستجلبة '$name' بسبب أخطاء في ملفها.", Location(1, 1))
                // Optionally report the specific errors if we want them to show up in the main file
            }

            SakhrModule(name, file.absolutePath, statements, isStdLib)
        } catch (e: Exception) {
            null
        }
    }

    private fun getFile(import: Stmt.Import): File? {
        val relativePath = import.path.joinToString("/") { it.lexeme } + ".صخر"
        return if (import.isStdLib) {
            stdLibPath?.let { File(it, relativePath) }
        } else {
            projectRoot?.let { File(it, relativePath) } ?: File(relativePath)
        }
    }

    companion object {
        fun findProjectRoot(file: File): File? {
            var current = file.parentFile
            while (current != null) {
                if (File(current, "صخر").exists()) return current
                current = current.parentFile
            }
            return null
        }
    }
}

data class SakhrModule(
    val name: String,
    val path: String,
    val statements: List<Stmt>,
    val isStdLib: Boolean
)
