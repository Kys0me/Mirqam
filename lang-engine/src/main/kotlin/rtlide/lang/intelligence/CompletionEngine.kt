package rtlide.lang.intelligence

import rtlide.lang.analysis.Location
import rtlide.lang.sakhr.SakhrType
import rtlide.lang.sakhr.Stmt

/** Symbol kinds shown in the completion popup (drives the icon and the ranking). */
enum class SymbolKind { PARAMETER, VARIABLE, CONSTANT, FIELD, METHOD, FUNCTION, STRUCT, TYPE, VALUE, KEYWORD }

/**
 * A declared name together with the line range where it is visible.
 * Lines are 1-based (same convention as [Location]).
 */
data class ScopedSymbol(
    val name: String,
    val kind: SymbolKind,
    val detail: String = "",
    val declLocation: Location,
    val scopeStartLine: Int,
    val scopeEndLine: Int,
    val paramCount: Int = -1,
)

/** One row in the completion popup. */
data class CompletionItem(
    val label: String,
    val kind: SymbolKind,
    val detail: String = "",
    /** >= 0 when the item is callable; the editor then inserts parentheses. */
    val paramCount: Int = -1,
)

/** Everything the analyzer knows that completion needs, snapshot per analysis. */
data class CompletionModel(
    val symbols: List<ScopedSymbol> = emptyList(),
    val structFields: Map<String, List<String>> = emptyMap(),
    /** Receiver type lexeme -> (method name -> parameter count). */
    val extensionMethods: Map<String, Map<String, Int>> = emptyMap(),
    val functionRanges: List<IntRange> = emptyList(),
    val loopRanges: List<IntRange> = emptyList(),
    val structRanges: List<IntRange> = emptyList(),
    val typeAtLocation: Map<Location, SakhrType> = emptyMap(),
)

/**
 * Walks the AST and produces [ScopedSymbol]s with real visibility ranges,
 * plus the function/loop/struct body ranges used to gate context keywords
 * (رد، اكفف، امض) the way IntelliJ does.
 */
class ScopedSymbolExtractor {

    data class Result(
        val symbols: List<ScopedSymbol>,
        val functionRanges: List<IntRange>,
        val loopRanges: List<IntRange>,
        val structRanges: List<IntRange>,
    )

    private val symbols = mutableListOf<ScopedSymbol>()
    private val functionRanges = mutableListOf<IntRange>()
    private val loopRanges = mutableListOf<IntRange>()
    private val structRanges = mutableListOf<IntRange>()

    fun extract(statements: List<Stmt>, totalLines: Int): Result {
        walk(statements, 1..totalLines.coerceAtLeast(1))
        return Result(symbols, functionRanges, loopRanges, structRanges)
    }

    private fun walk(statements: List<Stmt>, scope: IntRange) {
        // Functions and structs are hoisted: visible across the whole scope.
        for (stmt in statements) {
            when (stmt) {
                is Stmt.Function -> if (stmt.receiverType == null) {
                    val params = stmt.params.joinToString("، ") { it.name.lexeme }
                    val ret = stmt.returnType?.let { ": ${it.lexeme}" } ?: ""
                    symbols += ScopedSymbol(
                        stmt.name.lexeme, SymbolKind.FUNCTION, "($params)$ret",
                        stmt.name.location, scope.first, scope.last, stmt.params.size
                    )
                }
                is Stmt.Struct -> symbols += ScopedSymbol(
                    stmt.name.lexeme, SymbolKind.STRUCT, "بنية",
                    stmt.name.location, scope.first, scope.last, stmt.fields.size
                )
                else -> {}
            }
        }

        for (stmt in statements) {
            when (stmt) {
                // Variables become visible on the line after their declaration,
                // never inside their own initializer.
                is Stmt.Let -> for (name in stmt.names) {
                    symbols += ScopedSymbol(
                        name.lexeme, SymbolKind.VARIABLE, stmt.type?.lexeme ?: "",
                        name.location, name.location.line + 1, scope.last
                    )
                }
                is Stmt.Const -> for (name in stmt.names) {
                    symbols += ScopedSymbol(
                        name.lexeme, SymbolKind.CONSTANT, stmt.type?.lexeme ?: "",
                        name.location, name.location.line + 1, scope.last
                    )
                }
                is Stmt.Function -> {
                    val body = stmt.keyword.location.line..stmt.endToken.location.line
                    functionRanges += body
                    for (p in stmt.params) {
                        symbols += ScopedSymbol(
                            p.name.lexeme, SymbolKind.PARAMETER, p.type?.lexeme ?: "",
                            p.name.location, body.first, body.last
                        )
                    }
                    walk(stmt.body, body)
                }
                is Stmt.Struct -> structRanges += stmt.keyword.location.line..stmt.endToken.location.line
                is Stmt.Block -> walk(stmt.statements, blockRange(stmt, scope))
                is Stmt.If -> {
                    walkBody(stmt.thenBranch, scope)
                    stmt.elseBranch?.let { walkBody(it, scope) }
                }
                is Stmt.While -> {
                    val body = stmt.keyword.location.line..endLine(stmt.body, scope.last)
                    loopRanges += body
                    walkBody(stmt.body, body)
                }
                is Stmt.ForEach -> {
                    val body = stmt.keyword.location.line..endLine(stmt.body, scope.last)
                    loopRanges += body
                    stmt.indexVar?.let {
                        symbols += ScopedSymbol(it.lexeme, SymbolKind.VARIABLE, "رقم", it.location, body.first, body.last)
                    }
                    symbols += ScopedSymbol(
                        stmt.elementVar.lexeme, SymbolKind.VARIABLE, "",
                        stmt.elementVar.location, body.first, body.last
                    )
                    walkBody(stmt.body, body)
                }
                else -> {}
            }
        }
    }

