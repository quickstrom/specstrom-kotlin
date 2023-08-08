package io.quickstrom.specstrom

sealed interface Value {
    data class BindingData(val name : String, val position: Positioned.Position?)

    enum class ValOpType {
        PLUS, MINUS, TIMES, DIVIDE, MOD, LESS, GREATER, LESSEQ, GREATEREQ, EQ, NEQ, NOT
    }

    data class ValOp(val primop : ValOpType, val args: List<Value>) : Value {
        override fun print() {
            print(primop)
        }
        fun applyTo(value : Value) : Value {
            if (args.isEmpty()) {
                return when (primop) {
                    ValOpType.NOT -> if (value is BoolVal) BoolVal(!value.value) else Null
                    else -> ValOp(primop, listOf(value))
                }
            } else {
                val c0 = args[0]
                if (primop == ValOpType.PLUS && c0 is IntVal && value is IntVal) {
                    return IntVal(c0.value + value.value)
                } else if (primop == ValOpType.MINUS && c0 is IntVal && value is IntVal)  {
                    return IntVal(c0.value - value.value)
                } else {
                    return Null
                }
            }
        }
    }


    @JvmInline value class IntVal(val value: Int) : Value {
        override fun print() {
            print(value)
        }
    }

    @JvmInline value class BoolVal(val value: Boolean) : Value {
        override fun print() {
            print(value)
        }
    }

    @JvmInline value class ListVal(val elements: List<Value>) : Value {
        override fun print() {
            print("[")
            var n = elements.size
            for (e in elements) {
                e.print()
                n -= 1
                if (n == 0) {
                    print("]")
                } else {
                    print(", ")
                }
            }
        }
    }

    @JvmInline value class RecordVal(val elements: Map<String, Value>) : Value {
        override fun print() {
            print("{")
            var n = elements.size
            for (e in elements) {
                print(e.key)
                print(": ")
                e.value.print()
                n -= 1
                if (n == 0) {
                    print("}")
                } else {
                    print(", ")
                }
            }
        }
    }
    data class Con(val name: String, val elements: List<Value>) : Value {
        override fun print() {
            print(":")
            print(name)
            print("(")
            var n = elements.size
            for (e in elements) {
                e.print()
                n -= 1
                if (n == 0) {
                    print(")")
                } else {
                    print(", ")
                }
            }
        }
    }
    data class Closure(val scope: Scope, val binding: Pattern, val body: Expr) : Value {
        var metadata : BindingData? = null
        override fun print() {
            print("<closure>")
        }

    }
    data class Thunk(val scope: Scope, val body: Expr, val cacheIndex: Int, var metadata : BindingData) : Value {
        override fun print() {
            print("<thunk>")
        }
    }
    object Null : Value {
        override fun print() {
            print("null")
        }
    }

    fun print()
}
