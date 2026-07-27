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
                stmt.params.forEach { symbols.add(it.name.lexeme) }
                stmt.body.forEach { extractFromStmt(it, symbols) }
            }
            is Stmt.If -> {
                extractFromExpr(stmt.condition, symbols)
                extractFromStmt(stmt.thenBranch, symbols)
                stmt.elseBranch?.let { extractFromStmt(it, symbols) }
            }
            is Stmt.Let -> {
                symbols.add(stmt.name.lexeme)
                stmt.initializer?.let { extractFromExpr(it, symbols) }
            }
            is Stmt.Const -> {
                symbols.add(stmt.name.lexeme)
                extractFromExpr(stmt.initializer, symbols)
            }
            is Stmt.Return -> stmt.value?.let { extractFromExpr(it, symbols) }
        }
    }

    private fun extractFromExpr(expr: Expr, symbols: MutableSet<String>) {
        when (expr) {
            is Expr.Binary -> {
                extractFromExpr(expr.left, symbols)
                extractFromExpr(expr.right, symbols)
            }
            is Expr.Grouping -> extractFromExpr(expr.expression, symbols)
            is Expr.Literal -> { /* no symbols in literals */ }
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
        }
    }
}
