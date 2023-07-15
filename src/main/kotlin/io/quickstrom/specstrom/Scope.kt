package io.quickstrom.specstrom

data class Scope(val bindings: MutableMap<String, Value>, val next: Scope?) {
    fun lookup(id: String): Value? {
        var x: Value? = null
        var it : Scope? = this
        while (it != null && x == null) {
            x = bindings[id]
            it = next
        }
        return x
    }
}
