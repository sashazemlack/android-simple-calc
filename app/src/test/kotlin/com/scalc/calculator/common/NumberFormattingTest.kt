package com.scalc.calculator.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class NumberFormattingTest {

    private fun format(value: String, maxSignificant: Int = 15, negativeSign: String = "-") =
        NumberFormatting.format(BigDecimal(value), maxSignificant, negativeSign)

    // ---------------------------------------------------------------- format: plain notation

    @Test
    fun zeroInAnyScaleIsPlainZero() {
        assertEquals("0", format("0"))
        assertEquals("0", format("0.000"))
        assertEquals("0", format("-0"))
        assertEquals("0", NumberFormatting.format(BigDecimal("0E+10")))
    }

    @Test
    fun trailingZerosAreStripped() {
        assertEquals("123.45", format("123.4500"))
        assertEquals("100", format("100.00"))
        assertEquals("1200", format("1.2E+3"))
        assertEquals("2.5", format("2.50"))
    }

    @Test
    fun plainRangeBoundaries() {
        // Upper bound: plain while |x| < 10^15.
        assertEquals("999999999999999", format("999999999999999"))
        assertEquals("1E+15", format("1000000000000000"))
        assertEquals("1.5E+20", format("150000000000000000000"))
        // Lower bound: plain while |x| >= 10^-7.
        assertEquals("0.0000001", format("0.0000001"))
        assertEquals("0.00000012345", format("0.00000012345"))
        assertEquals("1E-8", format("0.00000001"))
        assertEquals("9.9E-8", format("0.000000099"))
    }

    @Test
    fun customPlainThresholds() {
        assertEquals("1.2345E+4", NumberFormatting.format(BigDecimal("12345"), plainUpperExponent = 4))
        assertEquals("1234", NumberFormatting.format(BigDecimal("1234"), plainUpperExponent = 4))
        assertEquals("1E-3", NumberFormatting.format(BigDecimal("0.001"), plainLowerExponent = -2))
        assertEquals("0.01", NumberFormatting.format(BigDecimal("0.01"), plainLowerExponent = -2))
    }

    // ---------------------------------------------------------------- format: rounding

    @Test
    fun roundsHalfUpToMaxSignificantDigits() {
        assertEquals("0.333333333333333", format("0.33333333333333333333"))
        assertEquals("0.666666666666667", format("0.66666666666666666666"))
        assertEquals("0.13", format("0.125", maxSignificant = 2))
        assertEquals("-0.13", format("-0.125", maxSignificant = 2))
        assertEquals("2", format("2.0000000000000004"))
        assertEquals("3.14159", format("3.14159265358979", maxSignificant = 6))
    }

    @Test
    fun roundingCanCarryIntoTheNextPowerOfTen() {
        assertEquals("10", format("9.9999999999999999"))
        assertEquals("1E+15", format("999999999999999.9"))
        assertEquals("1000", format("999.96", maxSignificant = 4))
    }

    @Test
    fun scientificMantissaKeepsSignificantDigits() {
        assertEquals("1.23456789012346E+20", format("123456789012345678901"))
        assertEquals("6.02214076E+23", format("6.02214076E+23"))
        assertEquals("1.6E-19", format("1.602E-19", maxSignificant = 2))
    }

    // ---------------------------------------------------------------- format: sign

    @Test
    fun negativeSignParameter() {
        assertEquals("-2.5", format("-2.5"))
        assertEquals("−2.5", format("-2.5", negativeSign = MINUS_SIGN.toString()))
        assertEquals("−1.5E+20", format("-1.5E+20", negativeSign = MINUS_SIGN.toString()))
        // The exponent sign stays ASCII so the text remains parseable.
        assertEquals("−2.5E-9", format("-0.0000000025", negativeSign = MINUS_SIGN.toString()))
        assertEquals("2.5E-9", format("0.0000000025", negativeSign = MINUS_SIGN.toString()))
    }

    // ---------------------------------------------------------------- formatFixedMax

    @Test
    fun formatFixedMaxLimitsFractionDigits() {
        assertEquals("1.01", NumberFormatting.formatFixedMax(BigDecimal("1.005"), 2))
        assertEquals("1.5", NumberFormatting.formatFixedMax(BigDecimal("1.50"), 4))
        assertEquals("100", NumberFormatting.formatFixedMax(BigDecimal("100"), 2))
        assertEquals("0", NumberFormatting.formatFixedMax(BigDecimal("-0.001"), 2))
        assertEquals("-12.35", NumberFormatting.formatFixedMax(BigDecimal("-12.345"), 2))
        assertEquals("−12.35", NumberFormatting.formatFixedMax(BigDecimal("-12.345"), 2, MINUS_SIGN.toString()))
        assertEquals("123456789012345678", NumberFormatting.formatFixedMax(BigDecimal("123456789012345678"), 2))
    }

    // ---------------------------------------------------------------- groupDigits

    @Test
    fun groupsIntegerParts() {
        assertEquals("1,234,567", NumberFormatting.groupDigits("1234567"))
        assertEquals("123", NumberFormatting.groupDigits("123"))
        assertEquals("1,000", NumberFormatting.groupDigits("1000"))
        assertEquals("100,000", NumberFormatting.groupDigits("100000"))
        assertEquals("-1,234", NumberFormatting.groupDigits("-1234"))
        assertEquals("−1,234", NumberFormatting.groupDigits("−1234"))
        assertEquals("1 234 567", NumberFormatting.groupDigits("1234567", separator = ' '))
    }

    @Test
    fun fractionDigitsAreUntouched() {
        assertEquals("1,234.5678", NumberFormatting.groupDigits("1234.5678"))
        assertEquals("0.123456789", NumberFormatting.groupDigits("0.123456789"))
        assertEquals(".12345", NumberFormatting.groupDigits(".12345"))
        assertEquals("1,234.", NumberFormatting.groupDigits("1234."))
    }

    @Test
    fun exponentDigitsAreUntouched() {
        assertEquals("1.5E+20", NumberFormatting.groupDigits("1.5E+20"))
        assertEquals("1.5E-1234", NumberFormatting.groupDigits("1.5E-1234"))
        assertEquals("2E1234", NumberFormatting.groupDigits("2E1234"))
        assertEquals("1,234E+5678", NumberFormatting.groupDigits("1234E+5678"))
    }

    @Test
    fun everyNumberInAnExpressionIsGrouped() {
        assertEquals("12,345+67,890×2", NumberFormatting.groupDigits("12345+67890×2"))
        assertEquals("(1,000−250.125)÷3", NumberFormatting.groupDigits("(1000−250.125)÷3"))
        assertEquals("sin(1,234.5678)", NumberFormatting.groupDigits("sin(1234.5678)"))
    }
}
