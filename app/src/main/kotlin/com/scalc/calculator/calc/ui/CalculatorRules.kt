package com.scalc.calculator.calc.ui

import com.scalc.calculator.calc.engine.CalcError
import com.scalc.calculator.calc.engine.CalculatorEngine
import com.scalc.calculator.calc.engine.Carry
import com.scalc.calculator.calc.engine.EvalResult
import com.scalc.calculator.common.MINUS_SIGN

/** What the live-result line shows: a formatted value, an error, or nothing. */
internal data class Preview(val text: String?, val error: CalcError?) {
    companion object {
        val None = Preview(text = null, error = null)
    }
}

/** Pure presentation rules of the calculator screen, kept apart from the ViewModel for testing. */
internal object CalculatorRules {

    /**
     * [expression] with its still-open parentheses closed, as evaluated and as stored in history:
     * "sin(30" becomes "sin(30)" and "2×(3+4" becomes "2×(3+4)".
     */
    fun closeParentheses(expression: String): String {
        val open = expression.count { it == '(' } - expression.count { it == ')' }
        return if (open > 0) expression + ")".repeat(open) else expression
    }

    /** A single number such as "12", "−0.5", "3." or "1.5E+20" (nothing to preview). */
    private val PLAIN_NUMBER = Regex("""[−-]?(\d+\.?\d*|\.\d+)(E[+\-−]?\d+)?""")

    /** Characters after which a pasted expression is appended instead of replacing the input. */
    private const val OPERAND_EXPECTED = "+−-×÷^(√"

    private val SIGNS = charArrayOf(MINUS_SIGN, '-', '+')

    fun isPlainNumber(expression: String): Boolean = PLAIN_NUMBER.matches(expression)

    /**
     * Live result for [expression]: nothing right after '=' (the result is the expression), for
     * input that is still being typed ([CalcError.Incomplete], [CalcError.Syntax]) or for a single
     * plain number; the error for genuine evaluation failures; otherwise the formatted value.
     */
    fun preview(expression: String, justEvaluated: Boolean, result: EvalResult): Preview {
        if (justEvaluated) return Preview.None
        return when (result) {
            is EvalResult.Value ->
                if (isPlainNumber(expression)) Preview.None else Preview(CalculatorEngine.format(result.value), null)
            is EvalResult.Error -> when (result.error) {
                CalcError.Incomplete, CalcError.Syntax -> Preview.None
                else -> Preview(null, result.error)
            }
        }
    }

    /**
     * True when a paste should be appended: the expression waits for an operand because it ends
     * with a binary operator, '(' (including "sin(") or '√'. A result shown after '=' is always
     * replaced.
     */
    fun pasteAppends(expression: String, justEvaluated: Boolean): Boolean =
        !justEvaluated && expression.isNotEmpty() && expression.last() in OPERAND_EXPECTED

    /**
     * True while [expression] still begins with the number [carry] was displayed as (optionally
     * after one '(' and one sign, mirroring [CalculatorEngine.evaluate]). Once the user edits that
     * number away the carry must be dropped, otherwise retyping the same digits would silently
     * reuse the hidden exact value.
     */
    fun continuesFrom(expression: String, carry: Carry): Boolean {
        var start = 0
        if (expression.startsWith("(")) start++
        if (expression.getOrNull(start)?.let { it in SIGNS } == true) start++
        val unsigned = carry.text.trimStart(*SIGNS)
        return unsigned.isNotEmpty() && expression.startsWith(unsigned, start)
    }
}
