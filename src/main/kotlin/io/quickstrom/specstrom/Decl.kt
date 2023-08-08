package io.quickstrom.specstrom.io.quickstrom.specstrom

import io.quickstrom.specstrom.Expr
import io.quickstrom.specstrom.Parser
import io.quickstrom.specstrom.Pattern
import io.quickstrom.specstrom.Positioned

sealed class Decl : Positioned() {
    data class Function(val name: String, val bindings: List<Pattern>, val body: Expr) : Decl()
    data class Action(val name: String, val bindings: List<Pattern>, val body: Expr) : Decl()
    data class Syntax(val name: String, val prec : Int, val assoc: Parser.Assoc) : Decl()
    data class Let(val binding: Pattern, val body: Expr) : Decl()
    data class Exec(val body: Expr) : Decl()
}