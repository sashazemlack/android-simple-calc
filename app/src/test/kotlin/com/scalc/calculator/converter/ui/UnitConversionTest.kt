package com.scalc.calculator.converter.ui

import com.scalc.calculator.common.AmountInput
import com.scalc.calculator.converter.engine.UnitCatalog
import com.scalc.calculator.converter.engine.UnitCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConversionTest {

    private fun unit(category: UnitCategory, key: String) = requireNotNull(UnitCatalog.find(category, key))

    @Test
    fun initialConversionUsesDefaultUnitsAndZero() {
        val temperature = UnitConversion.initial(UnitCategory.TEMPERATURE)
        assertEquals("0", temperature.amount.text)
        assertEquals("32", temperature.resultText)
        assertEquals("°F = °C × 9/5 + 32", temperature.formula)
        assertFalse(temperature.isBelowAbsoluteZero)
    }

    @Test
    fun derivedValuesFollowEveryCopy() {
        val base = UnitConversion.initial(UnitCategory.MASS)
        val typed = base.copy(amount = AmountInput("2.5"))
        assertEquals("5.51155655462", typed.resultText)
        val grams = typed.copy(to = unit(UnitCategory.MASS, "gram"))
        assertEquals("2,500", grams.resultText)
        assertEquals("1 kg = 1,000 g", grams.formula)
        assertNotEquals(typed, grams)
    }

    @Test
    fun incompleteDecimalInputIsConverted() {
        val conversion = UnitConversion(
            UnitCategory.LENGTH,
            AmountInput("3."),
            unit(UnitCategory.LENGTH, "foot"),
            unit(UnitCategory.LENGTH, "inch"),
        )
        assertEquals("36", conversion.resultText)
    }

    @Test
    fun largeResultsBelowTenToTheFifteenthShowEveryIntegerDigit() {
        val areas = UnitConversion(
            UnitCategory.AREA,
            AmountInput("1000000"),
            unit(UnitCategory.AREA, "square_mile"),
            unit(UnitCategory.AREA, "square_meter"),
        )
        assertEquals("2,589,988,110,336", areas.resultText)
        assertEquals("1 mi² = 2,589,988 m²", areas.formula)

        val miles = UnitConversion(
            UnitCategory.LENGTH,
            AmountInput("1"),
            unit(UnitCategory.LENGTH, "mile"),
            unit(UnitCategory.LENGTH, "millimeter"),
        )
        // The result and the rate hint directly below it agree.
        assertEquals("1,609,344", miles.resultText)
        assertEquals("1 mi = 1,609,344 mm", miles.formula)

        val lengths = UnitConversion(
            UnitCategory.LENGTH,
            AmountInput("123456789012345"),
            unit(UnitCategory.LENGTH, "millimeter"),
            unit(UnitCategory.LENGTH, "centimeter"),
        )
        assertEquals("12,345,678,901,235", lengths.resultText)
    }

    @Test
    fun negativeTemperaturesBelowAbsoluteZeroAreFlagged() {
        val base = UnitConversion.initial(UnitCategory.TEMPERATURE)
        assertFalse(base.copy(amount = AmountInput("-273.15")).isBelowAbsoluteZero)
        val impossible = base.copy(amount = AmountInput("-300"))
        assertTrue(impossible.isBelowAbsoluteZero)
        assertEquals("−508", impossible.resultText)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unitsMustMatchTheCategory() {
        UnitConversion(
            UnitCategory.LENGTH,
            AmountInput(),
            unit(UnitCategory.LENGTH, "meter"),
            unit(UnitCategory.AREA, "square_meter"),
        )
    }
}
