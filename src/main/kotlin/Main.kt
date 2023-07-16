package io.quickstrom.specstrom
import java.io.File
import java.io.FileReader

fun main(args: Array<String>) {
    val lex = Lexer(FileReader(File("test.strom")))
    val p = Parser(sortedMapOf(
        0 to listOf (Parser.SyntaxRule("_+_",Parser.Assoc.Left))
    ))

    println(p.fn(lex).toString())
    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
    println("Program arguments: ${args.joinToString()}")
}