    private fun walkBody(body: Stmt, enclosing: IntRange) {
        when (body) {
            is Stmt.Block -> walk(body.statements, blockRange(body, enclosing))
            else -> walk(listOf(body), enclosing)
        }
    }

    private fun blockRange(block: Stmt.Block, enclosing: IntRange): IntRange =
        enclosing.first..(block.endToken?.location?.line ?: enclosing.last)

    private fun endLine(stmt: Stmt, fallback: Int): Int = when (stmt) {
        is Stmt.Block -> stmt.endToken?.location?.line
            ?: stmt.statements.lastOrNull()?.let { endLine(it, fallback) } ?: fallback
        is Stmt.Function -> stmt.endToken.location.line
        is Stmt.Struct -> stmt.endToken.location.line
        is Stmt.If -> maxOf(
            endLine(stmt.thenBranch, fallback),
            stmt.elseBranch?.let { endLine(it, fallback) } ?: 0
        )
        is Stmt.While -> endLine(stmt.body, fallback)
        is Stmt.ForEach -> endLine(stmt.body, fallback)
        else -> fallback
    }
}

/**
 * Scope- and context-aware completion for Sakhr, modelled after IntelliJ:
 * only symbols visible at the caret are offered, and the candidate set is
 * driven by the syntactic position (member access, type annotation, naming
 * position, statement start, expression).
 */
object CompletionEngine {

    private val TYPE_NAMES = listOf("رقم", "نص", "منطقي", "قائمة", "عدم")
    private val VALUE_CONSTANTS = listOf("صح", "خطأ", "فارغ")
    private val NAMING_KEYWORDS = setOf("ليكن", "ألزم", "إجراء", "بنية")
    private val STATEMENT_KEYWORDS = listOf("ليكن", "ألزم", "إجراء", "بنية", "إن كان", "ما دام", "لكل")

    private val BUILTIN_FUNCTIONS = listOf(
        CompletionItem("أكتب", SymbolKind.FUNCTION, "(قيمة): عدم", 1),
        CompletionItem("اقرأ", SymbolKind.FUNCTION, "(): نص", 0),
        CompletionItem("إنهاء_البرنامج", SymbolKind.FUNCTION, "(رمز): عدم", 1),
        CompletionItem("رقم", SymbolKind.FUNCTION, "(قيمة): رقم", 1),
        CompletionItem("نص", SymbolKind.FUNCTION, "(قيمة): نص", 1),
        CompletionItem("منطقي", SymbolKind.FUNCTION, "(قيمة): منطقي", 1),
    )

    private val WORD = Regex("""[\p{L}_][\p{L}\p{N}_]*""")
    // لكل ( … caret is naming the index/element variable.
    private val FOR_NAMING = Regex("""لكل\s*\(\s*([\p{L}_][\p{L}\p{N}_]*\s*،\s*)?$""")
    // لكل (عنصر … the only sensible next token is 'في'.
    private val FOR_IN = Regex("""لكل\s*\(\s*[\p{L}_][\p{L}\p{N}_]*\s*(،\s*[\p{L}_][\p{L}\p{N}_]*\s*)?$""")
    // إجراء المطلع ( …
    private val MAIN_ARGS = Regex("""إجراء\s+المطلع\s*\(\s*$""")

