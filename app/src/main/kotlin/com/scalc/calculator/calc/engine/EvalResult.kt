package com.scalc.calculator.calc.engine

import java.math.BigDecimal

/** Outcome of [CalculatorEngine.evaluate]. */
sealed interface EvalResult {
    /** The expression evaluated to [value]. Use [CalculatorEngine.format] to display it. */
    data class Value(val value: BigDecimal) : EvalResult

    /** The expression has no value; see [CalcError]. */
    data class Error(val error: CalcError) : EvalResult
}

/** Reasons why an expression has no numeric value. */
enum class CalcError {
    /** Malformed input: unknown characters, misplaced operators, unmatched ')', "5..2", "×5". */
    Syntax,

    /**
     * A valid prefix of an expression that is not finished yet: empty input, a trailing binary
     * operator ("5+", "5×−", "2^"), an open function ("sin("), or empty parentheses ("()").
     * The UI shows neither a live result nor an error for it.
     */
    Incomplete,

    /** Division, modulo or a negative power of zero. */
    DivisionByZero,

    /**
     * Argument outside a function's domain: square root of a negative number, log/ln of x <= 0,
     * tan at an odd multiple of 90°, asin/acos outside [-1, 1], a negative base raised to a
     * non-integer exponent, or a radian sin/cos/tan argument of magnitude 1E+100 or more.
     */
    Domain,

    /** Factorial of a negative or non-integer number. */
    InvalidFactorial,

    /** A result or intermediate value reached 1E+100000 in magnitude (so n! with n > 25205 overflows). */
    Overflow,
}

/**
 * The exact [value] of a previous result together with the [text] it was displayed as.
 *
 * When a new expression begins with exactly that number (optionally after a leading '(' and/or
 * a sign), the exact value replaces the rounded display text, so "1÷3 =" followed by "×3" yields
 * 1 instead of 0.999999999999999.
 */
data class Carry(val text: String, val value: BigDecimal)

/** Internal control-flow exception carrying the [CalcError] reported to callers. */
internal class CalcException(val error: CalcError) : RuntimeException(error.name, null, false, false)
