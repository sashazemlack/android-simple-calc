package com.scalc.calculator.calc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Display formatting, re-parsing of formatted results, carried exact values and performance. */
class FormatAndCarryTest {

    private fun format(text: String) = CalculatorEngine.format(BigDecimal(text))

    @Test fun formatsLargeNumbersInScientificNotation() {
        assertEquals("1E+20", format("1E+20"))
        assertEquals("1.5E+20", format("1.5E+20"))
        assertEquals("1.23456789012346E+15", format("1234567890123456"))
    }

    @Test fun formatsFifteenDigitIntegersPlainly() = assertEquals("123456789012345", format("123456789012345"))

    @Test fun formatsSmallNumbers() {
        assertEquals("0.0000001", format("0.0000001"))
        assertEquals("1E-8", format("0.00000001"))
        assertEquals("−2.5E-9", format("-2.5E-9"))
    }

    @Test fun formatsZeroAndNegatives() {
        assertEquals("0", format("0"))
        assertEquals("0", format("0.000"))
        assertEquals("−5", format("-5"))
    }

    @Test fun negativeSignIsUnicodeMinus() {
        val shown = calc("2−5")
        assertEquals("−3", shown)
        assertEquals(0x2212, shown[0].code)
    }

    @Test fun stripsTrailingZeros() = assertEquals("2.5", format("2.500"))

    @Test fun formattedResultsReparseToTheSameDisplay() {
        val expressions = listOf("1÷3", "−2÷3", "10^20", "−1.5×10^(−9)", "π", "1000!", "0.1", "123.456", "−e", "2^0.5")
        for (expression in expressions) {
            val shown = calc(expression)
            assertEquals("round trip of $expression via $shown", shown, calc(shown))
        }
    }

    @Test fun formattedExponentReparses() = assertExact("-0.0000000025", "−2.5E-9")

    @Test fun carryKeepsExactValueOfPreviousResult() {
        val third = valueOf("1÷3")
        val text = CalculatorEngine.format(third)
        assertEquals("0.333333333333333", text)
        assertEquals("1", calc("$text×3", carry = Carry(text, third)))
    }

    @Test fun withoutCarryTheDisplayedDigitsAreUsed() = assertEquals("0.999999999999999", calc("0.333333333333333×3"))

    @Test fun carryIsIgnoredWhenTheNumberContinues() {
        val third = valueOf("1÷3")
        val text = CalculatorEngine.format(third)
        val value = valueOf("${text}4×3", carry = Carry(text, third))
        assertEquals(0, BigDecimal("1.0000000000000002").compareTo(value))
    }

    @Test fun negativeCarry() {
        val value = valueOf("−1÷3")
        val text = CalculatorEngine.format(value)
        assertEquals("−0.333333333333333", text)
        assertEquals("−1", calc("$text×3", carry = Carry(text, value)))
    }

    @Test fun carryAppliesAfterSignToggle() {
        val third = valueOf("1÷3")
        val text = CalculatorEngine.format(third)
        assertEquals("−1", calc("−$text×3", carry = Carry(text, third)))
    }

    @Test fun carryAppliesInsideLeadingParenthesis() {
        val value = valueOf("−1÷3")
        val text = CalculatorEngine.format(value)
        assertEquals("−1", calc("($text)×3", carry = Carry(text, value)))
        assertEquals("−0.999999999999999", calc("($text)×3"))
    }

    @Test fun carryInScientificNotation() {
        val value = valueOf("1E+20÷3")
        val text = CalculatorEngine.format(value)
        assertEquals("3.33333333333333E+19", text)
        assertEquals("1E+20", calc("$text×3", carry = Carry(text, value)))
    }

    @Test fun staleCarryIsIgnored() = assertEquals("10", calc("5×2", carry = Carry("5", BigDecimal("7"))))

    @Test fun carryOnlyAppliesToTheFirstNumber() {
        val third = valueOf("1÷3")
        val text = CalculatorEngine.format(third)
        val value = valueOf("1+$text", carry = Carry(text, third))
        assertEquals(0, BigDecimal("1.333333333333333").compareTo(value))
    }

    @Test fun carriedRoundedResultCancelsExactly() {
        // "1÷3 =", "×3 =" leaves the exact 0.999…9 (34 nines) displayed as "1"; "1−1" must be 0.
        val almostOne = valueOf("1÷3").multiply(BigDecimal(3))
        assertEquals("0", calc("1−1", carry = Carry("1", almostOne)))
    }

    @Test fun extremeTrigArgumentsAreFast() {
        val expressions = listOf(
            "sin(−1E-99999)".repeat(70),
            "cos(−1E-99999)".repeat(70),
            "acos(−1E-99999)".repeat(66),
            "asin(1E-99999)".repeat(70),
            "tan(−0.5E-99999)".repeat(60),
        )
        for (mode in AngleMode.entries) {
            for (expression in expressions) {
                assertTrue(expression.length <= CalculatorEngine.MAX_EXPRESSION_LENGTH)
                repeat(3) { CalculatorEngine.evaluate(expression, mode) }
                val bestMillis = (1..5).minOf {
                    val start = System.nanoTime()
                    CalculatorEngine.evaluate(expression, mode)
                    (System.nanoTime() - start) / 1e6
                }
                assertTrue("${expression.take(16)}… in $mode took $bestMillis ms", bestMillis < 20.0)
            }
        }
    }

    @Test fun evaluationIsFast() {
        val expressions = listOf(
            "125+25×2", "sin(30)+cos(45)×tan(60)", "1000!÷999!", "2^99999", "√2×√3", "ln(1E+500)",
            "(1+2)(3+4)(5+6)%7", "1E+99999%7", "10^(log(2))", "sin(1E+99999)", "asin(0.5)+acos(0.5)",
            "(".repeat(200) + "1" + ")".repeat(200),
        )
        repeat(20) { expressions.forEach { CalculatorEngine.evaluate(it, AngleMode.DEG) } }
        val runs = 50
        val start = System.nanoTime()
        repeat(runs) { expressions.forEach { CalculatorEngine.evaluate(it, AngleMode.DEG) } }
        val averageMillis = (System.nanoTime() - start) / 1e6 / (runs * expressions.size)
        assertTrue("average evaluation took $averageMillis ms", averageMillis < 5.0)
    }
}
