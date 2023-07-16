package io.quickstrom.specstrom

abstract class Positioned {
    data class Position(var file: String, var row: Int, var col: Int)
    var position: Position? = null
}
