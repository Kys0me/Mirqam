package rtlide.lang.sakhr

import rtlide.lang.analysis.DiagnosticCollector
import rtlide.lang.analysis.Location
import rtlide.lang.analysis.QuickFix

class TypeChecker(private val diagnostics: DiagnosticCollector) {
    private val scopes = mutableListOf<Scope>()
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
        val location: Location
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

    fun check(statements: List<Stmt>) {
        collectSignatures(statements)

        for (stmt in statements) {
            checkStmt(stmt)
        }
        
        endScope() // Process global scope
    }

    private fun collectSignatures(statements: List<Stmt>) {
        for (stmt in statements) {
            when (stmt) {
                is Stmt.Function -> {
                    val kind = if (stmt.receiverType != null) FunctionKind.EXTENSION else FunctionKind.FUNCTION
                    val receiverType = stmt.receiverType?.let { SakhrType.fromLexeme(it.lexeme) }
                    
                    val params = stmt.params.map { p ->
                        if (p.type == null) {
                            if (stmt.name.lexeme == "المطلع") SakhrType.LIST else SakhrType.UNKNOWN
                        } else {
                            SakhrType.fromLexeme(p.type.lexeme)
                        }
                    }.toMutableList()
                    
                    val returnType = stmt.returnType?.let { SakhrType.fromLexeme(it.lexeme) } ?: SakhrType.VOID
                    
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
                    for (field in stmt.fields) {
                        val type = field.type?.let { SakhrType.fromLexeme(it.lexeme) } ?: SakhrType.UNKNOWN
                        fieldMap[field.name.lexeme] = type
                        fieldNames.add(field.name.lexeme)
                        if (field.initializer == null) {
                            requiredFields.add(field.name.lexeme)
                        }
                    }
                    val info = StructInfo(stmt.name.lexeme, fieldMap, fieldNames, requiredFields, stmt.name.location)
                    scopes.last().structs[stmt.name.lexeme] = info
                    allStructFields[stmt.name.lexeme] = fieldNames
                }
                else -> {}
            }
        }
    }

    private fun checkStmt(stmt: Stmt) {
        when (stmt) {
            is Stmt.Block -> {
                beginScope()
                collectSignatures(stmt.statements)
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
                
                val initialParams = stmt.params.map { p ->
                    if (p.type == null) {
                        if (stmt.name.lexeme == "المطلع") SakhrType.LIST else SakhrType.UNKNOWN
                    } else {
                        SakhrType.fromLexeme(p.type.lexeme)
                    }
                }
                val returnType = stmt.returnType?.let { SakhrType.fromLexeme(it.lexeme) } ?: SakhrType.VOID
                
                val sig = scopes.last().functions[key]?.find { it.params == initialParams && it.returnType == returnType } ?: return 

                val enclosingFunction = currentFunction
                currentFunction = sig

                beginScope()
                if (sig.kind == FunctionKind.EXTENSION) {
                    scopes.last().variables["السياق"] = VariableInfo(sig.receiverType!!, isConstant = true, isDefined = true, location = stmt.name.location, isUsed = true)
                }

                for (i in stmt.params.indices) {
                    val param = stmt.params[i]
                    val paramType = sig.params[i]
                    declare(param.name, paramType, isConstant = true, isParameter = true)
                    define(param.name)
                    
                    if (param.type == null && paramType != SakhrType.UNKNOWN) {
                         diagnostics.reportInformation("الوسيط '${param.name.lexeme}' لديه نوع ضمني '${paramType.lexeme}'.", param.name.location, param.name.lexeme.length, listOf(QuickFix("استخدام نوع صريح: ${paramType.lexeme}", "ADD_TYPE:${paramType.lexeme}")))
                    }
                }
                
                if (stmt.returnType == null && sig.returnType != SakhrType.VOID) {
                     diagnostics.reportInformation("الدالة '${stmt.name.lexeme}' لديها نوع إرجاع ضمني '${sig.returnType.lexeme}'.", stmt.name.location, stmt.name.lexeme.length, listOf(QuickFix("استخدام نوع صريح: ${sig.returnType.lexeme}", "ADD_RETURN_TYPE:${sig.returnType.lexeme}")))
                }

                stmt.body.forEach { checkStmt(it) }

                if (sig.returnType != SakhrType.VOID && !returnsOnAllPaths(stmt.body)) {
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
                checkStmt(stmt.thenBranch)
                stmt.elseBranch?.let { checkStmt(it) }
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

            is Stmt.Break -> if (loopDepth == 0) diagnostics.reportError("لا يمكن استخدام 'اكفف' خارج حلقة.", stmt.keyword.location, stmt.keyword.lexeme.length)
            is Stmt.Continue -> if (loopDepth == 0) diagnostics.reportError("لا يمكن استخدام 'امض' خارج حلقة.", stmt.keyword.location, stmt.keyword.lexeme.length)

            is Stmt.Let -> {
                val explicitType = stmt.type?.let { SakhrType.fromLexeme(it.lexeme) }
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
                val explicitType = stmt.type?.let { SakhrType.fromLexeme(it.lexeme) }
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
                    val (loc, len) = if (stmt.value != null) getExprRange(stmt.value) else stmt.keyword.location to stmt.keyword.lexeme.length
                    diagnostics.reportError("نوع الراجع لا يتطابق مع نوع إرجاع الدالة.", loc, len)
                }
            }
            is Stmt.Raise -> { checkExpr(stmt.message) }
            is Stmt.Struct -> {
                val struct = lookupStruct(stmt.name.lexeme) ?: return
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
        }
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

                    val sig = resolveAndCapture(name, positional, named)
                    if (sig != null) return sig.returnType

                    // No matching callable: report at the callee name, like IntelliJ.
                    val varInfo = lookupVariable(expr.callee.name)
                    if (varInfo != null) {
                        varInfo.isUsed = true
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

    private fun lookupStruct(name: String): StructInfo? {
        for (i in scopes.size - 1 downTo 0) {
            val info = scopes[i].structs[name]
            if (info != null) return info
        }
        return null
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
        if (target.isOptional && !source.isOptional && target.lexeme == source.lexeme) return true
        if (target.lexeme != source.lexeme) return false
        if (target.lexeme == "قائمة") {
            if (target.elementType == null || source.elementType == null) return true
            return isAssignable(target.elementType, source.elementType)
        }
        return true
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
                    diagnostics.reportWarning("الدالة '${sig.name}' غير مستخدمة.", sig.location, sig.name.length)
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
