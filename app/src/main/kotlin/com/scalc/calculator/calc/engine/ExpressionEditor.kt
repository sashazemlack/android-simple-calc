package com.scalc.calculator.calc.engine

import com.scalc.calculator.calc.engine.ExpressionText.BINARY_OPERATORS
import com.scalc.calculator.calc.engine.ExpressionText.MINUS
import com.scalc.calculator.calc.engine.ExpressionText.endsOperand
import com.scalc.calculator.calc.engine.ExpressionText.endsWithOperand
import com.scalc.calculator.calc.engine.ExpressionText.isMinus
import com.scalc.calculator.calc.engine.ExpressionText.isUnaryMinusAt
import com.scalc.calculator.calc.engine.ExpressionText.trailingNumber

/**
 * The expression being edited. [justEvaluated] is true right after '=' replaced the expression
 * with its result.
 */
data class EditorState(val expression: String = "", val justEvaluated: Boolean = false)

/**
 * Pure key-press logic for the calculator's expression line. Every function returns a new state
 * (or the same one when the key is not allowed in the current position).
 *
 * After '=' (justEvaluated): digits, '.', constants, functions, '(' start a new expression;
 * binary operators, '^', '%' and '!' continue from the result (a negative result is wrapped in
 * parentheses for '^' and '!' so "(−5)^2" squares the result); backspace edits the result text;
 * toggleSign negates the result.
 *
 * Expressions are limited to [MAX_LENGTH] characters and numbers to [MAX_DIGITS] digits.
 */
object ExpressionEditor {
    const val MAX_LENGTH = 200
    const val MAX_DIGITS = 15
    private const val MAX_EXPONENT_DIGITS = 5

    private val FUNCTION_INSERTS = mapOf(
        "sin" to "sin(", "cos" to "cos(", "tan" to "tan(",
        "asin" to "asin(", "acos" to "acos(", "atan" to "atan(",
        "log" to "log(", "ln" to "ln(",
        "sqrt" to "√(", "pow10" to "10^(", "exp" to "e^(",
    )

    fun digit(s: EditorState, d: Char): EditorState {
        if (d !in '0'..'9') return s
        if (s.justEvaluated) return EditorState(d.toString())
        val expr = s.expression
        val number = trailingNumber(expr) ?: return s.append(d.toString())
        if (number.hasExponent) {
            return if (number.exponentDigits >= MAX_EXPONENT_DIGITS) s else s.append(d.toString())
        }
        if (number.text == "0") {
            // Replace a lone leading zero instead of producing "05".
            return if (d == '0') s else EditorState(expr.dropLast(1) + d)
        }
        if (number.mantissaDigits >= MAX_DIGITS) return s
        return s.append(d.toString())
    }

    fun decimalPoint(s: EditorState): EditorState {
        if (s.justEvaluated) return EditorState("0.")
        val expr = s.expression
        val number = trailingNumber(expr)
        if (number != null) return if (number.hasPoint || number.hasExponent) s else s.append(".")
        val last = expr.lastOrNull()
        return if (last == ')' || last == 'π' || last == 'e' || last == '!') s.append("×0.") else s.append("0.")
    }

    /** [op] is one of '+', '−' (or '-'), '×' (or '*'), '÷' (or '/'), '^', '%'. */
    fun operator(s: EditorState, op: Char): EditorState {
        val o = canonicalOperator(op) ?: return s
        val expr = s.expression
        if (s.justEvaluated && o == '^' && isNegativeNumber(expr)) return EditorState("($expr)^").withinLimit(s)
        if (expr.isEmpty()) return if (o == MINUS) s.append(MINUS.toString()) else s
        val number = trailingNumber(expr)
        if (number != null && number.isExponentIncomplete) return s
        if (number != null || endsOperand(expr.last())) return s.append(o.toString())

        val idx = expr.length - 1
        val last = expr[idx]
        return when {
            last == '(' || last == '√' -> if (o == MINUS) s.append(o.toString()) else s
            isUnaryMinusAt(expr, idx) -> when {
                o == MINUS -> s
                // "5×−" + '+' replaces both characters: "5+".
                idx > 0 && expr[idx - 1] in "×÷^" -> EditorState(expr.substring(0, idx - 1) + o)
                // A leading sign, or one after '(' or '√', cannot become a binary operator.
                else -> s
            }
            o == MINUS && last in "×÷^" -> s.append(o.toString())
            last in BINARY_OPERATORS || last == '-' -> EditorState(expr.dropLast(1) + o)
            else -> s
        }
    }

    /** [name] is one of sin, cos, tan, asin, acos, atan, log, ln, sqrt, pow10, exp. */
    fun function(s: EditorState, name: String): EditorState {
        val insert = FUNCTION_INSERTS[name] ?: return s
        if (s.justEvaluated) return EditorState(insert)
        val number = trailingNumber(s.expression)
        if (number != null && number.isExponentIncomplete) return s
        // "10^(" after a number would merge the digits ("210^("), so multiply explicitly.
        val text = if (name == "pow10" && number != null) "×$insert" else insert
        return s.append(text)
    }

    /** [c] is 'π' or 'e'. */
    fun constant(s: EditorState, c: Char): EditorState {
        if (c != 'π' && c != 'e') return s
        if (s.justEvaluated) return EditorState(c.toString())
        if (endsWithIncompleteExponent(s.expression)) return s
        return s.append(c.toString())
    }

