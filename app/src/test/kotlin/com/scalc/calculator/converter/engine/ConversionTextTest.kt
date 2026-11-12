package com.scalc.calculator.converter.engine

import com.scalc.calculator.converter.engine.ConversionAssertions.unit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ConversionTextTest {

    @Test
    fun valueUsesTwelveSignificantDigitsGroupingAndUnicodeMinus() {
        assertEquals("0", ConversionText.value(BigDecimal.ZERO))
        assertEquals("2.54", ConversionText.value(BigDecimal("2.540000")))
        assertEquals("1,234,567.891", ConversionText.value(BigDecimal("1234567.891")))
        assertEquals("−40", ConversionText.value(BigDecimal("-40")))
        assertEquals("−273.15", ConversionText.value(BigDecimal("-273.15")))
        assertEquals("0.333333333333", ConversionText.value(BigDecimal("0.3333333333333333333")))
        assertEquals("3.28083989501", ConversionText.value(BigDecimal("3.280839895013123359580052")))
        assertEquals("745.699871582", ConversionText.value(BigDecimal("745.69987158227022")))
        // Rounding up into a new digit.
        assertEquals("1,000", ConversionText.value(BigDecimal("999.9999999999999")))
    }

    @Test
    fun valueSwitchesToScientificNotationForExtremeMagnitudes() {
        assertEquals("1E+20", ConversionText.value(BigDecimal("1e20")))
        assertEquals("1.5E+15", ConversionText.value(BigDecimal("1500000000000000")))
        assertEquals("2.58998811034E+18", ConversionText.value(BigDecimal("2589988110336000000")))
        assertEquals("1.5E-9", ConversionText.value(BigDecimal("0.0000000015")))
        assertEquals("−1.5E-9", ConversionText.value(BigDecimal("-0.0000000015")))
        assertEquals("0.0000001", ConversionText.value(BigDecimal("0.0000001")))
        assertEquals("100,000,000,000,000", ConversionText.value(BigDecimal("1e14")))
    }

    @Test
    fun longPlainIntegerPartsAreShownInFullWithoutInventedZeros() {
        // 13-15 integer digits are below 10^15 and therefore plain: never round inside them.
        assertEquals("2,589,988,110,336", ConversionText.value(BigDecimal("2589988110336.000000")))
        assertEquals("2,589,988,110,336", ConversionText.value(BigDecimal("2589988110336")))
        assertEquals("12,345,678,901,235", ConversionText.value(BigDecimal("12345678901234.5")))
        assertEquals("−12,345,678,901,235", ConversionText.value(BigDecimal("-12345678901234.5")))
        assertEquals("123,456,789,012,346", ConversionText.value(BigDecimal("123456789012345.6")))
        // Rounding the units digit up into 10^15 switches to scientific notation.
        assertEquals("1E+15", ConversionText.value(BigDecimal("999999999999999.6")))
        // At most 12 integer digits keep the regular 12-significant-digit rounding.
        assertEquals("123,456,789,012", ConversionText.value(BigDecimal("123456789012.4")))
        assertEquals("12,345,678,901.2", ConversionText.value(BigDecimal("12345678901.23")))
    }

    @Test
    fun rateHintNeverRoundsInsideItsIntegerPart() {
        assertEquals(
            "1 mi = 1,609,344 mm",
            ConversionText.formula(unit(UnitCategory.LENGTH, "mile"), unit(UnitCategory.LENGTH, "millimeter")),
        )
        assertEquals(
            "1 mi² = 2,589,988 m²",
            ConversionText.formula(unit(UnitCategory.AREA, "square_mile"), unit(UnitCategory.AREA, "square_meter")),
        )
        // Fractional rates keep six significant digits.
        assertEquals(
            "1 mi = 1,609.34 m",
            ConversionText.formula(unit(UnitCategory.LENGTH, "mile"), unit(UnitCategory.LENGTH, "meter")),
        )
    }

    @Test
    fun linearFormulaShowsTheRateOfOneSourceUnit() {
        assertEquals("1 m = 3.28084 ft", ConversionText.formula(unit(UnitCategory.LENGTH, "meter"), unit(UnitCategory.LENGTH, "foot")))
        assertEquals("1 in = 2.54 cm", ConversionText.formula(unit(UnitCategory.LENGTH, "inch"), unit(UnitCategory.LENGTH, "centimeter")))
        assertEquals(
            "1 km² = 1,000,000 m²",
            ConversionText.formula(unit(UnitCategory.AREA, "square_kilometer"), unit(UnitCategory.AREA, "square_meter")),
        )
        assertEquals(
            "1 mm = 0.000000621371 mi",
            ConversionText.formula(unit(UnitCategory.LENGTH, "millimeter"), unit(UnitCategory.LENGTH, "mile")),
        )
        assertEquals(
            "1 mg = 1E-9 t",
            ConversionText.formula(unit(UnitCategory.MASS, "milligram"), unit(UnitCategory.MASS, "tonne")),
        )
        assertEquals(
            "1 kW = 1.34102 hp",
            ConversionText.formula(unit(UnitCategory.POWER, "kilowatt"), unit(UnitCategory.POWER, "mechanical_horsepower")),
        )
        assertEquals("1 m = 1 m", ConversionText.formula(unit(UnitCategory.LENGTH, "meter"), unit(UnitCategory.LENGTH, "meter")))
    }

    @Test
    fun temperatureFormulasUseOffsets() {
        val c = unit(UnitCategory.TEMPERATURE, "celsius")
        val f = unit(UnitCategory.TEMPERATURE, "fahrenheit")
        val k = unit(UnitCategory.TEMPERATURE, "kelvin")
        assertEquals("°F = °C × 9/5 + 32", ConversionText.formula(c, f))
        assertEquals("°C = (°F − 32) × 5/9", ConversionText.formula(f, c))
        assertEquals("K = °C + 273.15", ConversionText.formula(c, k))
        assertEquals("°C = K − 273.15", ConversionText.formula(k, c))
        assertEquals("K = (°F − 32) × 5/9 + 273.15", ConversionText.formula(f, k))
        assertEquals("°F = (K − 273.15) × 9/5 + 32", ConversionText.formula(k, f))
        assertEquals("1 °C = 1 °C", ConversionText.formula(c, c))
    }
}