    fun suggest(
        lines: List<String>,
        caretLine: Int, // 0-based
        caretCol: Int,  // 0-based
        model: CompletionModel,
        explicit: Boolean = false,
    ): List<CompletionItem> {
        val lineText = lines.getOrElse(caretLine) { "" }
        val col = caretCol.coerceIn(0, lineText.length)
        val caretLine1 = caretLine + 1

        var wordStart = col
        while (wordStart > 0 && isIdentChar(lineText[wordStart - 1])) wordStart--
        val prefix = lineText.substring(wordStart, col)
        val head = lineText.substring(0, wordStart)
        val trimmedHead = head.trimEnd()
        val lastWord = WORD.findAll(trimmedHead).lastOrNull()
            ?.takeIf { it.range.last == trimmedHead.lastIndex }?.value ?: ""

        // 1. Member access: fields and methods of the receiver's type only.
        if (trimmedHead.endsWith(".")) {
            return finish(memberItems(lineText, trimmedHead, caretLine1, model), prefix, showOnEmpty = true)
        }

        // 2. Naming positions: a brand-new identifier is expected, offer nothing.
        if (lastWord in NAMING_KEYWORDS) return emptyList()
        if (FOR_NAMING.containsMatchIn(trimmedHead)) return emptyList()
        if (MAIN_ARGS.containsMatchIn(trimmedHead)) {
            return finish(listOf(CompletionItem("وسائط: قائمة(نص)", SymbolKind.PARAMETER)), prefix, showOnEmpty = true)
        }

        // 3. Type annotation: after ':' only types and structs make sense.
        if (trimmedHead.endsWith(":")) {
            val items = TYPE_NAMES.map { CompletionItem(it, SymbolKind.TYPE, "نوع مضمن") } +
                visibleAt(model, caretLine1).filter { it.kind == SymbolKind.STRUCT }.map { it.toItem(noParams = true) }
            return finish(items, prefix, showOnEmpty = true)
        }

        // 4. Multi-word keyword continuations.
        if (lastWord == "إن") return finish(listOf(CompletionItem("كان", SymbolKind.KEYWORD)), prefix, showOnEmpty = true)
        if (lastWord == "ما") return finish(listOf(CompletionItem("دام", SymbolKind.KEYWORD)), prefix, showOnEmpty = true)

        // 5. Structural follow-ups: إذن بعد الشرط، كرر بعد ما دام، ابدأ بعدهما، في داخل لكل.
        structuralKeyword(trimmedHead)?.let {
            return finish(listOf(CompletionItem(it, SymbolKind.KEYWORD)), prefix, showOnEmpty = true)
        }
        if (FOR_IN.containsMatchIn(trimmedHead) && !trimmedHead.endsWith("في")) {
            return finish(listOf(CompletionItem("في", SymbolKind.KEYWORD)), prefix, showOnEmpty = true)
        }

        val inFunction = model.functionRanges.any { caretLine1 in it }
        val inLoop = model.loopRanges.any { caretLine1 in it }
        val inStructBody = model.structRanges.any { caretLine1 in it } && !inFunction
        val atStatementStart = trimmedHead.isEmpty()

        // Inside a struct body a statement start means naming a new field.
        if (atStatementStart && inStructBody) return emptyList()

        val items = mutableListOf<CompletionItem>()
        if (atStatementStart) {
            items += STATEMENT_KEYWORDS.map { CompletionItem(it, SymbolKind.KEYWORD) }
            if (inFunction) items += CompletionItem("رد", SymbolKind.KEYWORD)
            if (inLoop) {
                items += CompletionItem("اكفف", SymbolKind.KEYWORD)
                items += CompletionItem("امض", SymbolKind.KEYWORD)
            }
            if (openBlockCount(lines, caretLine) > 0) {
                items += CompletionItem("انتهى", SymbolKind.KEYWORD)
            }
            if (previousCodeLine(lines, caretLine)?.trim()?.endsWith("انتهى") == true) {
                items += CompletionItem("وإلا", SymbolKind.KEYWORD)
            }
            items += visibleAt(model, caretLine1).map { it.toItem() }
            items += BUILTIN_FUNCTIONS
        } else {
            // Expression position: values, visible symbols and callables.
            items += visibleAt(model, caretLine1).map { it.toItem() }
            items += BUILTIN_FUNCTIONS
            items += VALUE_CONSTANTS.map { CompletionItem(it, SymbolKind.VALUE, "قيمة") }
            items += CompletionItem("ليس", SymbolKind.KEYWORD)
        }
        return finish(items, prefix, showOnEmpty = explicit)
    }

