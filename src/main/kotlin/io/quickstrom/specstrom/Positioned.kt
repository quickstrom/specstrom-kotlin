package io.quickstrom.specstrom

abstract class Positioned {
    data class Position(val i: Int, val j: Int)
    var pos: Position? = null
}
