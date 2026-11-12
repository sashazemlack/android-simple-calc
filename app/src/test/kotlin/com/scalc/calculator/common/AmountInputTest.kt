package com.scalc.calculator.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AmountInputTest {

    private fun type(vararg keys: NumericKey, start: AmountInput = AmountInput()): AmountInput =
        keys.fold(start) { acc, key -> acc.press(key) }

    private fun digits(text: String): Array<NumericKey> =
        text.map { NumericKey.digit(it - '0') }.toTypedArray()

    @Test
    fun defaultIsZero() {
        val input = AmountInput()
        assertEquals("0", input.text)
        assertEquals(0, BigDecimal.ZERO.compareTo(input.value))
        assertFalse(input.isNegative)
    }

    @Test
    fun digitKeyMapping() {
        (0..9).forEach { d -> assertEquals('0' + d, NumericKey.digit(d).digit) }
        assertNull(NumericKey.Decimal.digit)
    }

    @Test
    fun firstDigitReplacesTheLeadingZero() {
        assertEquals("5", type(NumericKey.D5).text)
        assertEquals("57", type(NumericKey.D5, NumericKey.D7).text)
        assertEquals("0", type(NumericKey.D0, NumericKey.D0).text)
        assertEquals("10", type(NumericKey.D1, NumericKey.D0).text)
    }

    @Test
    fun doubleZeroOnZeroStaysZeroAndAppendsOtherwise() {
        val zero = AmountInput()
        assertSame(zero, zero.press(NumericKey.DoubleZero))
        assertEquals("500", type(NumericKey.D5, NumericKey.DoubleZero).text)
        assertEquals("0.00", type(NumericKey.Decimal, NumericKey.DoubleZero).text)
        assertEquals("-0", type(NumericKey.ToggleSign, NumericKey.DoubleZero).text)
    }

    @Test
    fun decimalPointOnlyOnce() {
        assertEquals("0.", type(NumericKey.Decimal).text)
        assertEquals("0.5", type(NumericKey.Decimal, NumericKey.D5).text)
        assertEquals("1.25", type(NumericKey.D1, NumericKey.Decimal, NumericKey.D2, NumericKey.Decimal, NumericKey.D5).text)
        assertEquals(0, BigDecimal("3").compareTo(type(NumericKey.D3, NumericKey.Decimal).value))
    }

    @Test
    fun atMostFifteenDigits() {
        val full = type(*digits("123456789012345"))
        assertEquals("123456789012345", full.text)
        assertSame(full, full.press(NumericKey.D6))
        assertSame(full, full.press(NumericKey.DoubleZero))
        // The decimal point itself is not a digit, fraction digits still count.
        val withPoint = full.press(NumericKey.Decimal)
        assertEquals("123456789012345.", withPoint.text)
        assertSame(withPoint, withPoint.press(NumericKey.D1))

        val fourteen = type(*digits("12345678901234"))
        assertSame(fourteen, fourteen.press(NumericKey.DoubleZero))
        assertEquals("123456789012345", fourteen.press(NumericKey.D5).text)

        val fraction = type(NumericKey.Decimal, *digits("12345678901234"))
        assertEquals("0.12345678901234", fraction.text)
        assertSame(fraction, fraction.press(NumericKey.D9))
        assertEquals(15, AmountInput.MAX_DIGITS)
    }

    @Test
    fun toggleSign() {
        val five = type(NumericKey.D5)
        val minusFive = five.press(NumericKey.ToggleSign)
        assertEquals("-5", minusFive.text)
        assertTrue(minusFive.isNegative)
        assertEquals("5", minusFive.press(NumericKey.ToggleSign).text)
        // Toggling on zero keeps the sign for the next digit.
        val minusZero = AmountInput().press(NumericKey.ToggleSign)
        assertEquals("-0", minusZero.text)
        assertEquals("-7", minusZero.press(NumericKey.D7).text)
        assertEquals("-0.5", type(NumericKey.Decimal, NumericKey.D5, NumericKey.ToggleSign).text)
    }

    @Test
    fun backspaceFallsBackToZero() {
        val input = type(NumericKey.D1, NumericKey.D2, NumericKey.D3)
        assertEquals("12", input.press(NumericKey.Backspace).text)
        assertEquals("0", type(NumericKey.Backspace, NumericKey.Backspace, NumericKey.Backspace, start = input).text)
        assertEquals("0", AmountInput().press(NumericKey.Backspace).text)
        assertEquals("0", type(NumericKey.D5, NumericKey.ToggleSign, NumericKey.Backspace).text)
        assertEquals("-1", type(NumericKey.D1, NumericKey.D2, NumericKey.ToggleSign, NumericKey.Backspace).text)
        assertEquals("1", type(NumericKey.D1, NumericKey.Decimal, NumericKey.Backspace).text)
        assertEquals("0", type(NumericKey.Decimal, NumericKey.Backspace).text)
    }

    @Test
    fun clearResets() {
        assertEquals("0", type(NumericKey.D9, NumericKey.ToggleSign, NumericKey.Decimal, NumericKey.D1, NumericKey.Clear).text)
    }

    @Test
    fun displayGroupsDigitsAndUsesUnicodeMinus() {
        assertEquals("1,234,567", AmountInput("1234567").display)
        assertEquals("1,234.5678", AmountInput("1234.5678").display)
        assertEquals("−1,234", AmountInput("-1234").display)
        assertEquals("1,234.", AmountInput("1234.").display)
        assertEquals("0", AmountInput().display)
    }

    @Test
    fun fromTextValidatesPersistedValues() {
        assertEquals("0", AmountInput.fromText(null).text)
        assertEquals("0", AmountInput.fromText("").text)
        assertEquals("0", AmountInput.fromText("-").text)
        assertEquals("0", AmountInput.fromText("abc").text)
        assertEquals("0", AmountInput.fromText("1,234").text)
        assertEquals("12.5", AmountInput.fromText(" 12.5 ").text)
        assertEquals("-3", AmountInput.fromText("-3").text)
    }
}
