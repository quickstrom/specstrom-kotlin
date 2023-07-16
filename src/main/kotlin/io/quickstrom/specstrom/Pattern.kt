package io.quickstrom.specstrom

sealed class Pattern : Positioned() {
    data class LazyPattern(val name: String) : Pattern()
    sealed class StrictPattern : Pattern() {
        fun at(pos : Position?) : StrictPattern {
            position = pos
            return this
        }
    }
    data class IntLit(val value: Int) : StrictPattern()
    data class BoolLit(val value: Boolean) : StrictPattern()
    data class ListLit(val elements: List<StrictPattern>) : StrictPattern()
    data class Record(val elements: Map<String, StrictPattern>) : StrictPattern()
    data class Con(val conName: String, val elements: List<StrictPattern>) : StrictPattern()
    data class Var(val name: String) : StrictPattern()


}
