package com.scalc.calculator.converter.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitCatalogTest {

    @Test
    fun categoriesAreInDisplayOrderWithStableKeys() {
        assertEquals(
            listOf("length", "area", "volume", "speed", "mass", "temperature", "power", "pressure"),
            UnitCategory.entries.map { it.key },
        )
        assertEquals("Weight / Mass", UnitCategory.MASS.displayName)
        UnitCategory.entries.forEach { assertSame(it, UnitCategory.fromKey(it.key)) }
        assertNull(UnitCategory.fromKey("currency"))
        assertNull(UnitCategory.fromKey(null))
    }

    @Test
    fun onlyTemperatureAllowsNegativeAmounts() {
        assertEquals(listOf(UnitCategory.TEMPERATURE), UnitCategory.entries.filter { it.allowsNegative })
    }

    @Test
    fun everyCategoryContainsExactlyTheRequiredUnits() {
        val expected = mapOf(
            UnitCategory.LENGTH to listOf(
                "millimeter" to "mm", "centimeter" to "cm", "meter" to "m", "kilometer" to "km",
                "inch" to "in", "foot" to "ft", "yard" to "yd", "mile" to "mi",
            ),
            UnitCategory.AREA to listOf(
                "square_meter" to "m²", "square_kilometer" to "km²", "hectare" to "ha",
                "acre" to "ac", "square_foot" to "ft²", "square_mile" to "mi²",
            ),
            UnitCategory.VOLUME to listOf(
                "milliliter" to "mL", "liter" to "L", "cubic_meter" to "m³", "us_gallon" to "gal",
                "us_quart" to "qt", "us_fluid_ounce" to "fl oz", "us_cup" to "cup",
            ),
            UnitCategory.SPEED to listOf(
                "meter_per_second" to "m/s", "kilometer_per_hour" to "km/h", "mile_per_hour" to "mph",
                "foot_per_second" to "ft/s", "knot" to "kn",
            ),
            UnitCategory.MASS to listOf(
                "milligram" to "mg", "gram" to "g", "kilogram" to "kg", "tonne" to "t",
                "ounce" to "oz", "pound" to "lb",
            ),
            UnitCategory.TEMPERATURE to listOf("celsius" to "°C", "fahrenheit" to "°F", "kelvin" to "K"),
            UnitCategory.POWER to listOf(
                "watt" to "W", "kilowatt" to "kW", "megawatt" to "MW",
                "metric_horsepower" to "PS", "mechanical_horsepower" to "hp",
            ),
            UnitCategory.PRESSURE to listOf(
                "pascal" to "Pa", "kilopascal" to "kPa", "bar" to "bar", "atmosphere" to "atm",
                "psi" to "psi", "mmhg" to "mmHg",
            ),
        )
        assertEquals(UnitCategory.entries.toSet(), expected.keys)
        expected.forEach { (category, units) ->
            assertEquals("units of $category", units, UnitCatalog.units(category).map { it.key to it.symbol })
            UnitCatalog.units(category).forEach { assertSame(category, it.category) }
        }
    }

    @Test
    fun unitKeysAreGloballyUniqueAndNamed() {
        val all = UnitCategory.entries.flatMap { UnitCatalog.units(it) }
        assertEquals(46, all.size)
        assertEquals(all.size, all.map { it.key }.toSet().size)
        all.forEach { assertTrue("name of ${it.key}", it.name.isNotBlank()) }
        assertEquals("Cup (US)", UnitCatalog.find(UnitCategory.VOLUME, "us_cup")?.name)
    }

    @Test
    fun defaultPairs() {
        val expected = mapOf(
            UnitCategory.LENGTH to ("meter" to "foot"),
            UnitCategory.AREA to ("square_meter" to "square_foot"),
            UnitCategory.VOLUME to ("liter" to "us_gallon"),
            UnitCategory.SPEED to ("kilometer_per_hour" to "mile_per_hour"),
            UnitCategory.MASS to ("kilogram" to "pound"),
            UnitCategory.TEMPERATURE to ("celsius" to "fahrenheit"),
            UnitCategory.POWER to ("kilowatt" to "mechanical_horsepower"),
            UnitCategory.PRESSURE to ("bar" to "psi"),
        )
        UnitCategory.entries.forEach { category ->
            val (from, to) = UnitCatalog.defaultPair(category)
            assertEquals(expected.getValue(category), from.key to to.key)
        }
    }

    @Test
    fun findRejectsUnknownAndForeignKeys() {
        assertNull(UnitCatalog.find(UnitCategory.LENGTH, "parsec"))
        assertNull(UnitCatalog.find(UnitCategory.LENGTH, null))
        assertNull(UnitCatalog.find(UnitCategory.LENGTH, "kilogram"))
        assertEquals("ft", UnitCatalog.find(UnitCategory.LENGTH, "foot")?.symbol)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unitsNeedPositiveFactors() {
        MeasureUnit("broken", UnitCategory.LENGTH, "Broken", "?", java.math.BigDecimal.ZERO)
    }
}
