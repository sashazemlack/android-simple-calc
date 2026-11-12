package com.scalc.calculator.calc.engine

/** The number lexeme at the end of an expression, e.g. "12.5", "0.", "1.5E+20" or "1.5E+" (incomplete). */
internal class NumberLexeme(val start: Int, val text: String) {
    private val exponentIndex = text.indexOf('E')

    val hasExponent: Boolean get() = exponentIndex >= 0
    val hasPoint: Boolean get() = '.' in text

    /** Digits before the exponent. */
    val mantissaDigits: Int get() = (if (hasExponent) text.substring(0, exponentIndex) else text).count { it in '0'..'9' }

    val exponentDigits: Int get() = if (hasExponent) text.substring(exponentIndex + 1).count { it in '0'..'9' } else 0

    /** "1.5E" or "1.5E+": an exponent marker without digits. */
    val isExponentIncomplete: Boolean get() = hasExponent && text.last() !in '0'..'9'

    /** Index in the whole expression where the exponent marker 'E' sits, or -1. */
    val exponentStart: Int get() = if (hasExponent) start + exponentIndex else -1
}

/** Character-level analysis of display expressions shared by [ExpressionEditor]. */
internal object ExpressionText {
    const val MINUS = '−'

    /** Binary operator characters that can trail an expression. '%' is handled separately (postfix percent). */
    const val BINARY_OPERATORS = "+−×÷^"

    /** Function names inserted and deleted as a unit with '('. "asin" precedes "sin" for suffix matching. */
    val FUNCTION_NAMES = listOf("asin", "acos", "atan", "sin", "cos", "tan", "log", "ln")

    fun isMinus(c: Char) = c == MINUS || c == '-'

    fun isMantissaChar(c: Char) = c in '0'..'9' || c == '.'

    /** True when [c] can end an operand: digit, '.', ')', a constant, '!' or a postfix '%'. */
    fun endsOperand(c: Char) = isMantissaChar(c) || c == ')' || c == 'π' || c == 'e' || c == '!' || c == '%'

    /** A minus at [index] is unary when it starts the expression or follows '(', '√', '×', '÷' or '^'. */
    fun isUnaryMinusAt(expr: String, index: Int): Boolean =
        isMinus(expr[index]) && (index == 0 || expr[index - 1] in "(√×÷^")

    /** The number at the very end of [expr], including an exponent suffix, or null. */
    fun trailingNumber(expr: String): NumberLexeme? {
        var i = expr.length
        while (i > 0 && expr[i - 1] in '0'..'9') i--
        // Possible exponent: "...E", "...E+", "...E-" directly before the trailing digits.
        val markerAt = when {
            i >= 2 && expr[i - 1] in "+-−" && expr[i - 2] == 'E' -> i - 2
            i >= 1 && expr[i - 1] == 'E' -> i - 1
            else -> -1
        }
        if (markerAt > 0 && isMantissaChar(expr[markerAt - 1])) {
            val mantissaStart = scanMantissaStart(expr, markerAt)
            return NumberLexeme(mantissaStart, expr.substring(mantissaStart))
        }
        val start = scanMantissaStart(expr, expr.length)
        return if (start == expr.length) null else NumberLexeme(start, expr.substring(start))
    }

    private fun scanMantissaStart(expr: String, end: Int): Int {
        var s = end
        while (s > 0 && isMantissaChar(expr[s - 1])) s--
        return s
    }

    /** True when [expr] ends with an operand (a number, possibly with an unfinished exponent, counts). */
    fun endsWithOperand(expr: String): Boolean =
        expr.isNotEmpty() && (endsOperand(expr.last()) || trailingNumber(expr) != null)

    /**
     * Start index of the operand at the end of [expr]: the whole implicit-multiplication chain
     * ("2π", "3!2") including '√' prefixes and a function name before a closing group
     * ("sin(30)"). A '%' followed by an operand is a modulo operator and ends the chain.
     * Returns null when [expr] does not end with an operand.
     */
    fun trailingOperandStart(expr: String): Int? {
        var start = atomStart(expr, expr.length) ?: return null
        while (start > 0) {
            val prev = expr[start - 1]
            if (prev == '%' || !endsOperand(prev)) break
            start = atomStart(expr, start) ?: break
        }
        return start
    }

    private fun atomStart(expr: String, end: Int): Int? {
        var e = end
        while (e > 0 && (expr[e - 1] == '!' || expr[e - 1] == '%')) e--
        if (e == 0) return null
        val c = expr[e - 1]
        var start = when {
            c == ')' -> {
                val open = matchingOpenParen(expr, e - 1) ?: return null
                open - functionNameBefore(expr, open).length
            }
            c == 'π' || c == 'e' -> e - 1
            else -> trailingNumber(expr.substring(0, e))?.start ?: return null
        }
        while (start > 0 && expr[start - 1] == '√') start--
        return start
    }

    private fun matchingOpenParen(expr: String, closeIndex: Int): Int? {
        var depth = 0
        for (i in closeIndex downTo 0) {
            when (expr[i]) {
                ')' -> depth++
                '(' -> if (--depth == 0) return i
            }
        }
        return null
    }

    /** The function name directly before the '(' at [openIndex], or "". */
    fun functionNameBefore(expr: String, openIndex: Int): String =
        FUNCTION_NAMES.firstOrNull { name -> openIndex >= name.length && expr.startsWith(name, openIndex - name.length) }
            ?: ""

    /** Number of '(' not yet closed. */
    fun unmatchedOpenParens(expr: String): Int {
        var depth = 0
        for (c in expr) {
            when (c) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
            }
        }
        return depth
    }
}
