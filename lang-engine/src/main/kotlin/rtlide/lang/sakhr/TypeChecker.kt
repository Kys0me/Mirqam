package rtlide.lang.sakhr

import rtlide.lang.analysis.DiagnosticCollector
import rtlide.lang.analysis.Location
import rtlide.lang.analysis.QuickFix

class TypeChecker(private val diagnostics: DiagnosticCollector) {
    private val scopes = mutableListOf<MutableMap<String, VariableInfo>>()
    private val functions = mutableMapOf<String, MutableList<FunctionSignature>>()
    private var currentFunction: FunctionSignature? = null

    enum class FunctionKind { FUNCTION, EXTENSION }
    data class VariableInfo(
        val type: SakhrType, 
        val isConstant: Boolean, 
        val isDefined: Boolean,
        val location: Location,
        var isUsed: Boolean = false,
        var isReassigned: Boolean = false,
        val isParameter: Boolean = false
    )
    
    data class FunctionSignature(
        val name: String,
        val params: List<SakhrType>,
        val returnType: SakhrType,
        val kind: FunctionKind,
        val receiverType: SakhrType? = null
    )

    init {
        registerBuiltIn("أكتب", listOf(SakhrType.NUMBER), SakhrType.VOID)
        registerBuiltIn("أكتب", listOf(SakhrType.STRING), SakhrType.VOID)
        registerBuiltIn("أكتب", listOf(SakhrType.BOOLEAN), SakhrType.VOID)
        registerBuiltIn("أكتب", listOf(SakhrType.LIST), SakhrType.VOID)
        registerBuiltIn("إنهاء_البرنامج", listOf(SakhrType.NUMBER), SakhrType.VOID)

        registerExtension(SakhrType.NUMBER, "نص", emptyList(), SakhrType.STRING)
        registerExtension(SakhrType.BOOLEAN, "نص", emptyList(), SakhrType.STRING)
        registerExtension(SakhrType.STRING, "نص", emptyList(), SakhrType.STRING)
        registerExtension(SakhrType.LIST, "نص", emptyList(), SakhrType.STRING)
        registerExtension(SakhrType.STRING, "طول", emptyList(), SakhrType.NUMBER)
        registerExtension(SakhrType.LIST, "حجم", emptyList(), SakhrType.NUMBER)
        registerExtension(SakhrType.LIST, "خذ", listOf(SakhrType.NUMBER), SakhrType.NUMBER)

        beginScope() // Global scope
    }

    private fun registerBuiltIn(name: String, params: List<SakhrType>, returnType: SakhrType) {
        val sig = FunctionSignature(name, params, returnType, FunctionKind.FUNCTION)
        functions.getOrPut(name) { mutableListOf() }.add(sig)
    }

    private fun registerExtension(
        receiverType: SakhrType,
        name: String,
        params: List<SakhrType>,
        returnType: SakhrType
    ) {
        val sig = FunctionSignature(name, params, returnType, FunctionKind.EXTENSION, receiverType)
        val key = "${receiverType.lexeme}::${name}"
        functions.getOrPut(key) { mutableListOf() }.add(sig)
    }

    fun check(statements: List<Stmt>) {
        for (stmt in statements) {
            if (stmt is Stmt.Function) {
                val kind = if (stmt.receiverType != null) FunctionKind.EXTENSION else FunctionKind.FUNCTION
                val receiverType = stmt.receiverType?.let { SakhrType.fromLexeme(it.lexeme) }
                val sig = FunctionSignature(
                    stmt.name.lexeme,
                    stmt.params.map { it.type?.let { t -> SakhrType.fromLexeme(t.lexeme) } ?: SakhrType.UNKNOWN },
                    stmt.returnType?.let { SakhrType.fromLexeme(it.lexeme) } ?: SakhrType.VOID,
                    kind,
                    receiverType
                )
                val key = if (kind == FunctionKind.EXTENSION) "${receiverType?.lexeme}::${stmt.name.lexeme}" else stmt.name.lexeme
                functions.getOrPut(key) { mutableListOf() }.add(sig)
            }
        }

        for (stmt in statements) {
            checkStmt(stmt)
        }
        
        endScope() // End global scope and check for unused
    }

