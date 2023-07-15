package io.quickstrom.specstrom

sealed class Frame : Positioned() {
    class ListFrame(private val elementsToGo: ListIterator<Expr>) : Frame() {
        val elements: MutableList<Value> = mutableListOf()
        fun shift(value: Value): Expr? {
            elements.add(value)
            if (!elementsToGo.hasNext()) return null
            return elementsToGo.next()
        }
    }
    class RecordFrame(private var currentKey: String, private val elementsToGo: ListIterator<Pair<String,Expr>>) :
        Frame() {
        val elements: MutableMap<String, Value> = mutableMapOf()
        fun shift(value: Value): Expr? {
            elements[currentKey] = value
            if (!elementsToGo.hasNext()) return null
            val (key,expr) = elementsToGo.next()
            currentKey = key
            return expr
        }
    }
    class ConFrame(val name: String, private val elementsToGo: ListIterator<Expr>) : Frame() {
        val elements: MutableList<Value> = mutableListOf()
        fun shift(value: Value): Expr? {
            elements.add(value)
            if (!elementsToGo.hasNext()) return null
            return elementsToGo.next()
        }
    }
    data class AppFrameLeft(val right: Expr) : Frame()
    data class AppFrameRight(val left: Value) : Frame()
    data class FunCallFrame(val env: Scope, val metadata : Value.BindingData?) : Frame()
    data class ThunkCallFrame(val env: Scope, val index: Int, val metadata : Value.BindingData) : Frame()
}
