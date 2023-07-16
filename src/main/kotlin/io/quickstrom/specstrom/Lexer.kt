package io.quickstrom.specstrom

import java.io.BufferedReader
import java.io.PushbackReader
import java.io.Reader

class Lexer(reader : Reader) : Iterator<Tok> {
    private val reader : PushbackReader = PushbackReader(BufferedReader(reader))
    override fun hasNext(): Boolean {
        val x = reader.read()
        if (x < 0 || x == 65535) return false
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
        return lexInteger() ?: lexParens() ?: lexSingleIdent() ?: lexAlphaIdent() ?: lexSymbolIdent() ?: Tok.Unknown(reader.read().toChar())
    }
}