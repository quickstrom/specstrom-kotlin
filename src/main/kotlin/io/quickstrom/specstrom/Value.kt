package io.quickstrom.specstrom

sealed interface Value {
    data class BindingData(val name : String, val position: Positioned.Position?)
    @JvmInline value class IntVal(val value: Int) : Value
    @JvmInline value class BoolVal(val value: Boolean) : Value
    @JvmInline value class ListVal(val elements: List<Value>) : Value
    @JvmInline value class RecordVal(val elements: Map<String, Value>) : Value
    data class Con(val name: String, val elements: List<Value>) : Value
    data class Closure(val scope: Scope, val binding: Pattern, val body: Expr) : Value {
        var metadata : BindingData? = null
    }
    data class Thunk(val scope: Scope, val body: Expr, val cacheIndex: Int, var metadata : BindingData) : Value
    object Null : Value
}
