package rtlide.lang.sakhr

import rtlide.lang.analysis.DiagnosticCollector
import rtlide.lang.analysis.Location
import rtlide.lang.analysis.QuickFix

class TypeChecker(
    private val diagnostics: DiagnosticCollector,
    private val moduleResolver: SakhrModuleResolver? = null
) {
    private val scopes = mutableListOf<Scope>()
    private val checkedModules = mutableMapOf<String, Scope>()
    private var currentFunction: FunctionSignature? = null
    private var loopDepth = 0
    
    val typeAtLocation = mutableMapOf<Location, SakhrType>()
    val allStructFields = mutableMapOf<String, List<String>>()
    // Receiver type lexeme -> (extension method name -> param count), for dot-completion.
    val extensionMethods = mutableMapOf<String, MutableMap<String, Int>>()
    // Declaration location -> resolved type, so completion can show/use inferred types.
    val declaredTypes = mutableMapOf<Location, SakhrType>()

    class Scope {
        val variables = mutableMapOf<String, VariableInfo>()
        val functions = mutableMapOf<String, MutableList<FunctionSignature>>()
        val structs = mutableMapOf<String, StructInfo>()
    }

    enum class FunctionKind { FUNCTION, EXTENSION }
    data class VariableInfo(
        val type: SakhrType,
        val isConstant: Boolean,
        val isDefined: Boolean,
        val location: Location,
        var isUsed: Boolean = false,
        var isReassigned: Boolean = false,
        val isParameter: Boolean = false,
        val fixOffset: Int = 0,
        val fixLength: Int? = null
    )

    data class StructInfo(
        val name: String,
        val fields: Map<String, SakhrType>,
        val fieldNames: List<String>,
        val requiredFields: Set<String>,
        val location: Location,
        var isUsed: Boolean = false,
        val usedFields: MutableSet<String> = mutableSetOf(),
        val fieldLocations: Map<String, Location> = emptyMap(),
        val fieldLengths: Map<String, Int> = emptyMap(),
        val startLocation: Location? = null,
        val endLocation: Location? = null,
        val isEnum: Boolean = false
    )

    data class FunctionSignature(
        val name: String,
        val params: MutableList<SakhrType>,
        val returnType: SakhrType,
        val kind: FunctionKind,
        val receiverType: SakhrType? = null,
        val location: Location,
        var isUsed: Boolean = false,
        val isBuiltIn: Boolean = false,
        val startLocation: Location? = null,
        val endLocation: Location? = null,
        val paramNames: List<String> = emptyList(),
        val isParamRequired: List<Boolean> = emptyList()
    )

    init {
        beginScope() // Global scope
        
        // --- Built-ins ---
        registerBuiltIn("أكتب", listOf(SakhrType.UNKNOWN), SakhrType.VOID)
        registerBuiltIn("إنهاء_البرنامج", listOf(SakhrType.NUMBER), SakhrType.VOID)
        registerBuiltIn("اقرأ", emptyList(), SakhrType.STRING)
        registerBuiltIn("رقم", listOf(SakhrType.UNKNOWN), SakhrType.NUMBER)
        registerBuiltIn("نص", listOf(SakhrType.UNKNOWN), SakhrType.STRING)
        registerBuiltIn("منطقي", listOf(SakhrType.UNKNOWN), SakhrType.BOOLEAN)

        // --- Extensions ---
        registerExtension(SakhrType.NUMBER, "كنص", emptyList(), SakhrType.STRING)
        registerExtension(SakhrType.BOOLEAN, "كنص", emptyList(), SakhrType.STRING)
        registerExtension(SakhrType.STRING, "كنص", emptyList(), SakhrType.STRING)
        registerExtension(SakhrType.LIST, "كنص", emptyList(), SakhrType.STRING)

        registerExtension(SakhrType.STRING, "طول", emptyList(), SakhrType.NUMBER)
        registerExtension(SakhrType.LIST, "حجم", emptyList(), SakhrType.NUMBER)
        registerExtension(SakhrType.LIST, "أضف", listOf(SakhrType.UNKNOWN), SakhrType.VOID)
        registerExtension(SakhrType.LIST, "أزل", listOf(SakhrType.UNKNOWN), SakhrType.VOID)
        registerExtension(SakhrType.LIST, "أدخل", listOf(SakhrType.NUMBER, SakhrType.UNKNOWN), SakhrType.VOID)
        registerExtension(SakhrType.LIST, "فهرس", listOf(SakhrType.UNKNOWN), SakhrType.NUMBER)
        registerExtension(SakhrType.LIST, "استبدل", listOf(SakhrType.NUMBER, SakhrType.UNKNOWN), SakhrType.VOID)
        registerExtension(SakhrType.LIST, "خذ", listOf(SakhrType.NUMBER), SakhrType.UNKNOWN)
    }

    private fun registerBuiltIn(name: String, params: List<SakhrType>, returnType: SakhrType) {
        val sig = FunctionSignature(
            name, params.toMutableList(), returnType, FunctionKind.FUNCTION,
            location = Location(0, 0), isBuiltIn = true,
            isParamRequired = List(params.size) { true }
        )
        scopes[0].functions.getOrPut(name) { mutableListOf() }.add(sig)
    }

    private fun registerExtension(receiverType: SakhrType, name: String, params: List<SakhrType>, returnType: SakhrType) {
        val sig = FunctionSignature(
            name, params.toMutableList(), returnType, FunctionKind.EXTENSION, receiverType,
            location = Location(0, 0), isBuiltIn = true,
            isParamRequired = List(params.size) { true }
        )
        val key = "${receiverType.lexeme}::${name}"
        scopes[0].functions.getOrPut(key) { mutableListOf() }.add(sig)
        extensionMethods.getOrPut(receiverType.lexeme) { mutableMapOf() }[name] = params.size
    }

    fun check(module: SakhrModule) {
        if (checkedModules.containsKey(module.path)) return
        
        beginScope()
        checkInternal(module.statements)
        checkedModules[module.path] = scopes.removeAt(scopes.size - 1)
    }

    fun check(statements: List<Stmt>) {
        checkInternal(statements)
        endScope() // Process global scope
    }

    private fun checkInternal(statements: List<Stmt>): Boolean {
        collectSignatures(statements)

        var terminated = false
        for (stmt in statements) {
            if (terminated) {
                val loc = getStmtStartLocation(stmt)
                diagnostics.reportWarning("كود غير قابل للوصول.", loc, 1) // Just mark the start
                break
            }
            checkStmt(stmt)
            if (stmtTerminates(stmt)) {
                terminated = true
            }
        }
        return terminated
    }

    private fun stmtTerminates(stmt: Stmt): Boolean = when (stmt) {
        is Stmt.Return -> true
        is Stmt.Raise -> true
        is Stmt.Break -> true
        is Stmt.Continue -> true
        is Stmt.Block -> checkInternal(stmt.statements)
        is Stmt.If -> {
            val thenTerminates = if (stmt.thenBranch is Stmt.Block) {
                checkInternal(stmt.thenBranch.statements)
            } else {
                stmtTerminates(stmt.thenBranch)
            }
            val elseTerminates = stmt.elseBranch?.let {
                if (it is Stmt.Block) checkInternal(it.statements) else stmtTerminates(it)
            } ?: false
            thenTerminates && elseTerminates
        }
        is Stmt.Match -> {
            val allCasesTerminate = stmt.cases.all { 
                if (it.body is Stmt.Block) checkInternal(it.body.statements) else stmtTerminates(it.body)
            }
            val defaultTerminates = stmt.defaultBranch?.let {
                if (it is Stmt.Block) checkInternal(it.statements) else stmtTerminates(it)
            } ?: false
            allCasesTerminate && defaultTerminates
        }
        else -> false
    }

    private fun getStmtStartLocation(stmt: Stmt): Location = when (stmt) {
        is Stmt.Block -> stmt.statements.firstOrNull()?.let { getStmtStartLocation(it) } ?: Location(0, 0)
        is Stmt.Expression -> getExprLocation(stmt.expression)
        is Stmt.Function -> stmt.keyword.location
        is Stmt.If -> stmt.keyword.location
        is Stmt.While -> stmt.keyword.location
        is Stmt.ForEach -> stmt.keyword.location
        is Stmt.Break -> stmt.keyword.location
        is Stmt.Continue -> stmt.keyword.location
        is Stmt.Let -> stmt.keyword.location
        is Stmt.Const -> stmt.keyword.location
        is Stmt.Return -> stmt.keyword.location
        is Stmt.Raise -> stmt.keyword.location
        is Stmt.Struct -> stmt.keyword.location
        is Stmt.Enum -> stmt.keyword.location
        is Stmt.Match -> stmt.keyword.location
        is Stmt.Import -> stmt.keyword.location
    }

    private fun importSymbols(from: Scope, location: Location) {
        val to = scopes.last()
        
        for ((name, info) in from.variables) {
            if (to.variables.containsKey(name)) {
                diagnostics.reportError("تضارب في الأسماء: المتغير '$name' معرف بالفعل.", location)
            } else {
                to.variables[name] = info
            }
        }
        
        for ((name, sigs) in from.functions) {
            val toSigs = to.functions.getOrPut(name) { mutableListOf() }
            for (sig in sigs) {
                if (toSigs.none { it.params == sig.params && it.kind == sig.kind && it.receiverType == sig.receiverType }) {
                    toSigs.add(sig)
                }
            }
        }
        
        for ((name, info) in from.structs) {
            if (to.structs.containsKey(name)) {
                diagnostics.reportError("تضارب في الأسماء: البنية '$name' معرفة بالفعل.", location)
            } else {
                to.structs[name] = info
            }
        }
    }

    private fun collectSignatures(statements: List<Stmt>) {
        for (stmt in statements) {
            when (stmt) {
                is Stmt.Import -> {
                    if (moduleResolver == null) {
                        diagnostics.reportError("لا يمكن استخدام 'استجلب' في هذا السياق.", stmt.path.first().location)
                        continue
                    }
                    val module = moduleResolver.resolve(stmt)
                    if (module != null) {
                        check(module)
                        checkedModules[module.path]?.let { importedScope ->
                            importSymbols(importedScope, stmt.path.first().location)
                        }
                    }
                }
                is Stmt.Function -> {
                    val kind = if (stmt.receiverType != null) FunctionKind.EXTENSION else FunctionKind.FUNCTION
                    val receiverType = resolveAndMarkType(stmt.receiverType)
                    
                    val params = stmt.params.map { p ->
                        if (p.type == null) {
                            if (stmt.name.lexeme == "المطلع") SakhrType.LIST else SakhrType.UNKNOWN
                        } else {
                            resolveAndMarkType(p.type)!!
                        }
                    }.toMutableList()
                    
                    val returnType = resolveAndMarkType(stmt.returnType) ?: SakhrType.VOID
                    
                    val sig = FunctionSignature(
                        stmt.name.lexeme, params, returnType, kind, receiverType,
                        location = stmt.name.location,
                        startLocation = stmt.keyword.location,
                        endLocation = stmt.endToken.location.let { Location(it.line, it.column + stmt.endToken.lexeme.length) },
                        paramNames = stmt.params.map { it.name.lexeme },
                        isParamRequired = stmt.params.map { it.defaultValue == null }
                    )
                    val key = if (kind == FunctionKind.EXTENSION) "${receiverType?.lexeme}::${stmt.name.lexeme}" else stmt.name.lexeme
                    scopes.last().functions.getOrPut(key) { mutableListOf() }.add(sig)
                    if (kind == FunctionKind.EXTENSION && receiverType != null) {
                        extensionMethods.getOrPut(receiverType.lexeme) { mutableMapOf() }[stmt.name.lexeme] = stmt.params.size
                    }
                }
                is Stmt.Struct -> {
                    val fieldMap = mutableMapOf<String, SakhrType>()
                    val fieldNames = mutableListOf<String>()
                    val requiredFields = mutableSetOf<String>()
                    val fieldLocations = mutableMapOf<String, Location>()
                    val fieldLengths = mutableMapOf<String, Int>()
                    for (field in stmt.fields) {
                        val type = resolveAndMarkType(field.type) ?: SakhrType.UNKNOWN
                        val name = field.name.lexeme
                        fieldMap[name] = type
                        fieldNames.add(name)
                        fieldLocations[name] = field.name.location
                        
                        val fieldEnd = when {
                            field.initializer != null -> exprEnd(field.initializer)
                            field.type != null -> tokenEnd(field.type)
                            else -> tokenEnd(field.name)
                        }
                        fieldLengths[name] = if (fieldEnd.line == field.name.location.line) fieldEnd.column - field.name.location.column else field.name.lexeme.length

                        if (field.initializer == null) {
                            requiredFields.add(name)
                        }
                    }
                    val info = StructInfo(
                        stmt.name.lexeme, fieldMap, fieldNames, requiredFields, stmt.name.location,
                        fieldLocations = fieldLocations, fieldLengths = fieldLengths,
                        startLocation = stmt.keyword.location,
                        endLocation = tokenEnd(stmt.endToken)
                    )
                    scopes.last().structs[stmt.name.lexeme] = info
                    allStructFields[stmt.name.lexeme] = fieldNames
                }
                is Stmt.Enum -> {
                    val fieldMap = mutableMapOf<String, SakhrType>()
                    val fieldNames = mutableListOf<String>()
                    val fieldLocations = mutableMapOf<String, Location>()
                    val fieldLengths = mutableMapOf<String, Int>()
                    val enumType = SakhrType(stmt.name.lexeme)
                    for (member in stmt.members) {
                        fieldMap[member.lexeme] = enumType
                        fieldNames.add(member.lexeme)
                        fieldLocations[member.lexeme] = member.location
                        fieldLengths[member.lexeme] = member.lexeme.length
                    }
                    val info = StructInfo(
                        stmt.name.lexeme, fieldMap, fieldNames, fieldNames.toSet(), stmt.name.location,
                        fieldLocations = fieldLocations, fieldLengths = fieldLengths,
                        startLocation = stmt.keyword.location,
                        endLocation = tokenEnd(stmt.endToken),
                        isEnum = true
                    )
                    scopes.last().structs[stmt.name.lexeme] = info
                    allStructFields[stmt.name.lexeme] = fieldNames
                }
                else -> {}
            }
        }
    }

    private fun checkStmt(stmt: Stmt): Boolean {
        var terminated = false
        when (stmt) {
            is Stmt.Block -> {
                beginScope()
                terminated = checkInternal(stmt.statements)
                endScope()
            }

            is Stmt.Expression -> {
                checkExpr(stmt.expression)
            }

            is Stmt.Function -> {
                val key = if (stmt.receiverType != null) {
                    "${resolveAndMarkType(stmt.receiverType)!!.lexeme}::${stmt.name.lexeme}"
                } else {
                    stmt.name.lexeme
                }
                
                val initialParams = stmt.params.map { p ->
                    if (p.type == null) {
                        if (stmt.name.lexeme == "المطلع") SakhrType.LIST else SakhrType.UNKNOWN
                    } else {
                        resolveAndMarkType(p.type)!!
                    }
                }
                val returnType = resolveAndMarkType(stmt.returnType) ?: SakhrType.VOID
                
                val sig = scopes.last().functions[key]?.find { it.params == initialParams && it.returnType == returnType } ?: return false

                val enclosingFunction = currentFunction
                currentFunction = sig

                beginScope()
                if (sig.kind == FunctionKind.EXTENSION) {
                    scopes.last().variables["السياق"] = VariableInfo(sig.receiverType!!, isConstant = true, isDefined = true, location = stmt.name.location, isUsed = true)
                }

                for (i in stmt.params.indices) {
                    val param = stmt.params[i]
                    val paramType = sig.params[i]
                    
                    val paramEnd = when {
                        param.defaultValue != null -> exprEnd(param.defaultValue)
                        param.type != null -> tokenEnd(param.type)
                        else -> tokenEnd(param.name)
                    }
                    val paramLen = if (paramEnd.line == param.name.location.line) paramEnd.column - param.name.location.column else param.name.lexeme.length

                    declare(param.name, paramType, isConstant = true, isParameter = true, fixOffset = 0, fixLength = paramLen)
                    define(param.name)
                    
                    if (param.type == null && paramType != SakhrType.UNKNOWN) {
                         diagnostics.reportInformation("الوسيط '${param.name.lexeme}' لديه نوع ضمني '${paramType.lexeme}'.", param.name.location, param.name.lexeme.length, listOf(QuickFix("استخدام نوع صريح: ${paramType.lexeme}", "ADD_TYPE:${paramType.lexeme}")))
                    }
                }
                
                if (stmt.returnType == null && sig.returnType != SakhrType.VOID) {
                     diagnostics.reportInformation("الدالة '${stmt.name.lexeme}' لديها نوع إرجاع ضمني '${sig.returnType.lexeme}'.", stmt.name.location, stmt.name.lexeme.length, listOf(QuickFix("استخدام نوع صريح: ${sig.returnType.lexeme}", "ADD_RETURN_TYPE:${sig.returnType.lexeme}")))
                }

                val bodyTerminates = checkInternal(stmt.body)

                if (sig.returnType != SakhrType.VOID && !bodyTerminates) {
                    diagnostics.reportError("الدالة '${sig.name}' يجب أن تعيد قيمة من نوع '${sig.returnType.lexeme}'.", stmt.name.location, stmt.name.lexeme.length)
                }

                endScope()
                currentFunction = enclosingFunction
            }

            is Stmt.If -> {
                val condType = checkExpr(stmt.condition)
                if (condType != SakhrType.BOOLEAN && condType != SakhrType.UNKNOWN) {
                    val (loc, len) = getExprRange(stmt.condition)
                    diagnostics.reportError("شرط 'إن كان' يجب أن يكون من نوع 'منطقي'.", loc, len)
                }
                val thenT = checkStmt(stmt.thenBranch)
                val elseT = stmt.elseBranch?.let { checkStmt(it) } ?: false
                terminated = thenT && elseT
            }

            is Stmt.While -> {
                val condType = checkExpr(stmt.condition)
                if (condType != SakhrType.BOOLEAN && condType != SakhrType.UNKNOWN) {
                    val (loc, len) = getExprRange(stmt.condition)
                    diagnostics.reportError("شرط 'ما دام' يجب أن يكون من نوع 'منطقي'.", loc, len)
                }
                loopDepth++
                checkStmt(stmt.body)
                loopDepth--
            }

            is Stmt.ForEach -> {
                val iterableType = checkExpr(stmt.iterable)
                if (iterableType.lexeme != "قائمة" && iterableType != SakhrType.UNKNOWN) {
                    val (loc, len) = getExprRange(stmt.iterable)
                    diagnostics.reportError("لا يمكن استخدام 'لكل' إلا مع قائمة.", loc, len)
                }
                beginScope()
                stmt.indexVar?.let {
                    declare(it, SakhrType.NUMBER, isConstant = true)
                    define(it)
                }
                declare(stmt.elementVar, iterableType.elementType ?: SakhrType.UNKNOWN, isConstant = true)
                define(stmt.elementVar)
                loopDepth++
                checkStmt(stmt.body)
                loopDepth--
                endScope()
            }

            is Stmt.Break -> {
                if (loopDepth == 0) diagnostics.reportError("لا يمكن استخدام 'اكفف' خارج حلقة.", stmt.keyword.location, stmt.keyword.lexeme.length)
                terminated = true
            }
            is Stmt.Continue -> {
                if (loopDepth == 0) diagnostics.reportError("لا يمكن استخدام 'امض' خارج حلقة.", stmt.keyword.location, stmt.keyword.lexeme.length)
                terminated = true
            }

            is Stmt.Let -> {
                val explicitType = resolveAndMarkType(stmt.type)
                val initType = stmt.initializer?.let { checkExpr(it) } ?: SakhrType.VOID
                
                val fixOffset = stmt.keyword.location.column - stmt.names[0].location.column
                val lastToken = stmt.endToken ?: stmt.names.last()
                val fixLength = (lastToken.location.column + lastToken.lexeme.length) - stmt.keyword.location.column

                if (stmt.names.size > 1) {
                    for (name in stmt.names) {
                        declare(name, SakhrType.UNKNOWN, isConstant = false, fixOffset = fixOffset, fixLength = fixLength)
                        define(name)
                    }
                } else {
                    val name = stmt.names[0]
                    val finalType = explicitType ?: initType
                    if (explicitType != null && stmt.initializer != null && !isAssignable(explicitType, initType)) {
                        val (loc, len) = getExprRange(stmt.initializer)
                        diagnostics.reportError("لا يمكن تعيين '${initType}' لمتغير من نوع '${explicitType}'.", loc, len)
                    }
                    declare(name, finalType, isConstant = false, fixOffset = fixOffset, fixLength = fixLength)
                    if (stmt.initializer != null) define(name)
                    if (stmt.type == null && finalType != SakhrType.UNKNOWN && finalType != SakhrType.VOID) {
                        diagnostics.reportInformation("المتغير '${name.lexeme}' لديه نوع ضمني '${finalType.lexeme}'.", name.location, name.lexeme.length, listOf(QuickFix("استخدام نوع صريح: ${finalType.lexeme}", "ADD_TYPE:${finalType.lexeme}")))
                    }
                }
            }

            is Stmt.Const -> {
                val explicitType = resolveAndMarkType(stmt.type)
                val initType = checkExpr(stmt.initializer)
                
                val fixOffset = stmt.keyword.location.column - stmt.names[0].location.column
                val lastToken = stmt.endToken ?: stmt.names.last()
                val fixLength = (lastToken.location.column + lastToken.lexeme.length) - stmt.keyword.location.column

                if (stmt.names.size > 1) {
                    for (name in stmt.names) {
                        declare(name, SakhrType.UNKNOWN, isConstant = true, fixOffset = fixOffset, fixLength = fixLength)
                        define(name)
                    }
                } else {
                    val name = stmt.names[0]
                    val finalType = explicitType ?: initType
                    if (explicitType != null && !isAssignable(explicitType, initType)) {
                        val (loc, len) = getExprRange(stmt.initializer)
                        diagnostics.reportError("لا يمكن تعيين '${initType}' لثابت من نوع '${explicitType}'.", loc, len)
                    }
                    declare(name, finalType, isConstant = true, fixOffset = fixOffset, fixLength = fixLength)
                    define(name)
                }
            }

            is Stmt.Return -> {
                if (currentFunction == null) diagnostics.reportError("لا يمكن استخدام 'رد' خارج الدالة.", stmt.keyword.location, stmt.keyword.lexeme.length)
                val valueType = stmt.value?.let { checkExpr(it) } ?: SakhrType.VOID
                if (currentFunction != null && !isAssignable(currentFunction!!.returnType, valueType)) {
                    diagnostics.reportError("نوع الإرجاع غير متوافق. المتوقع: '${currentFunction!!.returnType}', الموجود: '$valueType'.", stmt.keyword.location, stmt.keyword.lexeme.length)
                }
                terminated = true
            }

            is Stmt.Raise -> {
                checkExpr(stmt.message)
                terminated = true
            }
            
            is Stmt.Match -> {
                checkExpr(stmt.expression)
                var allCasesTerminate = true
                for (case in stmt.cases) {
                    if (!checkStmt(case.body)) allCasesTerminate = false
                }
                val defaultTerminates = stmt.defaultBranch?.let { checkStmt(it) } ?: false
                terminated = allCasesTerminate && defaultTerminates
            }
            
            is Stmt.Import -> {
                 // Already handled in collectSignatures
            }
            
            is Stmt.Struct -> {
                val struct = lookupStruct(stmt.name.lexeme) ?: return false
                for (field in stmt.fields) {
                    if (field.initializer != null) {
                        val initType = checkExpr(field.initializer)
                        val fieldType = struct.fields[field.name.lexeme] ?: SakhrType.UNKNOWN
                        if (!isAssignable(fieldType, initType)) {
                            val (loc, len) = getExprRange(field.initializer)
                            diagnostics.reportError("نوع القيمة المبدئية للحقل '${field.name.lexeme}' لا يتطابق مع نوع الحقل (${fieldType.lexeme}).", loc, len)
                        }
                    }
                }
            }
            
            is Stmt.Enum -> {
                // Members checked in collectSignatures
            }
        }
        return terminated
    }

    private fun checkExpr(expr: Expr): SakhrType {
        val type = when (expr) {
            is Expr.Literal -> when (expr.value) {
                is Double -> SakhrType.NUMBER
                is String -> SakhrType.STRING
                is Boolean -> SakhrType.BOOLEAN
                SakhrUnit -> SakhrType.VOID
                null -> SakhrType.NULL_LITERAL
                else -> SakhrType.UNKNOWN
            }

            is Expr.Variable -> {
                val info = lookupVariable(expr.name)
                if (info == null) {
                    val struct = lookupStruct(expr.name.lexeme)
                    if (struct != null) return SakhrType(struct.name)
                    
                    diagnostics.reportError("المتغير '${expr.name.lexeme}' غير معرف.", expr.name.location, expr.name.lexeme.length)
                    return SakhrType.UNKNOWN
                }
                info.isUsed = true
                if (!info.isDefined) diagnostics.reportError("استخدام '${expr.name.lexeme}' قبل تهيئته.", expr.name.location, expr.name.lexeme.length)
                info.type
            }

            is Expr.Assignment -> {
                val valueType = checkExpr(expr.value)
                val info = lookupVariable(expr.name)
                if (info == null) {
                    // Check if we are inside a call (named argument)
                    // This is hard to check here without context.
                    // Instead, we can let the caller handle it.
                    // But checkExpr is recursive.
                    
                    // Actually, the compiler's version didn't have this issue because 
                    // it didn't use checkExpr for the whole Assignment when it was a named arg.
                    
                    diagnostics.reportError("المتغير '${expr.name.lexeme}' غير معرف.", expr.name.location, expr.name.lexeme.length)
                } else {
                    info.isReassigned = true
                    if (info.isConstant) {
                        val fixes = listOf(QuickFix("تغيير إلى 'ليكن'", "CHANGE_TO_VAR"))
                        diagnostics.reportError("لا يمكن تعديل الثابت '${expr.name.lexeme}'.", expr.name.location, expr.name.lexeme.length, fixes)
                    }
                    if (!isAssignable(info.type, valueType)) {
                        val (loc, len) = getExprRange(expr.value)
                        diagnostics.reportError("عدم توافق الأنواع في التعيين.", loc, len)
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
                    TokenType.MINUS, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT -> SakhrType.NUMBER
                    TokenType.GREATER, TokenType.GREATER_EQUALS, TokenType.LESS, TokenType.LESS_EQUALS -> SakhrType.BOOLEAN
                    TokenType.EQUALS_EQUALS, TokenType.BANG_EQUALS -> SakhrType.BOOLEAN
                    else -> SakhrType.UNKNOWN
                }
            }

            is Expr.Logical -> {
                checkExpr(expr.left); checkExpr(expr.right)
                SakhrType.BOOLEAN
            }

            is Expr.Unary -> {
                checkExpr(expr.right)
                if (expr.operator.type == TokenType.MINUS) SakhrType.NUMBER else SakhrType.BOOLEAN
            }

            is Expr.ListLiteral -> {
                val elementTypes = expr.elements.map { checkExpr(it) }
                val commonType = if (elementTypes.isEmpty()) null else elementTypes.reduce { acc, t -> if (acc == t) acc else SakhrType.UNKNOWN }
                SakhrType("قائمة", if (commonType == SakhrType.UNKNOWN) null else commonType)
            }

            is Expr.Index -> {
                val objType = checkExpr(expr.obj)
                checkExpr(expr.index)
                objType.elementType ?: SakhrType.UNKNOWN
            }

            is Expr.Call -> {
                val positional = mutableListOf<SakhrType>()
                val named = mutableMapOf<String, SakhrType>()
                for (arg in expr.arguments) {
                    if (arg is Expr.Assignment) {
                        named[arg.name.lexeme] = checkExpr(arg.value)
                    } else {
                        positional.add(checkExpr(arg))
                    }
                }

                if (expr.callee is Expr.Variable) {
                    val name = expr.callee.name.lexeme
                    val struct = lookupStruct(name)
                    if (struct != null) return validateStructCall(struct, positional, named, expr.callee.name.location, expr.callee.name.lexeme.length)

                    val varInfo = lookupVariable(expr.callee.name)
                    if (varInfo != null) {
                        varInfo.isUsed = true
                        if (varInfo.type.isFunction) {
                            val sig = varInfo.type
                            if (positional.size != sig.parameterTypes!!.size) {
                                diagnostics.reportError("عدد الوسائط الممررة لا يتطابق مع تعريف الدالة.", expr.callee.name.location, expr.callee.name.lexeme.length)
                            }
                            // Simplified arg check for now
                            return sig.returnType ?: SakhrType.UNKNOWN
                        }
                    }

                    val sig = resolveAndCapture(name, positional, named)
                    if (sig != null) return sig.returnType

                    // No matching callable: report at the callee name, like IntelliJ.
                    if (varInfo != null) {
                        diagnostics.reportError("'${name}' ليس دالة ولا يمكن استدعاؤه.", expr.callee.name.location, expr.callee.name.lexeme.length)
                    } else if (lookupFunctions(name).isNotEmpty()) {
                        diagnostics.reportError("لا توجد نسخة من الدالة '${name}' تقبل هذه الوسائط.", expr.callee.name.location, expr.callee.name.lexeme.length)
                    } else {
                        val candidates = visibleCallableNames()
                        val closest = DiagnosticCollector.findClosest(name, candidates)
                        val hint = if (closest != null) " هل تقصد '${closest}'؟" else ""
                        val fixes = if (closest != null) listOf(QuickFix("تغيير إلى '${closest}'", closest)) else emptyList()
                        diagnostics.reportError("الدالة '${name}' غير معرفة.$hint", expr.callee.name.location, expr.callee.name.lexeme.length, fixes)
                    }
                    return SakhrType.UNKNOWN
                }
                
                if (expr.callee is Expr.Get) {
                    val objType = checkExpr(expr.callee.obj)
                    val methodName = expr.callee.name.lexeme
                    val sig = resolveAndCapture("${objType.lexeme}::${methodName}", positional, named)
                    if (sig != null) return sig.returnType

                    // Only report when the receiver type is known and it is not a
                    // struct field access being invoked.
                    val structInfo = lookupStruct(objType.lexeme)
                    val isField = structInfo?.fields?.containsKey(methodName) == true
                    if (objType != SakhrType.UNKNOWN && !isField) {
                        diagnostics.reportError("النوع '${objType.lexeme}' لا يحتوي على دالة باسم '$methodName'.", expr.callee.name.location, expr.callee.name.lexeme.length)
                    }
                }
                
                SakhrType.UNKNOWN
            }

            is Expr.Get -> {
                val objType = checkExpr(expr.obj)
                val propertyName = expr.name.lexeme

                if (objType.isOptional) {
                    diagnostics.reportWarning("الوصول إلى خاصية على نوع اختياري '${objType.lexeme}؟' قد يؤدي لخطأ إن كانت القيمة فارغة.", expr.name.location, expr.name.lexeme.length)
                }

                val struct = lookupStruct(objType.lexeme)
                if (struct != null) {
                    struct.usedFields.add(propertyName)
                    val fieldType = struct.fields[propertyName]
                    if (fieldType != null) return fieldType
                    
                    diagnostics.reportError("البنية '${objType.lexeme}' لا تحتوي على خاصية باسم '$propertyName'.", expr.name.location, expr.name.lexeme.length)
                } else {
                    val sig = resolveAndCapture("${objType.lexeme}::$propertyName", emptyList(), emptyMap<String, SakhrType>())
                    if (sig != null) return sig.returnType

                    if (objType != SakhrType.UNKNOWN) {
                        diagnostics.reportError("النوع '${objType.lexeme}' لا يحتوي على خاصية باسم '$propertyName'.", expr.name.location, expr.name.lexeme.length)
                    }
                }
                SakhrType.UNKNOWN
            }
            
            is Expr.Set -> {
                val valueType = checkExpr(expr.value)
                val objType = checkExpr(expr.obj)
                val propertyName = expr.name.lexeme

                if (objType.isOptional) {
                    diagnostics.reportError("لا يمكن تعيين قيمة لخاصية في نوع اختياري '${objType.lexeme}؟' دون التحقق من وجود القيمة.", expr.name.location, expr.name.lexeme.length)
                }

                val struct = lookupStruct(objType.lexeme)
                if (struct != null) {
                    struct.usedFields.add(propertyName)
                    val fieldType = struct.fields[propertyName]
                    if (fieldType != null) {
                        if (!isAssignable(fieldType, valueType)) {
                            val (loc, len) = getExprRange(expr.value)
                            diagnostics.reportError("عدم توافق نوع الحقل؛ يتوقع '${fieldType.lexeme}' ولكن وجد '${valueType.lexeme}'.", loc, len)
                        }
                        return valueType
                    } else {
                        diagnostics.reportError("البنية '${objType.lexeme}' لا تحتوي على حقل باسم '$propertyName'.", expr.name.location, expr.name.lexeme.length)
                    }
                } else if (objType != SakhrType.UNKNOWN) {
                    diagnostics.reportError("النوع '${objType.lexeme}' لا يحتوي على حقل باسم '$propertyName'.", expr.name.location, expr.name.lexeme.length)
                }
                valueType
            }

            is Expr.Context -> currentFunction?.receiverType ?: SakhrType.UNKNOWN
            is Expr.Grouping -> checkExpr(expr.expression)
            is Expr.Lambda -> {
                val paramTypes = expr.params.map { resolveAndMarkType(it.type) ?: SakhrType.UNKNOWN }
                val enclosingFunction = currentFunction
                // Create a temporary signature for the lambda so Return works
                val tempSig = FunctionSignature(
                    name = "دالة_مجهولة",
                    params = paramTypes.toMutableList(),
                    returnType = SakhrType.UNKNOWN,
                    kind = FunctionKind.FUNCTION,
                    location = expr.location
                )
                currentFunction = tempSig
                
                beginScope()
                expr.params.forEachIndexed { i, param ->
                    declare(param.name, paramTypes[i], isConstant = true, isParameter = true)
                    define(param.name)
                }
                val returnType = when (val body = expr.body) {
                    is LambdaBody.Expression -> checkExpr(body.expr)
                    is LambdaBody.Block -> {
                        body.statements.statements.forEach { checkStmt(it) }
                        SakhrType.UNKNOWN // Inference for multi-statement lambda block is complex
                    }
                }
                endScope()
                currentFunction = enclosingFunction
                SakhrType("دالة", null, false, paramTypes, returnType)
            }
        }
        typeAtLocation[getExprLocation(expr)] = type
        return type
    }

    private fun resolveAndCapture(name: String, positional: List<SakhrType>, named: Map<String, SakhrType>): FunctionSignature? {
        val sigs = mutableListOf<FunctionSignature>()
        for (i in scopes.size - 1 downTo 0) {
            scopes[i].functions[name]?.let { sigs.addAll(it) }
        }
        val sig = sigs.find { sig ->
            val totalArgs = positional.size + named.size
            if (totalArgs > sig.params.size) return@find false
            if (positional.size > sig.params.size) return@find false
            for (i in positional.indices) {
                if (!isAssignable(sig.params[i], positional[i])) return@find false
            }
            true
        }
        sig?.isUsed = true
        return sig
    }

    private fun validateStructCall(struct: StructInfo, positional: List<SakhrType>, named: Map<String, SakhrType>, location: Location, length: Int = 1): SakhrType {
        val assigned = mutableSetOf<String>()
        
        if (positional.size > struct.fieldNames.size) {
            diagnostics.reportError("عدد الوسائط الممررة أكثر من عدد حقول البنية '${struct.name}'.", location, length)
        }
        
        for (i in positional.indices) {
            if (i < struct.fieldNames.size) {
                val fieldName = struct.fieldNames[i]
                val fieldType = struct.fields[fieldName]!!
                if (!isAssignable(fieldType, positional[i])) {
                    diagnostics.reportError("نوع الوسيط رقم ${i + 1} لا يتطابق مع نوع الحقل '$fieldName' (${fieldType.lexeme}).", location, length)
                }
                assigned.add(fieldName)
            }
        }
        
        for ((name, type) in named) {
            if (!struct.fields.containsKey(name)) {
                diagnostics.reportError("البنية '${struct.name}' لا تملك حقلاً باسم '$name'.", location, length)
                continue
            }
            if (assigned.contains(name)) {
                diagnostics.reportError("تم تعيين الحقل '$name' بالفعل بواسطة وسيط موضعي.", location, length)
            }
            val fieldType = struct.fields[name]!!
            if (!isAssignable(fieldType, type)) {
                diagnostics.reportError("نوع الحقل '$name' لا يتطابق مع القيمة الممررة (${fieldType.lexeme}).", location, length)
            }
            assigned.add(name)
        }
        
        for (required in struct.requiredFields) {
            if (!assigned.contains(required)) {
                diagnostics.reportError("الحقل المطلوب '$required' لم يتم تعيينه في منشئ البنية '${struct.name}'.", location, length)
            }
        }

        return SakhrType(struct.name)
    }

    private fun returnsOnAllPaths(statements: List<Stmt>): Boolean {
        for (stmt in statements) {
            if (returnsOnAllPaths(stmt)) return true
        }
        return false
    }

    private fun returnsOnAllPaths(stmt: Stmt): Boolean {
        return when (stmt) {
            is Stmt.Return, is Stmt.Raise -> true
            is Stmt.Block -> returnsOnAllPaths(stmt.statements)
            is Stmt.If -> stmt.elseBranch != null && returnsOnAllPaths(stmt.thenBranch) && returnsOnAllPaths(stmt.elseBranch)
            else -> false
        }
    }

    private fun lookupVariable(name: Token): VariableInfo? {
        for (i in scopes.size - 1 downTo 0) {
            val info = scopes[i].variables[name.lexeme]
            if (info != null) return info
        }
        return null
    }

    private fun lookupFunctions(name: String): List<FunctionSignature> {
        val sigs = mutableListOf<FunctionSignature>()
        for (i in scopes.size - 1 downTo 0) {
            scopes[i].functions[name]?.let { sigs.addAll(it) }
        }
        return sigs
    }

    private fun visibleCallableNames(): Set<String> {
        val names = mutableSetOf<String>()
        for (scope in scopes) {
            names.addAll(scope.functions.keys.filter { !it.contains("::") })
            names.addAll(scope.structs.keys)
        }
        return names
    }

    private fun lookupStruct(name: String, markUsed: Boolean = true): StructInfo? {
        for (i in scopes.size - 1 downTo 0) {
            val info = scopes[i].structs[name]
            if (info != null) {
                if (markUsed) info.isUsed = true
                return info
            }
        }
        return null
    }

    private fun markTypeUsed(type: SakhrType) {
        lookupStruct(type.lexeme)
        type.elementType?.let { markTypeUsed(it) }
        type.parameterTypes?.forEach { markTypeUsed(it) }
        type.returnType?.let { markTypeUsed(it) }
    }

    private fun resolveAndMarkType(token: Token?): SakhrType? {
        if (token == null) return null
        val type = SakhrType.fromLexeme(token.lexeme)
        markTypeUsed(type)
        return type
    }

    private fun declare(name: Token, type: SakhrType, isConstant: Boolean, isParameter: Boolean = false, fixOffset: Int = 0, fixLength: Int? = null) {
        if (scopes.isEmpty()) return
        val scope = scopes.last()
        if (scope.variables.containsKey(name.lexeme)) {
            diagnostics.reportError("الاسم '${name.lexeme}' معرف مسبقاً.", name.location, name.lexeme.length)
        }
        scope.variables[name.lexeme] = VariableInfo(type, isConstant, false, name.location, isParameter = isParameter, fixOffset = fixOffset, fixLength = fixLength)
        declaredTypes[name.location] = type
    }

    private fun define(name: Token) {
        if (scopes.isEmpty()) return
        val scope = scopes.last()
        val info = scope.variables[name.lexeme]
        if (info != null) scope.variables[name.lexeme] = info.copy(isDefined = true)
    }

    private fun isAssignable(target: SakhrType, source: SakhrType): Boolean {
        if (target == SakhrType.UNKNOWN || source == SakhrType.UNKNOWN) return true
        if (source == SakhrType.NULL_LITERAL) return target.isOptional
        
        // Handle optionality
        val targetBase = if (target.isOptional) target.copy(isOptional = false) else target
        val sourceBase = if (source.isOptional) source.copy(isOptional = false) else source
        
        if (target.isOptional && !source.isOptional && isAssignable(targetBase, sourceBase)) return true
        
        if (target.lexeme != source.lexeme) return false
        
        if (target.lexeme == "قائمة") {
            if (target.elementType == null || source.elementType == null) return true
            return isAssignable(target.elementType, source.elementType)
        }
        
        if (target.isFunction && source.isFunction) {
            if (target.parameterTypes!!.size != source.parameterTypes!!.size) return false
            for (i in target.parameterTypes.indices) {
                // Parameters are contravariant, but Sakhr simplifies to invariant for now
                if (!isAssignable(target.parameterTypes[i], source.parameterTypes[i])) return false
            }
            return isAssignable(target.returnType!!, source.returnType!!)
        }
        
        return targetBase.lexeme == sourceBase.lexeme
    }

    private fun beginScope() { scopes.add(Scope()) }
    private fun endScope() {
        val lastScope = scopes.removeAt(scopes.size - 1)
        
        // Report unused variables
        for ((name, info) in lastScope.variables) {
            if (!info.isUsed && name != "السياق" && name != "_") {
                val fix = QuickFix("حذف آمن", if (info.isParameter) "SAFE_DELETE_PARAM" else "SAFE_DELETE_VAR", info.fixOffset, 0, info.fixLength)
                diagnostics.reportWarning("المتغير '$name' غير مستخدم.", info.location, name.length, listOf(fix))
            } else if (!info.isConstant && !info.isReassigned && name != "السياق" && !info.isParameter && name != "_") {
                 diagnostics.reportWarning("يفضل استخدام 'ألزم' لـ '$name'.", info.location, name.length, listOf(QuickFix("استخدام 'ألزم'", "ألزم")))
            }
        }
        
        // Report unused functions
        for (sigs in lastScope.functions.values) {
            for (sig in sigs) {
                if (!sig.isBuiltIn && !sig.isUsed && sig.name != "المطلع") {
                    val fixes = if (sig.startLocation != null && sig.endLocation != null) {
                        val startColOffset = sig.startLocation.column - sig.location.column
                        val endLineOffset = sig.endLocation.line - sig.location.line
                        val endColOffset = if (endLineOffset == 0) {
                            sig.endLocation.column - sig.startLocation.column
                        } else {
                            sig.endLocation.column
                        }
                        listOf(QuickFix("حذف آمن", "SAFE_DELETE_FUNCTION", startColOffset, endLineOffset, endColOffset))
                    } else emptyList()
                    diagnostics.reportWarning("الدالة '${sig.name}' غير مستخدمة.", sig.location, sig.name.length, fixes)
                }
            }
        }

        // Report unused structs/enums
        for ((name, struct) in lastScope.structs) {
            if (!struct.isUsed) {
                val kind = if (struct.isEnum) "التعداد" else "البنية"
                val fixes = if (struct.startLocation != null && struct.endLocation != null) {
                    val startColOffset = struct.startLocation.column - struct.location.column
                    val endLineOffset = struct.endLocation.line - struct.location.line
                    val endColOffset = if (endLineOffset == 0) {
                        struct.endLocation.column - struct.startLocation.column
                    } else {
                        struct.endLocation.column
                    }
                    listOf(QuickFix("حذف آمن", "SAFE_DELETE_STRUCT", startColOffset, endLineOffset, endColOffset))
                } else emptyList()
                diagnostics.reportWarning("$kind '$name' غير مستخدم.", struct.location, name.length, fixes)
            } else {
                // Report unused fields
                for (fieldName in struct.fieldNames) {
                    val isUsed = fieldName in struct.usedFields
                    val isRequired = !struct.isEnum && fieldName in struct.requiredFields
                    if (!isUsed && !isRequired) {
                        val kind = if (struct.isEnum) "عضو التعداد" else "الحقل"
                        val loc = struct.fieldLocations[fieldName]
                        val len = struct.fieldLengths[fieldName] ?: fieldName.length
                        if (loc != null) {
                            val fixes = listOf(QuickFix("حذف آمن", "SAFE_DELETE_FIELD", 0, 0, len))
                            diagnostics.reportWarning("$kind '$fieldName' غير مستخدم.", loc, fieldName.length, fixes)
                        }
                    }
                }
            }
        }
    }

    private fun getExprLocation(expr: Expr): Location = when (expr) {
        is Expr.Variable -> expr.name.location
        is Expr.Binary -> expr.operator.location
        is Expr.Logical -> expr.operator.location
        is Expr.Unary -> expr.operator.location
        is Expr.ListLiteral -> expr.bracket.location
        is Expr.Call -> expr.paren.location
        is Expr.Get -> expr.name.location
        is Expr.Assignment -> expr.name.location
        is Expr.Context -> expr.keyword.location
        is Expr.Grouping -> getExprLocation(expr.expression)
        is Expr.Literal -> expr.location ?: Location(0, 0)
        is Expr.Index -> expr.bracket.location
        is Expr.Set -> expr.name.location
        is Expr.Lambda -> expr.location
    }

    /** Best-effort start location + length of the source range covered by [expr],
     *  so squiggles underline the whole expression rather than a single char. */
    private fun getExprRange(expr: Expr): Pair<Location, Int> {
        val start = exprStart(expr)
        val end = exprEnd(expr)
        val length = if (end.line == start.line && end.column > start.column) end.column - start.column else 1
        return start to length
    }

    private fun exprStart(expr: Expr): Location = when (expr) {
        is Expr.Binary -> exprStart(expr.left)
        is Expr.Logical -> exprStart(expr.left)
        is Expr.Unary -> expr.operator.location
        is Expr.Grouping -> exprStart(expr.expression)
        is Expr.Literal -> expr.location ?: Location(0, 0)
        is Expr.ListLiteral -> expr.bracket.location
        is Expr.Variable -> expr.name.location
        is Expr.Call -> exprStart(expr.callee)
        is Expr.Get -> exprStart(expr.obj)
        is Expr.Index -> exprStart(expr.obj)
        is Expr.Set -> exprStart(expr.obj)
        is Expr.Assignment -> expr.name.location
        is Expr.Context -> expr.keyword.location
        is Expr.Lambda -> expr.location
    }

    private fun exprEnd(expr: Expr): Location = when (expr) {
        is Expr.Binary -> exprEnd(expr.right)
        is Expr.Logical -> exprEnd(expr.right)
        is Expr.Unary -> exprEnd(expr.right)
        is Expr.Grouping -> exprEnd(expr.expression)
        is Expr.Literal -> expr.location?.let { Location(it.line, it.column + literalLength(expr.value)) } ?: Location(0, 0)
        is Expr.ListLiteral ->
            if (expr.elements.isEmpty()) Location(expr.bracket.location.line, expr.bracket.location.column + 2)
            else exprEnd(expr.elements.last()).let { Location(it.line, it.column + 1) } // closing ']'
        is Expr.Variable -> tokenEnd(expr.name)
        is Expr.Call -> tokenEnd(expr.paren) // paren is the closing ')'
        is Expr.Get -> tokenEnd(expr.name)
        is Expr.Index -> exprEnd(expr.index).let { Location(it.line, it.column + 1) } // closing ']'
        is Expr.Set -> exprEnd(expr.value)
        is Expr.Assignment -> exprEnd(expr.value)
        is Expr.Context -> tokenEnd(expr.keyword)
        is Expr.Lambda -> when (val body = expr.body) {
            is LambdaBody.Expression -> exprEnd(body.expr)
            is LambdaBody.Block -> body.statements.endToken?.location ?: expr.location
        }
    }

    private fun lastStmtStart(stmt: Stmt): Location = when (stmt) {
        is Stmt.Block -> stmt.endToken?.location ?: Location(0, 0)
        is Stmt.Expression -> exprEnd(stmt.expression)
        is Stmt.Function -> stmt.endToken.location
        is Stmt.If -> stmt.elseBranch?.let { lastStmtStart(it) } ?: lastStmtStart(stmt.thenBranch)
        is Stmt.While -> lastStmtStart(stmt.body)
        is Stmt.ForEach -> lastStmtStart(stmt.body)
        is Stmt.Break -> tokenEnd(stmt.keyword)
        is Stmt.Continue -> tokenEnd(stmt.keyword)
        is Stmt.Let -> tokenEnd(stmt.endToken ?: stmt.names.last())
        is Stmt.Const -> tokenEnd(stmt.endToken ?: stmt.names.last())
        is Stmt.Return -> stmt.value?.let { exprEnd(it) } ?: tokenEnd(stmt.keyword)
        is Stmt.Raise -> exprEnd(stmt.message)
        is Stmt.Struct -> stmt.endToken.location
        is Stmt.Enum -> stmt.endToken.location
        is Stmt.Match -> stmt.endToken.location
        is Stmt.Import -> tokenEnd(stmt.path.last())
    }

    private fun tokenEnd(token: Token): Location =
        Location(token.location.line, token.location.column + token.lexeme.length)

    private fun literalLength(value: Any?): Int = when (value) {
        is String -> value.length + 2 // opening + closing quote
        is Double -> if (value == value.toLong().toDouble()) value.toLong().toString().length else value.toString().length
        is Boolean -> if (value) 2 else 3 // 'صح' / 'خطأ'
        SakhrUnit -> 3 // 'عدم'
        null -> 4 // 'فارغ'
        else -> 1
    }
}
