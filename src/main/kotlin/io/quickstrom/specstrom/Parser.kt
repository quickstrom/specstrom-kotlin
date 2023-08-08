package io.quickstrom.specstrom
import io.quickstrom.specstrom.io.quickstrom.specstrom.Decl
import org.nineml.coffeegrinder.parser.GearleyResult
import org.nineml.coffeegrinder.parser.NonterminalSymbol
import org.nineml.coffeegrinder.parser.SourceGrammar
import org.nineml.coffeegrinder.parser.TerminalSymbol
import org.nineml.coffeegrinder.trees.GenericBranch
import org.nineml.coffeegrinder.trees.GenericLeaf
import org.nineml.coffeegrinder.trees.GenericTree
import org.nineml.coffeegrinder.trees.GenericTreeBuilder
import java.util.*

class Parser(lex : Lexer) {
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
    var grammar = SourceGrammar()
    var result : GearleyResult? = null
    val lexer : Lexer = lex
    var anyExpr: NonterminalSymbol = grammar.getNonterminal("expression")
    var basicExpr: NonterminalSymbol = grammar.getNonterminal("basic expression")
    var exprList: NonterminalSymbol = grammar.getNonterminal("sequence of expressions")
    var fieldList: NonterminalSymbol = grammar.getNonterminal("sequence of field assignments")
    var decl: NonterminalSymbol = grammar.getNonterminal("declaration")
    val identifier = TerminalSymbol(Tok.TokSelector.AnyIdent)
    val fieldName = TerminalSymbol(Tok.TokSelector.FieldName)
    val integer = TerminalSymbol(Tok.TokSelector.AnyInteger)
    val lParen = TerminalSymbol(Tok.LParen)
    val rParen = TerminalSymbol(Tok.RParen)
    val funKwd = TerminalSymbol(Tok.Ident("fun"))
    val letKwd = TerminalSymbol(Tok.Ident("let"))
    val leftKwd = TerminalSymbol(Tok.Ident("left"))
    val rightKwd = TerminalSymbol(Tok.Ident("right"))
    val syntaxKwd = TerminalSymbol(Tok.Ident("syntax"))
    val execKwd = TerminalSymbol(Tok.Ident("exec"))
    val comma = TerminalSymbol(Tok.Ident(","))
    val lBrace = TerminalSymbol(Tok.Ident("{"))
    val rBrace = TerminalSymbol(Tok.Ident("}"))
    val equals = TerminalSymbol(Tok.Ident("="))
    val tilde = TerminalSymbol(Tok.Ident("~"))
    val lBracket = TerminalSymbol(Tok.Ident("["))
    val rBracket = TerminalSymbol(Tok.Ident("]"))
    val colon = TerminalSymbol(Tok.Ident(":"))
    val semi = TerminalSymbol(Tok.Ident(";"))

    fun setupGrammar(syntax : SortedMap<Int,List<SyntaxRule>>) {
        grammar = SourceGrammar()
        anyExpr = grammar.getNonterminal("expression")
        basicExpr = grammar.getNonterminal("basic expression")
        exprList = grammar.getNonterminal("sequence of expressions")
        fieldList = grammar.getNonterminal("sequence of field assignments")
        decl = grammar.getNonterminal("declaration")
        grammar.addRule(exprList,)
        grammar.addRule(exprList,anyExpr)
        grammar.addRule(exprList,anyExpr,comma, exprList)
        grammar.addRule(fieldList,)
        grammar.addRule(fieldList,fieldName, colon, anyExpr)
        grammar.addRule(fieldList,fieldName, colon, anyExpr,comma, fieldList)
        grammar.addRule(basicExpr,identifier)
        grammar.addRule(basicExpr,integer)
        grammar.addRule(basicExpr,colon,identifier)
        grammar.addRule(basicExpr,lParen,anyExpr,rParen)
        grammar.addRule(basicExpr,lBracket,exprList,rBracket)
        grammar.addRule(basicExpr,lBrace,fieldList,rBrace)
        grammar.addRule(basicExpr,basicExpr,lParen, exprList, rParen)
        grammar.addRule(basicExpr,tilde,identifier)
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
        grammar.addRule(decl,letKwd,anyExpr,equals,anyExpr, semi)
        grammar.addRule(decl,syntaxKwd,identifier,integer, semi)
        grammar.addRule(decl,syntaxKwd,identifier,integer,leftKwd, semi)
        grammar.addRule(decl,syntaxKwd,identifier,integer,rightKwd, semi)
        grammar.addRule(decl,execKwd,anyExpr, semi)
    }

