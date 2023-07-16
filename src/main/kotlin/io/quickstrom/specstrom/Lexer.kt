package io.quickstrom.specstrom

import java.io.BufferedReader
import java.io.PushbackReader
import java.io.Reader



class Lexer(filename : String, reader : Reader) : Iterator<Tok> {
    class PositionedReader(var pos : Positioned.Position, reader : Reader) {
        private val reader : PushbackReader = PushbackReader(BufferedReader(reader))
        var oldCol = 0
        fun read() : Int {
            val x = reader.read()
            if (x == -1 || x == 65535) { return -1 }
            if (x == '\n'.code) {
                pos.row += 1
                oldCol = pos.col
                pos.col = 1
            } else {
                oldCol = pos.col
                pos.col += 1
            }
            return x
        }
        fun unread(c : Int) {
            reader.unread(c)
            if (c == '\n'.code) {
                pos.row -= 1
                pos.col = oldCol
                oldCol = 0 //this is a bug if we backtrack too much, but shouldn't happen
            } else {
                pos.col -= 1
                oldCol = 0
            }
        }
    }
    private val reader = PositionedReader(Positioned.Position(filename,1,1), reader)
    override fun hasNext(): Boolean {
        val x = reader.read()
        if (x < 0) return false
        reader.unread(x)
        return true
    }

    private fun lexParens(): Tok? {
        val c = reader.read()
        return when (c.toChar()) {
            '(' -> Tok.LParen
            ')' -> Tok.RParen
            else -> { reader.unread(c); null }
        }
    }
    private fun lexInteger(): Tok? {
        var b = 0
        var v: Int
        var read = false
        while (true) {
            v = reader.read()
            if (v < 0) break
            val c = v.toChar()
            if (c.isDigit()) {
                b = b * 10 + c.digitToInt()
                read = true
            } else break
        }
        reader.unread(v)
        return if (!read) null else Tok.IntLit(b)
    }
    private fun lexSingleIdent(): Tok? {
        val v = reader.read()
        if (v < 0) return null
        return when (v.toChar()) {
            in "[]{};," -> Tok.Ident(v.toChar().toString())
            else -> { reader.unread(v); null }
        }
    }
    private fun lexAlphaIdent(): Tok? {
        val b = StringBuilder()
        var v: Int
        while (true) {
            v = reader.read()
            if (v < 0) break
            val c = v.toChar()
            if (c.isLetterOrDigit() || c in "_'!?@#$") {
                b.append(c)
            } else break
        }
        reader.unread(v)
        return if (b.isEmpty()) null else Tok.Ident(b.toString())
    }
    private fun lexSymbolIdent(): Tok? {
        val b = StringBuilder()
        var v: Int
        while (true) {
            v = reader.read()
            if (v < 0) break
            val c = v.toChar()
            if (!(c.isWhitespace() || c.isLetterOrDigit() || c in "[]{};," || c in "()\"\'`")) {
                b.append(c)
            } else break
        }
        reader.unread(v)
        return if (b.isEmpty()) null else Tok.Ident(b.toString())
    }

    private fun skipWhitespace() {
        var v: Int
        while (true) {
            v = reader.read()
            if (v < 0) break
            val c = v.toChar()
            if (!c.isWhitespace()) break
        }
        reader.unread(v)
    }
    override fun next(): Tok {
        skipWhitespace()
        val pos = Positioned.Position(reader.pos.file,reader.pos.row,reader.pos.col)
        return (lexInteger() ?: lexParens() ?: lexSingleIdent() ?: lexAlphaIdent() ?: lexSymbolIdent() ?: Tok.Unknown(reader.read().toChar())).at(pos)
    }
}