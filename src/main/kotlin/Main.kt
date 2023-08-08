package io.quickstrom.specstrom
import io.quickstrom.specstrom.io.quickstrom.specstrom.Decl
import java.io.File
import java.io.FileReader

fun main(args: Array<String>) {
    val lex = Lexer("test.strom",FileReader(File("test.strom")))
    val p = Parser(lex)
    var syntaxMap = mutableMapOf(
        0 to listOf (
            Parser.SyntaxRule("_+_",Parser.Assoc.Left),
            Parser.SyntaxRule("_-_",Parser.Assoc.Left)
        )
    );
    val primops : MutableMap<String,Value> = mutableMapOf(
        "_+_" to Value.ValOp(Value.ValOpType.PLUS, listOf()),
        "_-_" to Value.ValOp(Value.ValOpType.MINUS, listOf()),
    )
    var env = Scope(primops,null);
    var thunkCache : MutableList<Value?> = mutableListOf();
    while (true) {
        val dec = p.parseDecl(syntaxMap.toSortedMap())
        when (dec) {
            is Decl.Syntax -> {
                syntaxMap.merge(dec.prec,listOf(Parser.SyntaxRule(dec.name,dec.assoc))) { a, b -> a + b }
            }
            is Decl.Let -> {
                when (dec.binding) {
                    is Pattern.LazyPattern -> {
                        env.bindings.put(dec.binding.name,
                                Value.Thunk(env, dec.body, thunkCache.size,
                                    Value.BindingData(dec.binding.name,dec.position)))
                        thunkCache.add(null);
                    }
                    is Pattern.StrictPattern -> {
                        val eval = Eval(dec.body,env, thunkCache)
                        eval.execute()
                        when (eval.mode) {
                            Eval.Mode.RETURN -> {
                                match(env.bindings,dec.binding, eval.value!!)
                                eval.value!!.print()
                                println()
                            }
                            Eval.Mode.ERROR -> {
                                println(eval.error)
                            }
                            Eval.Mode.EVAL -> {
                                TODO()
                            }
                        }

                    }
                }

            }
            is Decl.Function -> {}
            is Decl.Action -> {}
            is Decl.Exec -> {
                val eval = Eval(dec.body,env, thunkCache)
                eval.execute()
                when (eval.mode) {
                    Eval.Mode.RETURN -> {
                        eval.value!!.print()
                        println()
                    }
                    Eval.Mode.ERROR -> {
                        println(eval.error)
                    }
                    Eval.Mode.EVAL -> {
                        TODO()
                    }
                }
            }
            else -> {}
        }
    }

}