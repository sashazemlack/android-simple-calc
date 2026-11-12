package com.scalc.calculator.calc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.math.BigDecimal

/** Evaluates and renders the outcome: the formatted value, or "Error:<CalcError>". */
internal fun calc(expression: String, mode: AngleMode = AngleMode.DEG, carry: Carry? = null): String =
    when (val result = CalculatorEngine.evaluate(expression, mode, carry)) {
        is EvalResult.Value -> CalculatorEngine.format(result.value)
        is EvalResult.Error -> "Error:${result.error}"
    }

internal fun valueOf(expression: String, mode: AngleMode = AngleMode.DEG, carry: Carry? = null): BigDecimal =
    when (val result = CalculatorEngine.evaluate(expression, mode, carry)) {
        is EvalResult.Value -> result.value
        is EvalResult.Error -> throw AssertionError("Expected a value for '$expression' but got ${result.error}")
    }

/** Asserts the exact value (BigDecimal.compareTo, scale-insensitive). */
internal fun assertExact(expected: String, expression: String, mode: AngleMode = AngleMode.DEG) {
    val actual = valueOf(expression, mode)
    assertTrue("'$expression': expected exactly $expected but was ${actual.toPlainString()}", BigDecimal(expected).compareTo(actual) == 0)
}

internal fun assertError(expected: CalcError, expression: String, mode: AngleMode = AngleMode.DEG) {
    assertEquals("'$expression'", "Error:$expected", calc(expression, mode))
}

/** Asserts the display string of the result. */
internal fun assertShows(expected: String, expression: String, mode: AngleMode = AngleMode.DEG) {
    assertEquals("'$expression'", expected, calc(expression, mode))
}
