package io.quickstrom.specstrom


class Eval(e : Expr) {
    enum class Mode {
        EVAL,
        RETURN,
        ERROR
    }
    enum class Error {
        VariableNotFound,
        NotAFunction,
        Unknown
    }
    var expr: Expr? = e
    var error: Error? = null
    var value: Value? = null
    var mode: Mode = Mode.EVAL
    val stack: ArrayDeque<Frame> = ArrayDeque()
    var env: Scope = Scope(mutableMapOf(),null)
    val thunkCache: MutableList<Value?> = mutableListOf()
    private fun match(to : MutableMap<String,Value>, pat : Pattern.StrictPattern, value : Value) : Boolean {
        when (pat) {
            is Pattern.Var -> {
                return if (to.containsKey(pat.name)) {
                    throw Exception()
                } else {
                    to[pat.name] = value
                    if (value is Value.Closure) {
                        value.metadata = Value.BindingData(pat.name,pat.pos)
                    }
                    true
                }
            }
            is Pattern.IntLit ->
                return value is Value.IntVal && value.value == pat.value
            is Pattern.BoolLit ->
                return value is Value.BoolVal && value.value == pat.value
            is Pattern.ListLit -> {
                if (value is Value.ListVal) {
                    if (pat.elements.size != value.elements.size) return false
                    for ((x,y) in pat.elements.zip(value.elements)) {
                        if (!this.match(to,x,y)) return false
                    }
                } else return false
            }
            is Pattern.Con -> {
                if (value is Value.Con && value.name == pat.conName) {
                    if (pat.elements.size != value.elements.size) return false
                    for ((x,y) in pat.elements.zip(value.elements)) {
                        if (!match(to,x,y)) return false
                    }
                } else return false
            }
            is Pattern.Record -> {
                if (value is Value.RecordVal) {
                    for ((k,v) in pat.elements) {
                        val subValue = value.elements[k] ?: return false
                        if (!match(to,v,subValue))
                            return false
                    }
                } else return false
            }
        }
        return true
    }