    private fun checkStmt(stmt: Stmt) {
        when (stmt) {
            is Stmt.Block -> {
                beginScope()
                stmt.statements.forEach { checkStmt(it) }
                endScope()
            }
            is Stmt.Expression -> {
                checkExpr(stmt.expression)
            }
            is Stmt.Function -> {
                val key = if (stmt.receiverType != null) {
                    "${SakhrType.fromLexeme(stmt.receiverType.lexeme).lexeme}::${stmt.name.lexeme}"
                } else {
                    stmt.name.lexeme
                }
                
                val params = stmt.params.map { it.type?.let { t -> SakhrType.fromLexeme(t.lexeme) } ?: SakhrType.UNKNOWN }
                val returnType = stmt.returnType?.let { SakhrType.fromLexeme(it.lexeme) } ?: SakhrType.VOID
                val sig = functions[key]?.find { it.params == params && it.returnType == returnType }
                    ?: return

                val enclosingFunction = currentFunction
                currentFunction = sig

                beginScope()
                if (sig.kind == FunctionKind.EXTENSION) {
                    scopes.last()["السياق"] = VariableInfo(sig.receiverType!!, isConstant = true, isDefined = true, location = stmt.name.location, isUsed = true)
                }

                for (i in stmt.params.indices) {
                    val param = stmt.params[i]
                    val paramType = sig.params[i]
                    declare(param.name, paramType, isConstant = true, isParameter = true) // Parameters are val by default
                    define(param.name)
                    
                    if (param.type == null && paramType != SakhrType.UNKNOWN) {
                         diagnostics.reportInformation("الوسيط '${param.name.lexeme}' لديه نوع ضمني '${paramType.lexeme}'.", param.name.location, param.name.lexeme.length, listOf(QuickFix("استخدام نوع صريح: ${paramType.lexeme}", "ADD_TYPE:${paramType.lexeme}")))
                    } else if (param.type != null) {
                         diagnostics.reportInformation("الوسيط '${param.name.lexeme}' لديه نوع صريح.", param.name.location, param.name.lexeme.length, listOf(QuickFix("إزالة النوع الصريح", "REMOVE_TYPE")))
                    }
                }
                
                if (stmt.returnType == null && sig.returnType != SakhrType.VOID) {
                     diagnostics.reportInformation("الدالة '${stmt.name.lexeme}' لديها نوع إرجاع ضمني '${sig.returnType.lexeme}'.", stmt.name.location, stmt.name.lexeme.length, listOf(QuickFix("استخدام نوع صريح: ${sig.returnType.lexeme}", "ADD_RETURN_TYPE:${sig.returnType.lexeme}")))
                } else if (stmt.returnType != null) {
                     diagnostics.reportInformation("الدالة '${stmt.name.lexeme}' لديها نوع إرجاع صريح.", stmt.name.location, stmt.name.lexeme.length, listOf(QuickFix("إزالة نوع الإرجاع الصريح", "REMOVE_RETURN_TYPE")))
                }

                stmt.body.forEach { checkStmt(it) }

                if (sig.returnType != SakhrType.VOID && !returnsOnAllPaths(stmt.body)) {
                    diagnostics.reportError(
                        "الدالة '${sig.name}' يجب أن تعيد قيمة من نوع '${sig.returnType.lexeme}'.",
                        stmt.name.location
                    )
                }

                endScope()
                currentFunction = enclosingFunction
            }
            is Stmt.If -> {
                val condType = checkExpr(stmt.condition)
                if (condType != SakhrType.BOOLEAN && condType != SakhrType.UNKNOWN) {
                    diagnostics.reportError(
                        "شرط 'إن كان' يجب أن يكون من نوع 'منطقي'.",
                        getExprLocation(stmt.condition)
                    )
                }
                checkStmt(stmt.thenBranch)
                stmt.elseBranch?.let { checkStmt(it) }
            }
            is Stmt.Let -> {
                val initType = stmt.initializer?.let { checkExpr(it) } ?: SakhrType.UNKNOWN
                val declaredType = stmt.type?.let { SakhrType.fromLexeme(it.lexeme) }
                val finalType = declaredType ?: initType
                
                if (declaredType != null && initType != SakhrType.UNKNOWN && !isAssignable(declaredType, initType)) {
                    diagnostics.reportError(
                        "النوع المصرح به '${declaredType.lexeme}' لا يتطابق مع نوع القيمة '${initType.lexeme}'.",
                        stmt.name.location
                    )
                }
                
                declare(stmt.name, finalType, isConstant = false)
                define(stmt.name)
                
                if (stmt.type == null && finalType != SakhrType.UNKNOWN) {
                    diagnostics.reportInformation("المتغير '${stmt.name.lexeme}' لديه نوع ضمني '${finalType.lexeme}'.", stmt.name.location, stmt.name.lexeme.length, listOf(QuickFix("استخدام نوع صريح: ${finalType.lexeme}", "ADD_TYPE:${finalType.lexeme}")))
                } else if (stmt.type != null) {
                    diagnostics.reportInformation("المتغير '${stmt.name.lexeme}' لديه نوع صريح.", stmt.name.location, stmt.name.lexeme.length, listOf(QuickFix("إزالة النوع الصريح", "REMOVE_TYPE")))
                }
            }
            is Stmt.Const -> {
                val initType = checkExpr(stmt.initializer)
                val declaredType = stmt.type?.let { SakhrType.fromLexeme(it.lexeme) }
                val finalType = declaredType ?: initType

                if (declaredType != null && initType != SakhrType.UNKNOWN && !isAssignable(declaredType, initType)) {
                    diagnostics.reportError(
                        "النوع المصرح به '${declaredType.lexeme}' لا يتطابق مع نوع القيمة '${initType.lexeme}'.",
                        stmt.name.location
                    )
                }

                declare(stmt.name, finalType, isConstant = true)
                define(stmt.name)

                if (stmt.type == null && finalType != SakhrType.UNKNOWN) {
                    diagnostics.reportInformation("الثابت '${stmt.name.lexeme}' لديه نوع ضمني '${finalType.lexeme}'.", stmt.name.location, stmt.name.lexeme.length, listOf(QuickFix("استخدام نوع صريح: ${finalType.lexeme}", "ADD_TYPE:${finalType.lexeme}")))
                } else if (stmt.type != null) {
                    diagnostics.reportInformation("الثابت '${stmt.name.lexeme}' لديه نوع صريح.", stmt.name.location, stmt.name.lexeme.length, listOf(QuickFix("إزالة النوع الصريح", "REMOVE_TYPE")))
                }
            }
            is Stmt.Return -> {
                if (currentFunction == null) {
                    diagnostics.reportError("لا يمكن استخدام 'رجع' خارج الدالة.", stmt.keyword.location)
                }
                val valueType = stmt.value?.let { checkExpr(it) } ?: SakhrType.VOID
                if (currentFunction != null && !isAssignable(currentFunction!!.returnType, valueType)) {
                    diagnostics.reportError(
                        "نوع الراجع '${valueType.lexeme}' لا يتطابق مع نوع إرجاع الدالة '${currentFunction!!.returnType.lexeme}'.",
                        stmt.keyword.location
                    )
                }
            }
        }
    }