    // ---- context helpers -------------------------------------------------

    private fun structuralKeyword(trimmedHead: String): String? {
        val t = trimmedHead.trimStart()
        if (t.isEmpty()) return null
        return when {
            t.endsWith("إذن") || t.endsWith("كرر") || t.endsWith("وإلا") -> "ابدأ"
            !t.endsWith(")") -> null
            t.startsWith("إن كان") && !t.contains("إذن") -> "إذن"
            t.startsWith("ما دام") && !t.contains("كرر") -> "كرر"
            t.startsWith("لكل") -> "ابدأ"
            else -> null
        }
    }

    private fun memberItems(
        lineText: String,
        trimmedHead: String,
        caretLine1: Int,
        model: CompletionModel,
    ): List<CompletionItem> {
        var idEnd = trimmedHead.length - 1 // index of '.'
        while (idEnd > 0 && lineText[idEnd - 1].isWhitespace()) idEnd--
        var idStart = idEnd
        while (idStart > 0 && isIdentChar(lineText[idStart - 1])) idStart--
        val receiver = lineText.substring(idStart, idEnd)
        if (receiver.isEmpty()) return emptyList()

        // Prefer the type the analyzer recorded for this exact occurrence
        // (columns are 1-based); fall back to the receiver's declared type,
        // which survives while the current line does not parse yet.
        val typeLexeme = model.typeAtLocation[Location(caretLine1, idStart + 1)]?.lexeme
            ?: visibleAt(model, caretLine1)
                .lastOrNull { it.name == receiver && it.kind != SymbolKind.FUNCTION && it.kind != SymbolKind.STRUCT }
                ?.detail?.let { baseTypeName(it) }
            ?: return emptyList()

        val items = mutableListOf<CompletionItem>()
        model.structFields[typeLexeme]?.forEach { field ->
            items += CompletionItem(field, SymbolKind.FIELD, typeLexeme)
        }
        model.extensionMethods[typeLexeme]?.forEach { (name, paramCount) ->
            items += CompletionItem(name, SymbolKind.METHOD, "دالة", paramCount)
        }
        return items
    }

    /** "قائمة(رقم)؟" -> "قائمة", "نقطة؟" -> "نقطة". */
    private fun baseTypeName(detail: String): String? =
        detail.substringBefore("(").removeSuffix("؟").trim().ifEmpty { null }

    private fun visibleAt(model: CompletionModel, line: Int): List<ScopedSymbol> =
        model.symbols.filter { line in it.scopeStartLine..it.scopeEndLine }

    private fun openBlockCount(lines: List<String>, caretLine: Int): Int {
        var depth = 0
        for (i in 0 until caretLine.coerceAtMost(lines.size)) {
            for (m in WORD.findAll(lines[i])) {
                when (m.value) {
                    "ابدأ" -> depth++
                    "انتهى" -> depth--
                }
            }
        }
        return depth
    }

    private fun previousCodeLine(lines: List<String>, caretLine: Int): String? {
        for (i in caretLine - 1 downTo 0) {
            val l = lines.getOrNull(i) ?: return null
            if (l.isNotBlank()) return l
        }
        return null
    }

    private fun finish(
        items: List<CompletionItem>,
        prefix: String,
        showOnEmpty: Boolean = false,
    ): List<CompletionItem> {
        if (prefix.isEmpty() && !showOnEmpty) return emptyList()
        return items.asSequence()
            .filter { it.label != prefix && (prefix.isEmpty() || it.label.startsWith(prefix)) }
            .distinctBy { it.label }
            .sortedWith(compareBy({ rank(it.kind) }, { it.label }))
            .take(16)
            .toList()
    }

    private fun rank(kind: SymbolKind): Int = when (kind) {
        SymbolKind.PARAMETER, SymbolKind.VARIABLE, SymbolKind.FIELD -> 0
        SymbolKind.CONSTANT, SymbolKind.METHOD, SymbolKind.TYPE -> 1
        SymbolKind.FUNCTION -> 2
        SymbolKind.STRUCT -> 3
        SymbolKind.VALUE -> 4
        SymbolKind.KEYWORD -> 5
    }

    private fun isIdentChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    private fun ScopedSymbol.toItem(noParams: Boolean = false) =
        CompletionItem(name, kind, detail, if (noParams) -1 else paramCount)
}
