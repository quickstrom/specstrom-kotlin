package io.quickstrom.specstrom


sealed class Expr : Positioned() {
    data class BoolLit(val value: Boolean) : Expr()
    data class IntLit(val value: Int) : Expr()
    data class ListLit(val elements: List<Expr>) : Expr()
    data class RecordLit(val elements: List<Pair<String,Expr>>) : Expr()
    data class ConLit(val name: String, val elements: List<Expr>) : Expr()
    data class App(val left: Expr, val right: Expr) : Expr()
    data class Lambda(val binding: Pattern, val body: Expr) : Expr()
    data class Var(val name: String) : Expr()
}
