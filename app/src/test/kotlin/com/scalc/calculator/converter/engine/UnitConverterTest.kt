package com.scalc.calculator.converter.engine

import com.scalc.calculator.converter.engine.ConversionAssertions.assertExact
import com.scalc.calculator.converter.engine.ConversionAssertions.assertRounded
import com.scalc.calculator.converter.engine.ConversionAssertions.assertWithin
import com.scalc.calculator.converter.engine.ConversionAssertions.convert
import com.scalc.calculator.converter.engine.ConversionAssertions.unit
import com.scalc.calculator.converter.engine.UnitCategory.AREA
import com.scalc.calculator.converter.engine.UnitCategory.LENGTH
import com.scalc.calculator.converter.engine.UnitCategory.MASS
import com.scalc.calculator.converter.engine.UnitCategory.POWER
import com.scalc.calculator.converter.engine.UnitCategory.PRESSURE
import com.scalc.calculator.converter.engine.UnitCategory.SPEED
import com.scalc.calculator.converter.engine.UnitCategory.TEMPERATURE
import com.scalc.calculator.converter.engine.UnitCategory.VOLUME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class UnitConverterTest {

    // ---------------------------------------------------------------- Length

    @Test
    fun length_exactDefinitions() {
        assertExact("2.54", convert("1", LENGTH, "inch", "centimeter"))
        assertExact("0.3048", convert("1", LENGTH, "foot", "meter"))
        assertExact("1.609344", convert("1", LENGTH, "mile", "kilometer"))
        assertExact("0.9144", convert("1", LENGTH, "yard", "meter"))
        assertExact("12", convert("1", LENGTH, "foot", "inch"))
        assertExact("3", convert("1", LENGTH, "yard", "foot"))
        assertExact("5280", convert("1", LENGTH, "mile", "foot"))
        assertExact("1760", convert("1", LENGTH, "mile", "yard"))
        assertExact("1000000", convert("1", LENGTH, "kilometer", "millimeter"))
        assertExact("2.5", convert("25", LENGTH, "millimeter", "centimeter"))
    }

    @Test
    fun length_nonTerminatingResults() {
        assertRounded("0.621371192237334", convert("1", LENGTH, "kilometer", "mile"))
        assertRounded("0.0393700787401575", convert("1", LENGTH, "millimeter", "inch"))
        assertRounded("3.28083989501312", convert("1", LENGTH, "meter", "foot"))
        assertRounded("1.09361329833771", convert("1", LENGTH, "meter", "yard"))
    }

    // ---------------------------------------------------------------- Area

    @Test
    fun area_conversions() {
        assertExact("4046.8564224", convert("1", AREA, "acre", "square_meter"))
        assertRounded("2.4710538146717", convert("1", AREA, "hectare", "acre"))
        assertExact("2.589988110336", convert("1", AREA, "square_mile", "square_kilometer"))
        assertExact("0.09290304", convert("1", AREA, "square_foot", "square_meter"))
        assertExact("43560", convert("1", AREA, "acre", "square_foot"))
        assertExact("640", convert("1", AREA, "square_mile", "acre"))
        assertExact("100", convert("1", AREA, "square_kilometer", "hectare"))
        assertExact("0.0001", convert("1", AREA, "square_meter", "hectare"))
        assertRounded("10.7639104167097", convert("1", AREA, "square_meter", "square_foot"))
    }

    // ---------------------------------------------------------------- Volume

    @Test
    fun volume_conversions() {
        assertExact("3.785411784", convert("1", VOLUME, "us_gallon", "liter"))
        assertRounded("0.264172052358148", convert("1", VOLUME, "liter", "us_gallon"))
        assertExact("946.352946", convert("1", VOLUME, "us_quart", "milliliter"))
        assertExact("29.5735295625", convert("1", VOLUME, "us_fluid_ounce", "milliliter"))
        assertExact("236.5882365", convert("1", VOLUME, "us_cup", "milliliter"))
        assertExact("1000", convert("1", VOLUME, "cubic_meter", "liter"))
        assertExact("1000", convert("1", VOLUME, "liter", "milliliter"))
        // US customary relations: 1 gal = 4 qt = 16 cups = 128 fl oz, 1 cup = 8 fl oz.
        assertExact("4", convert("1", VOLUME, "us_gallon", "us_quart"))
        assertExact("16", convert("1", VOLUME, "us_gallon", "us_cup"))
        assertExact("128", convert("1", VOLUME, "us_gallon", "us_fluid_ounce"))
        assertExact("8", convert("1", VOLUME, "us_cup", "us_fluid_ounce"))
    }

    // ---------------------------------------------------------------- Speed

    @Test
    fun speed_conversions() {
        assertExact("10", convert("36", SPEED, "kilometer_per_hour", "meter_per_second"))
        assertRounded("0.277777777777778", convert("1", SPEED, "kilometer_per_hour", "meter_per_second"))
        assertExact("36", convert("10", SPEED, "meter_per_second", "kilometer_per_hour"))
        assertExact("1.609344", convert("1", SPEED, "mile_per_hour", "kilometer_per_hour"))
        assertExact("1.852", convert("1", SPEED, "knot", "kilometer_per_hour"))
        assertExact("0.3048", convert("1", SPEED, "foot_per_second", "meter_per_second"))
        assertExact("0.44704", convert("1", SPEED, "mile_per_hour", "meter_per_second"))
        assertRounded("1.94384449244060", convert("1", SPEED, "meter_per_second", "knot"))
        // 60 mph = 88 ft/s exactly.
        assertExact("88", convert("60", SPEED, "mile_per_hour", "foot_per_second"))
    }

    // ---------------------------------------------------------------- Mass

    @Test
    fun mass_conversions() {
        assertExact("0.45359237", convert("1", MASS, "pound", "kilogram"))
        assertExact("28.349523125", convert("1", MASS, "ounce", "gram"))
        assertExact("1000", convert("1", MASS, "tonne", "kilogram"))
        assertExact("0.001", convert("1", MASS, "milligram", "gram"))
        assertExact("16", convert("1", MASS, "pound", "ounce"))
        assertExact("1000000", convert("1", MASS, "kilogram", "milligram"))
        assertRounded("2.20462262184878", convert("1", MASS, "kilogram", "pound"))
    }

    // ---------------------------------------------------------------- Temperature

    @Test
    fun temperature_celsiusFahrenheit() {
        assertExact("32", convert("0", TEMPERATURE, "celsius", "fahrenheit"))
        assertExact("212", convert("100", TEMPERATURE, "celsius", "fahrenheit"))
        assertExact("-40", convert("-40", TEMPERATURE, "celsius", "fahrenheit"))
        assertExact("-40", convert("-40", TEMPERATURE, "fahrenheit", "celsius"))
        assertExact("37", convert("98.6", TEMPERATURE, "fahrenheit", "celsius"))
        assertExact("0", convert("32", TEMPERATURE, "fahrenheit", "celsius"))
        assertExact("100", convert("212", TEMPERATURE, "fahrenheit", "celsius"))
        assertRounded("-17.7777777777778", convert("0", TEMPERATURE, "fahrenheit", "celsius"))
    }

    @Test
    fun temperature_kelvin() {
        assertExact("-273.15", convert("0", TEMPERATURE, "kelvin", "celsius"))
        assertExact("26.85", convert("300", TEMPERATURE, "kelvin", "celsius"))
        assertExact("273.15", convert("0", TEMPERATURE, "celsius", "kelvin"))
        assertExact("373.15", convert("100", TEMPERATURE, "celsius", "kelvin"))
        assertExact("273.15", convert("32", TEMPERATURE, "fahrenheit", "kelvin"))
        assertExact("310.15", convert("98.6", TEMPERATURE, "fahrenheit", "kelvin"))
        assertExact("0", convert("-459.67", TEMPERATURE, "fahrenheit", "kelvin"))
        assertExact("-459.67", convert("0", TEMPERATURE, "kelvin", "fahrenheit"))
        assertExact("212", convert("373.15", TEMPERATURE, "kelvin", "fahrenheit"))
        assertExact("80.33", convert("300", TEMPERATURE, "kelvin", "fahrenheit"))
    }

    @Test
    fun temperature_belowAbsoluteZeroDetection() {
        val celsius = unit(TEMPERATURE, "celsius")
        val fahrenheit = unit(TEMPERATURE, "fahrenheit")
        val kelvin = unit(TEMPERATURE, "kelvin")
        assertFalse(UnitConverter.isBelowAbsoluteZero(BigDecimal("-273.15"), celsius))
        assertTrue(UnitConverter.isBelowAbsoluteZero(BigDecimal("-273.16"), celsius))
        assertFalse(UnitConverter.isBelowAbsoluteZero(BigDecimal("-459.67"), fahrenheit))
        assertTrue(UnitConverter.isBelowAbsoluteZero(BigDecimal("-460"), fahrenheit))
        assertFalse(UnitConverter.isBelowAbsoluteZero(BigDecimal.ZERO, kelvin))
        assertTrue(UnitConverter.isBelowAbsoluteZero(BigDecimal("-0.01"), kelvin))
        // Negative values of other categories are never "below absolute zero".
        assertFalse(UnitConverter.isBelowAbsoluteZero(BigDecimal("-500"), unit(LENGTH, "meter")))
    }

    // ---------------------------------------------------------------- Power

    @Test
    fun power_conversions() {
        assertExact("745.69987158227022", convert("1", POWER, "mechanical_horsepower", "watt"))
        assertExact("735.49875", convert("1", POWER, "metric_horsepower", "watt"))
        assertExact("1000", convert("1", POWER, "megawatt", "kilowatt"))
        assertExact("1000", convert("1", POWER, "kilowatt", "watt"))
        assertRounded("1.34102208959503", convert("1", POWER, "kilowatt", "mechanical_horsepower"))
        assertRounded("1.35962161730390", convert("1", POWER, "kilowatt", "metric_horsepower"))
        assertRounded("0.986320070619531", convert("1", POWER, "metric_horsepower", "mechanical_horsepower"))
    }

    @Test
    fun power_mechanicalHorsepowerIsDerivedFromFootPoundForce() {
        // 550 ft·lbf/s = 550 × 0.3048 m × 0.45359237 kg × 9.80665 m/s² per second.
        val derived = BigDecimal(550) * BigDecimal("0.3048") * BigDecimal("0.45359237") * BigDecimal("9.80665")
        assertExact(derived.toPlainString(), unit(POWER, "mechanical_horsepower").numerator)
    }

    // ---------------------------------------------------------------- Pressure

    @Test
    fun pressure_conversions() {
        assertExact("101325", convert("1", PRESSURE, "atmosphere", "pascal"))
        assertExact("1.01325", convert("1", PRESSURE, "atmosphere", "bar"))
        assertExact("100", convert("1", PRESSURE, "bar", "kilopascal"))
        assertRounded("14.5037737730209", convert("1", PRESSURE, "bar", "psi"))
        assertRounded("6.89475729316836", convert("1", PRESSURE, "psi", "kilopascal"))
        assertRounded("6894.757293168361", convert("1", PRESSURE, "psi", "pascal"))
        assertExact("133.322387415", convert("1", PRESSURE, "mmhg", "pascal"))
        assertRounded("14.6959487755134", convert("1", PRESSURE, "atmosphere", "psi"))
    }

    /**
     * The conventional millimeter of mercury (133.322387415 Pa) is not exactly 1/760 atm (that is
     * the torr, 133.322368421 Pa), so 1 atm = 759.99989... mmHg: 760 to six significant digits.
     */
    @Test
    fun pressure_atmosphereIsAbout760MillimetersOfMercury() {
        val mmHg = convert("1", PRESSURE, "atmosphere", "mmhg")
        assertRounded("760.000", mmHg)
        assertRounded("759.999891725611", mmHg)
        assertRounded("760", convert("101325", PRESSURE, "pascal", "mmhg"))
    }

    // ---------------------------------------------------------------- General properties

    @Test
    fun identityConversionsReturnTheValueUnchanged() {
        val values = listOf("0", "1", "-40", "123.456", "0.000001", "98765432109876.5")
        UnitCategory.entries.forEach { category ->
            UnitCatalog.units(category).forEach { unit ->
                values.forEach { v ->
                    assertExact(v, UnitConverter.convert(BigDecimal(v), unit, unit))
                }
            }
        }
    }

    @Test
    fun scalingIsLinearForLinearCategories() {
        assertExact("25.4", convert("10", LENGTH, "inch", "centimeter"))
        assertExact("-2.54", convert("-1", LENGTH, "inch", "centimeter"))
        assertExact("0", convert("0", AREA, "acre", "hectare"))
    }

    /**
     * A → B → A must reproduce the input within 1e-25 relative error for every ordered unit pair.
     * Affine temperature scales accumulate rounding of their offsets, so for them the tolerance is
     * relative to max(|value|, 1).
     */
    @Test
    fun roundTripPreservesPrecisionForAllUnitPairs() {
        val samples = listOf("0", "1", "0.000001", "0.001", "0.5", "3.14159", "-40", "123.456", "98765.4321", "1000000000000")
            .map(::BigDecimal)
        val relative = BigDecimal("1e-25")
        var checked = 0
        UnitCategory.entries.forEach { category ->
            val units = UnitCatalog.units(category)
            for (a in units) for (b in units) for (v in samples) {
                val back = UnitConverter.convert(UnitConverter.convert(v, a, b), b, a)
                val scale = if (category == TEMPERATURE) v.abs().max(BigDecimal.ONE) else v.abs()
                assertWithin(v, back, relative * scale, "${a.key} -> ${b.key} -> ${a.key} for $v:")
                checked++
            }
        }
        assertEquals(UnitCategory.entries.sumOf { UnitCatalog.units(it).size.let { n -> n * n } } * samples.size, checked)
    }

    @Test
    fun resultsAreRoundedToDecimal128() {
        val r = convert("1", LENGTH, "meter", "foot")
        assertTrue("precision ${r.precision()}", r.precision() <= UnitConverter.MATH_CONTEXT.precision)
    }

    @Test(expected = IllegalArgumentException::class)
    fun convertingAcrossCategoriesIsRejected() {
        UnitConverter.convert(BigDecimal.ONE, unit(LENGTH, "meter"), unit(MASS, "kilogram"))
    }

    @Test
    fun identityShortCircuitReturnsSameInstance() {
        val v = BigDecimal("12.50")
        assertSame(v, UnitConverter.convert(v, unit(TEMPERATURE, "celsius"), unit(TEMPERATURE, "celsius")))
    }
}