    fun execute() {
        while (true) when (mode) {
            Mode.ERROR -> {
                return
            }
            Mode.EVAL -> {
                when (val e : Expr? = expr) {
                    is Expr.IntLit -> {
                        mode = Mode.RETURN
                        value = Value.IntVal(e.value)
                        expr = null
                    }
                    is Expr.BoolLit -> {
                        mode = Mode.RETURN
                        value = Value.BoolVal(e.value)
                        expr = null
                    }
                    is Expr.App -> {
                        stack.addLast(Frame.AppFrameLeft(e.right))
                        expr = e.left
                    }
                    is Expr.ListLit -> {
                        val iter: ListIterator<Expr> = e.elements.listIterator()
                        if (!iter.hasNext()) {
                            mode = Mode.RETURN
                            expr = null
                            value = Value.ListVal(listOf())
                        } else {
                            val first = iter.next()
                            stack.addLast(Frame.ListFrame(iter))
                            expr = first
                        }
                    }
                    is Expr.ConLit -> {
                        val iter: ListIterator<Expr> = e.elements.listIterator()
                        if (!iter.hasNext()) {
                            mode = Mode.RETURN
                            expr = null
                            value = Value.Con(e.name,listOf())
                        } else {
                            val first = iter.next()
                            stack.addLast(Frame.ConFrame(e.name, iter))
                            expr = first
                        }
                    }
                    is Expr.RecordLit -> {
                        val iter: ListIterator<Pair<String,Expr>> = e.elements.listIterator()
                        if (!iter.hasNext()) {
                            mode = Mode.RETURN
                            expr = null
                            value = Value.RecordVal(mapOf())
                        } else {
                            val (key,keyExp) = iter.next()
                            stack.addLast(Frame.RecordFrame(key,iter))
                            expr = keyExp
                        }
                    }
                    is Expr.Lambda -> {
                        value = Value.Closure(this.env,e.binding, e.body, null)
                        expr = null
                        mode = Mode.RETURN
                    }
                    is Expr.Var -> {
                        value = env.lookup(e.name)
                        when (val v = value) {
                            is Value.Thunk -> {
                                value = if (v.cacheIndex < thunkCache.size) thunkCache[v.cacheIndex] else null
                                if (value != null) {
                                    mode = Mode.RETURN
                                    expr = null
                                } else {
                                    stack.addLast(Frame.ThunkCallFrame(this.env,v.cacheIndex,v.metadata))
                                    expr = v.body
                                    env = v.scope
                                }
                            }
                            null -> {
                                mode = Mode.ERROR
                                error = Error.VariableNotFound
                            }
                            else -> {
                                mode = Mode.RETURN
                                expr = null
                            }
                        }
                    }
                    null -> {
                        mode = Mode.ERROR
                        error = Error.Unknown // this shouldn't happen
                    }
                }
            }
            Mode.RETURN -> {
                when (val f = this.stack.lastOrNull()) {
                    null -> return
                    is Frame.FunCallFrame -> {
                        env = f.env
                        stack.removeLast()
                    }
                    is Frame.ThunkCallFrame -> {
                        env = f.env
                        thunkCache[f.index] = value!!
                        stack.removeLast()
                    }
                    is Frame.AppFrameLeft -> {
                        when (val func = this.value) {
                            is Value.Closure -> {
                                when (func.binding) {
                                    is Pattern.LazyPattern -> {
                                        stack.removeLast()
                                        val hm : MutableMap<String,Value> =
                                            mutableMapOf(func.binding.name to
                                                    Value.Thunk(env, f.right, thunkCache.size,
                                                        Value.BindingData(func.binding.name,func.binding.pos)))
                                        thunkCache.add(null)
                                        stack.addLast(Frame.FunCallFrame (env,func.metadata))
                                        env = Scope (hm, func.scope)
                                        expr = func.body
                                        mode = Mode.EVAL
                                        value = null
                                    }
                                    is Pattern.StrictPattern -> {
                                        stack.removeLast()
                                        stack.addLast(Frame.AppFrameRight(func))
                                        expr = f.right
                                        mode = Mode.EVAL
                                        value = null
                                    }
                                }
                            }
                            is Value.Null -> {
                                value = Value.Null
                                stack.removeLast()
                            }
                            else -> {
                                mode = Mode.ERROR
                                error = Error.NotAFunction
                            }
                        }
                    }
                    is Frame.AppFrameRight -> {
                        if (f.left is Value.Closure && f.left.binding is Pattern.StrictPattern) {
                            stack.removeLast()
                            val hm : MutableMap<String, Value> = mutableMapOf()
                            val success = match(hm, f.left.binding, value!!)
                            if (!success) {
                                value = Value.Null
                            } else {
                                //TODO: TCO?
                                stack.addLast(Frame.FunCallFrame(env,f.left.metadata))
                                env = Scope(hm, f.left.scope)
                                expr = f.left.body
                                value = null
                                mode = Mode.EVAL
                            }

                        } else {
                            mode = Mode.ERROR
                            error = Error.Unknown // shouldn't happen
                        }
                    }
                    is Frame.ListFrame -> {
                        expr = f.shift(value!!)
                        if (expr == null) {
                            value = Value.ListVal(f.elements)
                        } else {
                            mode = Mode.EVAL
                            value = null
                        }
                    }
                    is Frame.ConFrame -> {
                        expr = f.shift(value!!)
                        if (expr == null) {
                            value = Value.Con(f.name,f.elements)
                        } else {
                            mode = Mode.EVAL
                            value = null
                        }
                    }
                    is Frame.RecordFrame -> {
                        expr = f.shift(value!!)
                        if (expr == null) {
                            value = Value.RecordVal(f.elements)
                        } else {
                            mode = Mode.EVAL
                            value = null
                        }
                    }
                }
            }
        }
    }
}