    private fun checkExpr(expr: Expr): SakhrType {
        return when (expr) {
            is Expr.Literal -> {
                when (expr.value) {
                    is Double -> SakhrType.NUMBER
                    is String -> SakhrType.STRING
                    is Boolean -> SakhrType.BOOLEAN
                    null -> SakhrType.VOID
                    else -> SakhrType.UNKNOWN
                }
            }
            is Expr.Variable -> {
                val info = lookupVariable(expr.name)
                if (info == null) {
                    val allVariables = scopes.flatMap { it.keys }
                    val suggestion = DiagnosticCollector.findClosest(expr.name.lexeme, allVariables)
                    val msg = if (suggestion != null) "المتغير '${expr.name.lexeme}' غير معرف؛ هل قصدت '$suggestion'؟" 
                             else "المتغير '${expr.name.lexeme}' غير معرف في هذا النطاق."
                    
                    val fixes = mutableListOf<QuickFix>()
                    if (suggestion != null) fixes.add(QuickFix("استخدام $suggestion", suggestion))
                    fixes.add(QuickFix("تعريف المتغير '${expr.name.lexeme}'", "CREATE_VAR:${expr.name.lexeme}"))

                    diagnostics.reportError(msg, expr.name.location, expr.name.lexeme.length, fixes)
                    return SakhrType.UNKNOWN
                }
                info.isUsed = true
                if (!info.isDefined) {
                    diagnostics.reportError("لا يمكن استخدام المتغير '${expr.name.lexeme}' قبل تهيئته.", expr.name.location)
                }
                info.type
            }
            is Expr.Assignment -> {
                val valueType = checkExpr(expr.value)
                val info = lookupVariable(expr.name)
                if (info == null) {
                    val msg = "المتغير '${expr.name.lexeme}' غير معرف."
                    diagnostics.reportError(msg, expr.name.location, expr.name.lexeme.length, listOf(QuickFix("تعريف المتغير '${expr.name.lexeme}'", "CREATE_VAR:${expr.name.lexeme}")))
                } else {
                    info.isReassigned = true
                    if (info.isConstant) {
                        val fixes = listOf(QuickFix("تغيير إلى 'ليكن'", "CHANGE_TO_VAR"))
                        diagnostics.reportError("لا يمكن إعادة تعيين قيمة لـ '${expr.name.lexeme}' لأنه معرف كـ 'ألزم' (أو وسيط دالة).", expr.name.location, expr.name.lexeme.length, fixes)
                    }
                    if (!isAssignable(info.type, valueType)) {
                        diagnostics.reportError(
                            "لا يمكن تعيين قيمة من نوع '${valueType.lexeme}' لمتغير من نوع '${info.type.lexeme}'.",
                            expr.name.location
                        )
                    }
                }
                valueType
            }
            is Expr.Binary -> {
                val leftType = checkExpr(expr.left)
                val rightType = checkExpr(expr.right)
                when (expr.operator.type) {
                    TokenType.PLUS -> {
                        if (leftType == SakhrType.STRING || rightType == SakhrType.STRING) return SakhrType.STRING
                        if (leftType == SakhrType.NUMBER && rightType == SakhrType.NUMBER) return SakhrType.NUMBER
                        SakhrType.UNKNOWN
                    }
                    TokenType.MINUS, TokenType.STAR, TokenType.SLASH -> {
                        if (leftType == SakhrType.NUMBER && rightType == SakhrType.NUMBER) return SakhrType.NUMBER
                        SakhrType.NUMBER
                    }
                    TokenType.GREATER, TokenType.LESS -> {
                        SakhrType.BOOLEAN
                    }
                    TokenType.EQUALS_EQUALS, TokenType.BANG_EQUALS -> SakhrType.BOOLEAN
                    else -> SakhrType.UNKNOWN
                }
            }
            is Expr.Call -> {
                val argTypes = expr.arguments.map { checkExpr(it) }
                when (val callee = expr.callee) {
                    is Expr.Variable -> {
                        val sig = resolveOverload(callee.name.lexeme, argTypes)
                        if (sig == null) {
                            diagnostics.reportError("تعذر العثور على دالة باسم '${callee.name.lexeme}' تطابق هذه الوسائط.", callee.name.location)
                            return SakhrType.UNKNOWN
                        }
                        sig.returnType
                    }
                    is Expr.Get -> {
                        val objType = checkExpr(callee.obj)
                        val methodName = callee.name.lexeme
                        val sig = resolveOverload("${objType.lexeme}::${methodName}", argTypes)
                        if (sig == null) {
                            diagnostics.reportError("النوع '${objType.lexeme}' لا يحتوي على دالة ممتدة باسم '${methodName}' تطابق هذه الوسائط.", callee.name.location)
                            return SakhrType.UNKNOWN
                        }
                        sig.returnType
                    }
                    else -> {
                        checkExpr(callee)
                        SakhrType.UNKNOWN
                    }
                }
            }
            is Expr.Get -> {
                val objType = checkExpr(expr.obj)
                if (objType == SakhrType.LIST && expr.name.lexeme == "حجم") return SakhrType.NUMBER
                SakhrType.UNKNOWN
            }
            is Expr.Context -> {
                if (currentFunction?.kind != FunctionKind.EXTENSION) {
                    diagnostics.reportError("لا يمكن استخدام الكلمة المفتاحية 'السياق' إلا داخل الدوال الممتدة.", expr.keyword.location)
                    return SakhrType.UNKNOWN
                }
                currentFunction?.receiverType ?: SakhrType.UNKNOWN
            }
            is Expr.Grouping -> checkExpr(expr.expression)
        }
    }

