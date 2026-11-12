package com.scalc.calculator.calc.engine

import java.math.BigDecimal
import java.util.EnumSet

/** Lexical categories of the calculator's display syntax. */
internal enum class TokenType {
    Number,
    Plus,
    Minus,
    Times,
    Divide,

    /** '%' followed by the start of an operand: binary truncated remainder ("10%3"). */
    Modulo,

    /** '%' not followed by the start of an operand: postfix percent, x / 100 ("10%", "10%+5"). */
    Percent,
    Caret,
    Bang,
    LParen,
    RParen,
    Sqrt,
    Pi,
    Euler,
    Func,
    End,
}

/** Functions written as `name(` in the display syntax. Ordered so "asin" is matched before "sin". */
internal enum class MathFunction(val displayName: String) {
    Asin("asin"),
    Acos("acos"),
    Atan("atan"),
    Sin("sin"),
    Cos("cos"),
    Tan("tan"),
    Log("log"),
    Ln("ln"),
}

internal class Token(
    val type: TokenType,
    val text: String = "",
    val function: MathFunction? = null,
    /** Exact value to use instead of [text] for a number token (see [Carry]). */
    val exactValue: BigDecimal? = null,
)

/**
 * Splits a display expression into tokens. Accepts the display operators plus ASCII '-', '*'
 * and '/' for robustness; whitespace is ignored.
 *
 * Numbers are digits with at most one '.', optionally followed by an upper-case exponent
 * `E[+|−|-]digits`. A dangling exponent at the very end ("1.5E+") is [CalcError.Incomplete].
 *
 * @param exactNumberIndex start index of the number token that takes [exactNumberValue], or -1.
 */
internal class Tokenizer(
    private val text: String,
    private val exactNumberIndex: Int = -1,
    private val exactNumberValue: BigDecimal? = null,
) {
    private val tokens = ArrayList<Token>()
    private var pos = 0

    fun tokenize(): List<Token> {
        while (pos < text.length) {
            val c = text[pos]
            when {
                c.isWhitespace() -> pos++
                c.isAsciiDigit() || c == '.' -> readNumber()
                else -> readSymbol(c)
            }
        }
        tokens += Token(TokenType.End)
        classifyPercents()
        return tokens
    }

    private fun readNumber() {
        val start = pos
        var digits = 0
        var seenPoint = false
        while (pos < text.length) {
            val c = text[pos]
            when {
                c.isAsciiDigit() -> digits++
                c == '.' -> if (seenPoint) fail(CalcError.Syntax) else seenPoint = true
                else -> break
            }
            pos++
        }
        if (digits == 0) fail(CalcError.Syntax)
        if (pos < text.length && text[pos] == 'E') readExponent()
        val exact = if (start == exactNumberIndex) exactNumberValue else null
        tokens += Token(TokenType.Number, text.substring(start, pos), exactValue = exact)
    }

    private fun readExponent() {
        var p = pos + 1
        if (p < text.length && text[p] in EXPONENT_SIGNS) p++
        val digitsStart = p
        while (p < text.length && text[p].isAsciiDigit()) p++
        if (p == digitsStart) fail(if (p >= text.length) CalcError.Incomplete else CalcError.Syntax)
        pos = p
    }

    private fun readSymbol(c: Char) {
        val type = when (c) {
            '+' -> TokenType.Plus
            '−', '-' -> TokenType.Minus
            '×', '*' -> TokenType.Times
            '÷', '/' -> TokenType.Divide
            '^' -> TokenType.Caret
            '%' -> TokenType.Percent
            '!' -> TokenType.Bang
            '(' -> TokenType.LParen
            ')' -> TokenType.RParen
            '√' -> TokenType.Sqrt
            'π' -> TokenType.Pi
            else -> null
        }
        if (type != null) {
            tokens += Token(type, c.toString())
            pos++
            return
        }
        val function = MathFunction.entries.firstOrNull { text.startsWith(it.displayName, pos) }
        if (function != null) {
            tokens += Token(TokenType.Func, function.displayName, function = function)
            pos += function.displayName.length
            return
        }
        if (c == 'e') {
            tokens += Token(TokenType.Euler, "e")
            pos++
            return
        }
        fail(CalcError.Syntax)
    }

    /**
     * Decides for every '%' whether it is a postfix percent or a modulo operator: it is modulo
     * exactly when the next token starts an operand (number, constant, function, '√' or '(').
     * A following sign is treated as a binary operator, so "50%−10" is 0.5 − 10.
     */
    private fun classifyPercents() {
        for (i in tokens.indices) {
            if (tokens[i].type == TokenType.Percent && tokens[i + 1].type in OPERAND_STARTS) {
                tokens[i] = Token(TokenType.Modulo, "%")
            }
        }
    }

    private fun fail(error: CalcError): Nothing = throw CalcException(error)

    private fun Char.isAsciiDigit() = this in '0'..'9'

    companion object {
        private const val EXPONENT_SIGNS = "+-−"

        /** Token types that can begin an operand (and therefore follow an implicit multiplication). */
        val OPERAND_STARTS: Set<TokenType> = EnumSet.of(
            TokenType.Number, TokenType.Pi, TokenType.Euler, TokenType.Func, TokenType.Sqrt, TokenType.LParen,
        )
    }
}
