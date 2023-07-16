package io.quickstrom.specstrom
import org.nineml.coffeegrinder.parser.GearleyResult
import org.nineml.coffeegrinder.parser.NonterminalSymbol
import org.nineml.coffeegrinder.parser.SourceGrammar
import org.nineml.coffeegrinder.parser.TerminalSymbol
import org.nineml.coffeegrinder.trees.ParseTree
import java.util.*

class Parser(syntax : SortedMap<Int,List<SyntaxRule>>) {
    sealed interface SyntaxItem {

        fun show() : String

        object Lower : SyntaxItem {
            override fun show(): String {
                return "_"
            }
        }
        object Same : SyntaxItem {
            override fun show(): String {
                return "_"
            }
        }
        object Any : SyntaxItem {
            override fun show(): String {
                return "_"
            }
        }
        @JvmInline value class Ident(val name : String) : SyntaxItem {
            override fun show(): String {
                return name
            }
        }
    }
    enum class Assoc {
        Left, Right, None
    }
    class SyntaxRule(spec : String, assoc : Assoc) {
        val bits : List<SyntaxItem>
        init {
            val strings = spec.split("_")
            val strings2 = List(strings.size) { index -> listOf(strings[index], "_")}
                .flatten().dropLast(1).filter { !it.isEmpty() }
            bits = strings2.mapIndexed { index, str ->
                if (str == "_")
                    if (index == 0 && assoc == Assoc.Left) SyntaxItem.Same
                    else if (index == 0) SyntaxItem.Lower
                    else if (index == strings2.lastIndex && assoc == Assoc.Right) SyntaxItem.Same
                    else if (index == strings2.lastIndex) SyntaxItem.Lower
                    else SyntaxItem.Any
                 else
                    SyntaxItem.Ident(str)
            }
        }
    }
    val grammar = SourceGrammar()
    val anyExpr: NonterminalSymbol = grammar.getNonterminal("expression")
    val basicExpr: NonterminalSymbol = grammar.getNonterminal("basic expression")
    val exprList: NonterminalSymbol = grammar.getNonterminal("sequence of expressions")
    val fieldList: NonterminalSymbol = grammar.getNonterminal("sequence of field assignments")
    val identifier = TerminalSymbol(Tok.TokSelector.AnyIdent)
    val fieldName = TerminalSymbol(Tok.TokSelector.FieldName)
    val integer = TerminalSymbol(Tok.TokSelector.AnyInteger)
    val lParen = TerminalSymbol(Tok.LParen)
    val rParen = TerminalSymbol(Tok.RParen)
    val funKwd = TerminalSymbol(Tok.Ident("fun"))
    val comma = TerminalSymbol(Tok.Ident(","))
    val lBrace = TerminalSymbol(Tok.Ident("{"))
    val rBrace = TerminalSymbol(Tok.Ident("}"))
    val lBracket = TerminalSymbol(Tok.Ident("["))
    val rBracket = TerminalSymbol(Tok.Ident("]"))
    val colon = TerminalSymbol(Tok.Ident(":"))