    private fun resolveOverload(name: String, argTypes: List<SakhrType>): FunctionSignature? {
        val sigs = functions[name] ?: return null
        return sigs.find { sig ->
            if (sig.params.size != argTypes.size) return@find false
            for (i in sig.params.indices) {
                if (!isAssignable(sig.params[i], argTypes[i])) return@find false
            }
            true
        }
    }

    private fun returnsOnAllPaths(statements: List<Stmt>): Boolean {
        for (stmt in statements) {
            if (returnsOnAllPaths(stmt)) return true
        }
        return false
    }

    private fun returnsOnAllPaths(stmt: Stmt): Boolean {
        return when (stmt) {
            is Stmt.Return -> true
            is Stmt.Block -> returnsOnAllPaths(stmt.statements)
            is Stmt.If -> {
                if (stmt.elseBranch == null) false
                else returnsOnAllPaths(stmt.thenBranch) && returnsOnAllPaths(stmt.elseBranch)
            }
            else -> false
        }
    }

    private fun lookupVariable(name: Token): VariableInfo? {
        for (i in scopes.size - 1 downTo 0) {
            val info = scopes[i][name.lexeme]
            if (info != null) return info
        }
        return null
    }

    private fun declare(name: Token, type: SakhrType, isConstant: Boolean, isParameter: Boolean = false) {
        if (scopes.isEmpty()) return
        val scope = scopes.last()
        if (scope.containsKey(name.lexeme)) {
            diagnostics.reportError("تم تعريف الاسم '${name.lexeme}' مسبقاً في هذا النطاق.", name.location)
        }
        scope[name.lexeme] = VariableInfo(type, isConstant, false, name.location, isParameter = isParameter)
    }

