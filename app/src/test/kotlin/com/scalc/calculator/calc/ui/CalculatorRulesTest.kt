package com.scalc.calculator.calc.ui

import com.scalc.calculator.calc.engine.AngleMode
import com.scalc.calculator.calc.engine.CalcError
import com.scalc.calculator.calc.engine.CalculatorEngine
import com.scalc.calculator.calc.engine.Carry
import com.scalc.calculator.calc.engine.EvalResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CalculatorRulesTest {

    private fun preview(expression: String, justEvaluated: Boolean = false) =
        CalculatorRules.preview(expression, justEvaluated, CalculatorEngine.evaluate(expression, AngleMode.DEG))

    @Test
    fun closeParenthesesBalancesTheExpressionForHistory() {
        assertEquals("sin(30)", CalculatorRules.closeParentheses("sin(30"))
        assertEquals("2×(3+√(4))", CalculatorRules.closeParentheses("2×(3+√(4"))
        assertEquals("(1+2)×3", CalculatorRules.closeParentheses("(1+2)×3"))
        assertEquals("12+7×3", CalculatorRules.closeParentheses("12+7×3"))
    }

    @Test
    fun plainNumbers() {
        listOf("0", "12", "−5", "-5", "3.", ".5", "0.25", "1.5E+20", "2E-9", "1E5").forEach {
            assertTrue(it, CalculatorRules.isPlainNumber(it))
        }
        listOf("", "−", "π", "2π", "5+", "(5)", "1.5E", "5!", "10%", "√4", "1E+").forEach {
            assertFalse(it, CalculatorRules.isPlainNumber(it))
        }
    }

    @Test
    fun previewShowsFormattedValuesOfRealExpressions() {
        assertEquals(Preview("175", null), preview("125+25×2"))
        assertEquals(Preview("−3", null), preview("2−5"))
        assertEquals(Preview("1E+20", null), preview("10^20"))
    }

    @Test
    fun previewIsEmptyWhileTypingAndForPlainNumbers() {
        assertEquals(Preview.None, preview(""))
        assertEquals(Preview.None, preview("5+"))
        assertEquals(Preview.None, preview("sin("))
        assertEquals(Preview.None, preview("5..2"))
        assertEquals(Preview.None, preview("42"))
        assertEquals(Preview.None, preview("−42"))
    }

    @Test
    fun previewIsEmptyRightAfterEquals() = assertEquals(Preview.None, preview("2+2", justEvaluated = true))

    @Test
    fun previewShowsGenuineEvaluationErrors() {
        assertEquals(Preview(null, CalcError.DivisionByZero), preview("5÷0"))
        assertEquals(Preview(null, CalcError.Domain), preview("√(−4)"))
        assertEquals(Preview(null, CalcError.InvalidFactorial), preview("(−1)!"))
        assertEquals(Preview(null, CalcError.Overflow), preview("10^100000"))
    }

    @Test
    fun pasteAppendsOnlyWhereAnOperandIsExpected() {
        listOf("5+", "5−", "5×", "5÷", "2^", "(", "sin(", "2×√", "5×−").forEach {
            assertTrue(it, CalculatorRules.pasteAppends(it, justEvaluated = false))
        }
        listOf("", "5", "(5)", "π", "5!", "10%").forEach {
            assertFalse(it, CalculatorRules.pasteAppends(it, justEvaluated = false))
        }
        assertFalse(CalculatorRules.pasteAppends("5+", justEvaluated = true))
    }

    @Test
    fun carryContinuesWhileTheExpressionStartsWithItsText() {
        val carry = Carry("0.333333333333333", BigDecimal.ONE.divide(BigDecimal(3), java.math.MathContext.DECIMAL128))
        assertTrue(CalculatorRules.continuesFrom("0.333333333333333", carry))
        assertTrue(CalculatorRules.continuesFrom("0.333333333333333×3", carry))
        assertTrue(CalculatorRules.continuesFrom("−0.333333333333333", carry))
        assertTrue(CalculatorRules.continuesFrom("(−0.333333333333333)^", carry))
        assertFalse(CalculatorRules.continuesFrom("0.33333333333333", carry))
        assertFalse(CalculatorRules.continuesFrom("", carry))
        assertFalse(CalculatorRules.continuesFrom("7", carry))

        val negative = Carry("−2.5", BigDecimal("-2.5"))
        assertTrue(CalculatorRules.continuesFrom("−2.5+1", negative))
        assertTrue(CalculatorRules.continuesFrom("2.5", negative))
    }

    @Test
    fun evaluationResultTypesAreHandled() {
        val value = CalculatorRules.preview("1+1", false, EvalResult.Value(BigDecimal("2")))
        assertEquals("2", value.text)
        val incomplete = CalculatorRules.preview("1+", false, EvalResult.Error(CalcError.Incomplete))
        assertEquals(Preview.None, incomplete)
    }
}