    init {
        grammar.addRule(exprList,)
        grammar.addRule(exprList,anyExpr)
        grammar.addRule(exprList,anyExpr,comma, exprList)
        grammar.addRule(fieldList,)
        grammar.addRule(fieldList,fieldName, colon, anyExpr)
        grammar.addRule(fieldList,fieldName, colon, anyExpr,comma, fieldList)
        grammar.addRule(basicExpr,identifier)
        grammar.addRule(basicExpr,integer)
        grammar.addRule(basicExpr,colon,identifier)
        grammar.addRule(basicExpr,lBracket,exprList,rBracket)
        grammar.addRule(basicExpr,lBrace,fieldList,rBrace)
        grammar.addRule(basicExpr,basicExpr,lParen, exprList, rParen)
        grammar.addRule(basicExpr,funKwd,lParen,exprList,rParen,lBrace,anyExpr, rBrace)
        var lowerNonterminal = basicExpr
        for ((k,rules) in syntax) {
            val currentNonterminal = grammar.getNonterminal("(" + k.toString() + ")")
            grammar.addRule(currentNonterminal, lowerNonterminal)
            for (r in rules) {
                grammar.addRule(currentNonterminal,r.bits.map {
                    when (it) {
                        is SyntaxItem.Ident -> TerminalSymbol(Tok.Ident(it.name))
                        is SyntaxItem.Any -> anyExpr
                        is SyntaxItem.Lower -> lowerNonterminal
                        is SyntaxItem.Same -> currentNonterminal
                    }
                })
            }
            lowerNonterminal = currentNonterminal
        }
        grammar.addRule(anyExpr, lowerNonterminal)
    }
    fun fromAnyExpr(pt : ParseTree) : Expr {
        var cur = pt
        while (cur.children.size == 1 && cur.symbol != basicExpr) {
            cur = cur.children[0]
        }
        if (cur.symbol == basicExpr) {
            return fromBasicExpr(cur)
        }
        val name = StringBuilder()
        val args : MutableList<Expr> = mutableListOf()
        for (c in cur.children) {
            if (c.token == null) {
                name.append('_')
                args.add(fromAnyExpr(c))
            } else {
                name.append(c.token.value)
            }
        }
        var func : Expr = Expr.Var(name.toString())
        for (arg in args) {
            func = Expr.App(func, arg)
        }
        return func

    }
    fun fromExprList(pt : ParseTree) : List<Expr> {
        var cur : ParseTree? = pt
        val list = mutableListOf<Expr>()
        while (cur?.symbol == exprList) {
            if (!cur.children.isEmpty()) {
                list.add(fromAnyExpr(cur.children[0]))
                cur = if (cur.children.size == 3) cur.children[2] else null
            } else break
        }
        return list
    }
    fun fromFieldList(pt : ParseTree) : List<Pair<String,Expr>> {
        var cur : ParseTree? = pt
        val list = mutableListOf<Pair<String,Expr>>()
        while (cur?.symbol == fieldList) {
            if (!cur.children.isEmpty()) {
                val p = Pair(cur.children[0].token.value,fromAnyExpr(cur.children[2]));
                list.add(p)
                cur = if (cur.children.size == 5) cur.children[4] else null
            } else break;
        }
        return list
    }
    fun fromBasicExpr(pt : ParseTree) : Expr {
        when (pt.children.size) {
            1 -> { // literal
                return when (val v = pt.children[0].token) {
                    is Tok.IntLit -> Expr.IntLit(v.value)
                    else -> Expr.Var(pt.children[0].token.value)
                }
            }
            2 -> { //constructor literal
                return Expr.ConLit(pt.children[1].token.value, listOf())
            }
            3 -> { // list or record literal
                return if (pt.children[1].symbol == exprList) {
                    Expr.ListLit(fromExprList(pt.children[1]))
                } else {
                    Expr.RecordLit(fromFieldList(pt.children[1]))
                }
            }
            4 -> { // function application
                var func = fromBasicExpr(pt.children[0])
                val args = fromExprList(pt.children[2])
                if (func is Expr.ConLit) {
                    func = Expr.ConLit(func.name,func.elements + args)
                } else {
                    for (arg in args) {
                        func = Expr.App(func, arg)
                    }
                }
                return func
            }
            7 -> { // lambda abstraction
                val args = fromExprList(pt.children[2])
                var body = fromAnyExpr(pt.children[5])
                for (arg in args) {
                    body = Expr.Lambda(exprToStrictPattern(arg),body)
                }
                return body
            }
            else -> throw Exception()
        }
    }
    fun exprToStrictPattern(exp : Expr) : Pattern.StrictPattern {
        return when (exp) {
            is Expr.Var -> Pattern.Var(exp.name)
            is Expr.App -> TODO()
            is Expr.BoolLit -> Pattern.BoolLit(exp.value)
            is Expr.ConLit -> Pattern.Con(exp.name,exp.elements.map(::exprToStrictPattern))
            is Expr.IntLit -> Pattern.IntLit(exp.value)
            is Expr.Lambda -> TODO()
            is Expr.ListLit -> Pattern.ListLit(exp.elements.map(::exprToStrictPattern))
            is Expr.RecordLit -> Pattern.Record(buildMap {
                for ((x,y) in exp.elements) {
                    put(x,exprToStrictPattern(y))
                }
            })
        }
    }
    fun fn(lex : Lexer) : Expr {
        println(grammar.rules.toString())
        val opts = grammar.parserOptions
        opts.prefixParsing = true
        val parser = grammar.getParser(opts,"expression")
        val result : GearleyResult = parser.parse(lex)
        return fromAnyExpr(result.tree)
    }
}