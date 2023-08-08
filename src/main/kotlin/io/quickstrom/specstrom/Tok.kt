package io.quickstrom.specstrom
import org.nineml.coffeegrinder.tokens.Token;
sealed abstract class Tok : Token(listOf()) {

    var position : Positioned.Position? = null
    fun at(pos : Positioned.Position) : Tok {
        position = pos

        return this
    }

    sealed abstract class TokSelector : Token(listOf()) {
        object AnyIdent : TokSelector() {
            override fun matches(input: Token?): Boolean {
                return (input is Tok.Ident)
            }
            override fun getValue(): String {
                return "[identifier]"
            }
        }
        object FieldName : TokSelector() {
            override fun matches(input: Token?): Boolean {
                return (input is Tok.Ident && input.name.all{ it.isLetterOrDigit() })
            }
            override fun getValue(): String {
                return "[field name]"
            }
        }
        object AnyInteger : TokSelector() {
            override fun matches(input: Token?): Boolean {
                return (input is Tok.IntLit)
            }
            override fun getValue(): String {
                return "[integer]"
            }
        }
    }
    data class Ident(val name : String) : Tok() {
        override fun getValue(): String {
            return name
        }
    }
    data class IntLit(val value : Int) : Tok() {
        override fun getValue(): String {
            return value.toString()
        }
    }
    object LParen : Tok() {
        override fun getValue(): String {
            return "("
        }
    }
    object RParen : Tok() {
        override fun getValue(): String {
            return ")"
        }
    }
    data class Unknown(val c : Char) : Tok() {
        override fun getValue(): String {
            return c.toString()
        }
    }
    override fun matches(input: Token?): Boolean {
        return (input == this)
    }
}