    private fun define(name: Token) {
        if (scopes.isEmpty()) return
        val scope = scopes.last()
        val info = scope[name.lexeme]
        if (info != null) {
            scope[name.lexeme] = info.copy(isDefined = true)
        }
    }

    private fun isAssignable(target: SakhrType, source: SakhrType): Boolean {
        if (target == SakhrType.UNKNOWN || source == SakhrType.UNKNOWN) return true
        return target == source
    }

    private fun beginScope() {
        scopes.add(mutableMapOf())
    }

    private fun endScope() {
        val lastScope = scopes.removeAt(scopes.size - 1)
        for ((name, info) in lastScope) {
            if (info.isParameter) continue // Skip all warnings for parameters as requested
            
            if (!info.isUsed && name != "السياق") {
                diagnostics.reportWarning("المتغير '$name' غير مستخدم.", info.location, name.length)
            } else if (!info.isConstant && !info.isReassigned && name != "السياق") {
                 diagnostics.reportWarning("المتغير '$name' لا يتم تعديله؛ يفضل تعريفه باستخدام 'ألزم'.", info.location, name.length, listOf(QuickFix("استخدام 'ألزم'", "ألزم")))
            }
        }
    }

    private fun getExprLocation(expr: Expr): Location {
        return when (expr) {
            is Expr.Variable -> expr.name.location
            is Expr.Binary -> expr.operator.location
            is Expr.Call -> expr.paren.location
            is Expr.Get -> expr.name.location
            is Expr.Assignment -> expr.name.location
            is Expr.Context -> expr.keyword.location
            is Expr.Grouping -> getExprLocation(expr.expression)
            else -> Location(0, 0)
        }
    }
}
