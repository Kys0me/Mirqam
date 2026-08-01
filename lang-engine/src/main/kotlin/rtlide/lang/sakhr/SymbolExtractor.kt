package rtlide.lang.sakhr

class SymbolExtractor {
    fun extract(statements: List<Stmt>): List<String> {
        val symbols = mutableSetOf<String>()
        statements.forEach { extractFromStmt(it, symbols) }
        return symbols.toList()
    }

    private fun extractFromStmt(stmt: Stmt, symbols: MutableSet<String>) {
        when (stmt) {
            is Stmt.Block -> stmt.statements.forEach { extractFromStmt(it, symbols) }
            is Stmt.Expression -> extractFromExpr(stmt.expression, symbols)
            is Stmt.Function -> {
                symbols.add(stmt.name.lexeme)
                stmt.params.forEach { 
                    symbols.add(it.name.lexeme)
                    it.defaultValue?.let { d -> extractFromExpr(d, symbols) }
                }
                stmt.body.forEach { extractFromStmt(it, symbols) }
            }
            is Stmt.If -> {
                extractFromExpr(stmt.condition, symbols)
                extractFromStmt(stmt.thenBranch, symbols)
                stmt.elseBranch?.let { extractFromStmt(it, symbols) }
            }
            is Stmt.While -> {
                extractFromExpr(stmt.condition, symbols)
                extractFromStmt(stmt.body, symbols)
            }
            is Stmt.ForEach -> {
                stmt.indexVar?.let { symbols.add(it.lexeme) }
                symbols.add(stmt.elementVar.lexeme)
                extractFromExpr(stmt.iterable, symbols)
                extractFromStmt(stmt.body, symbols)
            }
            is Stmt.Break, is Stmt.Continue -> { /* no symbols */ }
            is Stmt.Let -> {
                stmt.names.forEach { symbols.add(it.lexeme) }
                stmt.initializer?.let { extractFromExpr(it, symbols) }
            }
            is Stmt.Const -> {
                stmt.names.forEach { symbols.add(it.lexeme) }
                extractFromExpr(stmt.initializer, symbols)
            }
            is Stmt.Return -> stmt.value?.let { extractFromExpr(it, symbols) }
            is Stmt.Raise -> extractFromExpr(stmt.message, symbols)
            is Stmt.Struct -> {
                symbols.add(stmt.name.lexeme)
                stmt.fields.forEach { field ->
                    symbols.add(field.name.lexeme)
                    field.initializer?.let { i -> extractFromExpr(i, symbols) }
                }
            }
            is Stmt.Enum -> {
                symbols.add(stmt.name.lexeme)
                stmt.members.forEach { symbols.add(it.lexeme) }
            }
            is Stmt.Match -> {
                extractFromExpr(stmt.expression, symbols)
                stmt.cases.forEach { case ->
                    extractFromExpr(case.pattern, symbols)
                    extractFromStmt(case.body, symbols)
                }
                stmt.defaultBranch?.let { extractFromStmt(it, symbols) }
            }
            is Stmt.Import -> {
                stmt.path.forEach { symbols.add(it.lexeme) }
            }
        }
    }

    private fun extractFromExpr(expr: Expr, symbols: MutableSet<String>) {
        when (expr) {
            is Expr.Binary -> {
                extractFromExpr(expr.left, symbols)
                extractFromExpr(expr.right, symbols)
            }
            is Expr.Logical -> {
                extractFromExpr(expr.left, symbols)
                extractFromExpr(expr.right, symbols)
            }
            is Expr.Unary -> {
                extractFromExpr(expr.right, symbols)
            }
            is Expr.Grouping -> extractFromExpr(expr.expression, symbols)
            is Expr.Literal -> { /* no symbols in literals */ }
            is Expr.ListLiteral -> {
                expr.elements.forEach { extractFromExpr(it, symbols) }
            }
            is Expr.Variable -> symbols.add(expr.name.lexeme)
            is Expr.Call -> {
                extractFromExpr(expr.callee, symbols)
                expr.arguments.forEach { extractFromExpr(it, symbols) }
            }
            is Expr.Get -> {
                extractFromExpr(expr.obj, symbols)
                symbols.add(expr.name.lexeme)
            }
            is Expr.Context -> { /* context is a keyword */ }
            is Expr.Assignment -> {
                symbols.add(expr.name.lexeme)
                extractFromExpr(expr.value, symbols)
            }
            is Expr.Index -> {
                extractFromExpr(expr.obj, symbols)
                extractFromExpr(expr.index, symbols)
            }
            is Expr.Set -> {
                extractFromExpr(expr.obj, symbols)
                symbols.add(expr.name.lexeme)
                extractFromExpr(expr.value, symbols)
            }
            is Expr.Lambda -> {
                expr.params.forEach { 
                    symbols.add(it.name.lexeme)
                    it.defaultValue?.let { d -> extractFromExpr(d, symbols) }
                }
                when (val body = expr.body) {
                    is LambdaBody.Expression -> extractFromExpr(body.expr, symbols)
                    is LambdaBody.Block -> body.statements.statements.forEach { extractFromStmt(it, symbols) }
                }
            }
        }
    }
}
