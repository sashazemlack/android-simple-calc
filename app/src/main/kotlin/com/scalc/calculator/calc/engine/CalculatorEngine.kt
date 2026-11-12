package com.scalc.calculator.calc.engine

import com.scalc.calculator.common.MINUS_SIGN
import com.scalc.calculator.common.NumberFormatting
import java.math.BigDecimal

/**
 * Safe expression evaluation for the calculator: tokenizer + recursive-descent parser +
 * BigDecimal evaluator. No eval/scripting; pure Kotlin and java.math, so it is JVM-testable.
 *
 * Semantics in brief (see [Parser] for the grammar):
 * - + − × ÷ and modulo at 34 significant digits (0.1+0.2 is exactly 0.3). A sum or difference
 *   below 1E-32 of its larger operand is rounding residue and becomes 0 ("1÷3×3−1" = 0,
 *   "1+40!−40!" = 0); consequently "10^32+1−10^32" is 0 as well.
 * - '%' is a postfix percent unless an operand follows it, in which case it is modulo
 *   (truncated remainder). "A ± B%" takes B percent of A: 200+10% = 220.
 * - '^' is right-associative and binds tighter than unary minus: −2^2 = −4, 2^3^2 = 512.
 * - Implicit multiplication: 2π, 2(3+1), (1+1)(2+2), 3sin(30), 2√9.
 * - Unclosed '(' are closed automatically at the end.
 */
object CalculatorEngine {
    /** Longer expressions are rejected as [CalcError.Syntax]. */
    const val MAX_EXPRESSION_LENGTH = 1000

    /** Significant digits shown by [format]. */
    const val DISPLAY_DIGITS = 15

    /** Evaluates [expression] in [angleMode]; [carry] supplies the exact value of a leading previous result. */
    fun evaluate(expression: String, angleMode: AngleMode, carry: Carry? = null): EvalResult {
        if (expression.length > MAX_EXPRESSION_LENGTH) return EvalResult.Error(CalcError.Syntax)
        return try {
            val carryIndex = carryIndex(expression, carry)
            val exact = if (carryIndex >= 0) carry?.value?.abs() else null
            val tokens = Tokenizer(expression, carryIndex, exact).tokenize()
            val tree = Parser(tokens).parse()
            EvalResult.Value(Evaluator(angleMode).evaluate(tree))
        } catch (e: CalcException) {
            EvalResult.Error(e.error)
        } catch (e: ArithmeticException) {
            EvalResult.Error(CalcError.Overflow)
        } catch (e: StackOverflowError) {
            EvalResult.Error(CalcError.Syntax)
        }
    }

    /**
     * Display form of a value: at most 15 significant digits, '.' decimal separator, Unicode minus,
     * scientific notation like "1.5E+20" / "2E-9" outside 1E-7 <= |x| < 1E+15. Always re-parseable
     * by [evaluate].
     */
    fun format(value: BigDecimal): String =
        NumberFormatting.format(value, DISPLAY_DIGITS, negativeSign = MINUS_SIGN.toString())

    /**
     * Checks only the syntax of [expression] (tokenizer and parser, no evaluation), so it is
     * cheap enough for the main thread. Returns [CalcError.Syntax] for malformed or overlong
     * input, [CalcError.Incomplete] for a valid prefix such as "5+", and null when the expression
     * parses; evaluation errors (division by zero, domain, overflow) are not detected here.
     * Syntax errors come only from the tokenizer and parser, so [evaluate] reports
     * [CalcError.Syntax] for the same inputs.
     */
    fun checkSyntax(expression: String): CalcError? {
        if (expression.length > MAX_EXPRESSION_LENGTH) return CalcError.Syntax
        return try {
            Parser(Tokenizer(expression).tokenize()).parse()
            null
        } catch (e: CalcException) {
            e.error
        } catch (e: StackOverflowError) {
            CalcError.Syntax
        }
    }

    /**
     * Normalizes clipboard text into display syntax (rules documented on [PasteNormalizer]).
     * Returns null when the text is blank, longer than [MAX_EXPRESSION_LENGTH] after
     * normalization, or not a valid expression ([CalcError.Syntax]); incomplete expressions such
     * as "5+" are accepted. Only the syntax is checked ([checkSyntax]); nothing is evaluated.
     */
    fun normalizePasted(text: String): String? {
        val normalized = PasteNormalizer.normalize(text) ?: return null
        if (normalized.isEmpty() || normalized.length > MAX_EXPRESSION_LENGTH) return null
        return if (checkSyntax(normalized) == CalcError.Syntax) null else normalized
    }

    /**
     * Index of the leading number token that the [carry] value applies to, or -1.
     *
     * The expression may start with '(' and/or one sign before the number (so "(−0.333…)^2" and a
     * sign-toggled result still use the exact value; the sign itself stays a unary operator). The
     * number must be followed by something other than a digit, '.' or 'E', and the carry text
     * must be the formatted carry value, which keeps a stale carry harmless.
     */
    private fun carryIndex(expression: String, carry: Carry?): Int {
        if (carry == null) return -1
        val unsignedText = carry.text.removePrefix(MINUS_SIGN.toString()).removePrefix("-")
        if (unsignedText.isEmpty() || !(unsignedText[0].isDigit() || unsignedText[0] == '.')) return -1
        if (format(carry.value.abs()) != unsignedText) return -1
        var start = 0
        if (expression.startsWith("(")) start++
        val sign = expression.getOrNull(start)
        if (sign == MINUS_SIGN || sign == '-' || sign == '+') start++
        if (!expression.startsWith(unsignedText, start)) return -1
        val next = expression.getOrNull(start + unsignedText.length)
        if (next != null && (next.isDigit() || next == '.' || next == 'E')) return -1
        return start
    }
}