    fun fromDecl(pt : GenericTree) : Decl {
        val p = pt as GenericBranch
        val kwd = p.children[0] as GenericLeaf
        when (kwd.token.value) {
            "syntax" -> {
                return Decl.Syntax((pt.children[1] as GenericLeaf).token.value,((pt.children[2] as GenericLeaf).token as Tok.IntLit).value,
                    if (pt.children.size > 3 && (pt.children[3] as GenericLeaf).token.value == "left") { Parser.Assoc.Left }
                    else if (pt.children.size > 3 && (pt.children[3] as GenericLeaf).token.value == "right") { Parser.Assoc.Right }
                    else { Parser.Assoc.None} )
            }
            "let" -> {
                return Decl.Let(exprToPattern(fromAnyExpr(p.children[1])),fromAnyExpr(p.children[3]))
            }
            "exec" -> {
                return Decl.Exec(fromAnyExpr(p.children[1]))
            }
            else -> TODO()
        }
    }
    fun fromAnyExpr(pt : GenericTree) : Expr {
        var cur = pt
        while (cur is GenericBranch && cur.children.size == 1 && cur.symbol != basicExpr) {
            cur = cur.children[0]
        }
        if (cur is GenericBranch && cur.symbol == basicExpr) {
            return fromBasicExpr(cur)
        }
        val name = StringBuilder()
        val args : MutableList<Expr> = mutableListOf()
        var pos : Positioned.Position? = null
        for (c in cur.children) {
            if (c is GenericBranch) {
                name.append('_')
                args.add(fromAnyExpr(c))
            } else if (c is GenericLeaf) {
                name.append(c.token.value)
                if (pos == null) {
                    pos = (c.token as Tok).position
                }
            }
        }
        var func : Expr = Expr.Var(name.toString()).at(pos)
        for (arg in args) {
            func = Expr.App(func, arg).at(func.position)
        }
        return func

    }
    fun fromExprList(pt : GenericTree) : List<Expr> {
        var cur : GenericTree? = pt
        val list = mutableListOf<Expr>()
        while (cur is GenericBranch && cur.symbol == exprList) {
            if (!cur.children.isEmpty()) {
                list.add(fromAnyExpr(cur.children[0]))
                cur = if (cur.children.size == 3) cur.children[2] else null
            } else break
        }
        return list
    }
    fun fromFieldList(pt : GenericTree) : List<Pair<String,Expr>> {
        var cur : GenericTree? = pt
        val list = mutableListOf<Pair<String,Expr>>()
        while (cur is GenericBranch && cur.symbol == fieldList) {
            if (!cur.children.isEmpty()) {
                val p = Pair((cur.children[0] as GenericLeaf).token.value,fromAnyExpr(cur.children[2]));
                list.add(p)
                cur = if (cur.children.size == 5) cur.children[4] else null
            } else break;
        }
        return list
    }
    fun fromBasicExpr(pt : GenericTree) : Expr {
        when (pt.children.size) {
            1 -> { // literal
                return when (val v = (pt.children[0] as GenericLeaf).token) {
                    is Tok.IntLit -> Expr.IntLit(v.value).at(v.position)
                    else -> Expr.Var(v.value).at((v as Tok).position)
                }
            }
            2 -> { //constructor literal or lazy pattern
                val c0 = pt.children[0]
                if (c0 is GenericLeaf && c0.token.value == ":") {
                    return Expr.ConLit((pt.children[1] as GenericLeaf).token.value, listOf()).at((c0.token as Tok).position)
                } else if (c0 is GenericLeaf) {
                    return Expr.App(Expr.Var("~_").at((c0.token as Tok).position),
                                    Expr.Var((pt.children[1] as GenericLeaf).token.value).at(((pt.children[1] as GenericLeaf).token as Tok).position))
                        .at((c0.token as Tok).position)
                }
            }
            3 -> { // list or record literal or paren'd expression
                val c1 = pt.children[1]
                return if (c1 is GenericBranch && c1.symbol == exprList) {
                    Expr.ListLit(fromExprList(pt.children[1])).at(((pt.children[0] as GenericLeaf).token as Tok).position)
                } else if (c1 is GenericBranch && c1.symbol == fieldList) {
                    Expr.RecordLit(fromFieldList(pt.children[1])).at(((pt.children[0] as GenericLeaf).token as Tok).position)
                } else {
                    fromAnyExpr(pt.children[1])
                }
            }
            4 -> { // function application
                var func = fromBasicExpr(pt.children[0])
                val args = fromExprList(pt.children[2])
                if (func is Expr.ConLit) {
                    func = Expr.ConLit(func.name,func.elements + args).at(func.position)
                } else {
                    for (arg in args) {
                        func = Expr.App(func, arg).at(func.position)
                    }
                }
                return func
            }
            7 -> { // lambda abstraction
                val args = fromExprList(pt.children[2])
                var body = fromAnyExpr(pt.children[5])
                for (arg in args.reversed()) {
                    body = Expr.Lambda(exprToPattern(arg),body).at(((pt.children[0] as GenericLeaf).token as Tok).position)
                }
                return body
            }
            else -> throw Exception()
        }
        throw Exception()
    }
    fun exprToPattern(exp : Expr) : Pattern {
        return if (exp is Expr.App && exp.left is Expr.Var && exp.right is Expr.Var
            && exp.left.name == "~_") {
            val ret = Pattern.LazyPattern(exp.right.name)
            ret.position = exp.left.position
            ret
        } else {
            exprToStrictPattern(exp)
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
        }.at(exp.position)
    }
    fun parseDecl(syntax : SortedMap<Int,List<SyntaxRule>>) : Decl {
        setupGrammar(syntax)
        val opts = grammar.parserOptions
        opts.prefixParsing = true
        result = if (result == null) {
            val parser = grammar.getParser(opts,"declaration")
            parser.parse(lexer)
        } else {
            result!!.continueParsing(grammar.getParser(opts, "declaration"))
        }
        val builder = GenericTreeBuilder()
        result!!.arborist.getTree(builder)
        return fromDecl(builder.tree)
    }

}