    fun openParen(s: EditorState): EditorState {
        if (s.justEvaluated) return EditorState("(")
        if (endsWithIncompleteExponent(s.expression)) return s
        return s.append("(")
    }

    /** Ignored without an unmatched '(' or when the open group is empty or ends with an operator. */
    fun closeParen(s: EditorState): EditorState {
        val expr = s.expression
        if (ExpressionText.unmatchedOpenParens(expr) <= 0) return s
        if (!endsWithOperand(expr) || endsWithIncompleteExponent(expr)) return s
        return s.append(")")
    }

    fun factorial(s: EditorState): EditorState {
        val expr = s.expression
        if (s.justEvaluated && isNegativeNumber(expr)) return EditorState("($expr)!").withinLimit(s)
        if (!endsWithOperand(expr) || endsWithIncompleteExponent(expr)) return s
        return s.append("!")
    }

    /**
     * Toggles the sign of the last operand: a unary '−' before it is removed, a binary '+' becomes
     * '−' and vice versa, otherwise a unary '−' is inserted (after '%' as modulo operator it is
     * inserted as "(−" so the remainder keeps a negative divisor). Without a trailing operand the
     * sign of the operand about to be typed is toggled. After '=' the whole result is negated.
     */
    fun toggleSign(s: EditorState): EditorState {
        val expr = s.expression
        if (s.justEvaluated && isSignedNumber(expr)) {
            return when {
                isMinus(expr[0]) -> s.copy(expression = expr.substring(1))
                expr.toBigDecimalOrNull()?.signum() == 0 -> s
                else -> s.copy(expression = "$MINUS$expr")
            }
        }
        if (expr.isEmpty()) return s.append(MINUS.toString())
        if (!endsWithOperand(expr)) return toggleUpcomingSign(s)

        val start = ExpressionText.trailingOperandStart(expr) ?: return s
        val before = expr.getOrNull(start - 1)
        return when {
            before == null -> s.insert(0, MINUS.toString())
            isMinus(before) && isUnaryMinusAt(expr, start - 1) -> EditorState(expr.removeRange(start - 1, start))
            isMinus(before) -> EditorState(expr.replaceRange(start - 1, start, "+"))
            before == '+' -> EditorState(expr.replaceRange(start - 1, start, MINUS.toString()))
            before == '%' -> s.insert(start, "($MINUS")
            else -> s.insert(start, MINUS.toString())
        }
    }

    private fun toggleUpcomingSign(s: EditorState): EditorState {
        val expr = s.expression
        val idx = expr.length - 1
        val last = expr[idx]
        return when {
            isUnaryMinusAt(expr, idx) -> EditorState(expr.dropLast(1))
            last == '(' || last == '√' || last == '×' || last == '÷' || last == '^' -> s.append(MINUS.toString())
            last == '+' -> EditorState(expr.dropLast(1) + MINUS)
            isMinus(last) -> EditorState(expr.dropLast(1) + "+")
            else -> s
        }
    }

    /**
     * Removes the last token: a function name together with its '(' ("asin(") or one character.
     * An exponent marker left without digits ("1.5E+") is removed with it.
     */
    fun backspace(s: EditorState): EditorState {
        val expr = s.expression
        if (expr.isEmpty()) return EditorState()
        val name = ExpressionText.FUNCTION_NAMES.firstOrNull { expr.endsWith("$it(") }
        val shortened = if (name != null) expr.dropLast(name.length + 1) else expr.dropLast(1)
        val number = trailingNumber(shortened)
        val text = if (number != null && number.isExponentIncomplete) shortened.substring(0, number.exponentStart) else shortened
        return EditorState(text)
    }

    fun clear(): EditorState = EditorState()

    /** Replaces the whole expression (paste, history restore). */
    fun replaceWith(text: String): EditorState = EditorState(text, justEvaluated = false)

    private fun canonicalOperator(op: Char): Char? = when (op) {
        '+' -> '+'
        '−', '-' -> MINUS
        '×', '*' -> '×'
        '÷', '/' -> '÷'
        '^' -> '^'
        '%' -> '%'
        else -> null
    }

    /** True when [expr] is a single number, optionally preceded by a minus sign. */
    private fun isSignedNumber(expr: String): Boolean {
        val number = trailingNumber(expr) ?: return false
        return number.start == 0 || (number.start == 1 && isMinus(expr[0]))
    }

    private fun isNegativeNumber(expr: String): Boolean = expr.isNotEmpty() && isMinus(expr[0]) && isSignedNumber(expr)

    private fun endsWithIncompleteExponent(expr: String): Boolean = trailingNumber(expr)?.isExponentIncomplete == true

    private fun EditorState.append(text: String): EditorState {
        val next = expression + text
        return if (next.length > MAX_LENGTH) this else EditorState(next)
    }

    private fun EditorState.insert(index: Int, text: String): EditorState {
        val next = StringBuilder(expression).insert(index, text).toString()
        return if (next.length > MAX_LENGTH) this else EditorState(next)
    }

    private fun EditorState.withinLimit(fallback: EditorState): EditorState =
        if (expression.length > MAX_LENGTH) fallback else this
}
