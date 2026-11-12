package com.scalc.calculator.converter.engine

import java.math.BigDecimal

/**
 * All supported units, grouped by category in display order.
 *
 * Factors are exact definitional values (SI / international yard and pound agreement of 1959 /
 * US customary definitions), never rounded approximations:
 *  - 1 in = 0.0254 m, 1 ft = 0.3048 m, 1 yd = 0.9144 m, 1 mi = 1609.344 m
 *  - 1 lb = 0.45359237 kg, 1 oz = 1/16 lb = 0.028349523125 kg
 *  - 1 US gal = 231 in³ = 0.003785411784 m³; quart, fluid ounce and cup are exact fractions of it
 *  - standard gravity g = 9.80665 m/s²; 1 lbf = 0.45359237 kg × g
 */
object UnitCatalog {

    /** Standard acceleration of gravity, m/s² (exact, CGPM 1901). */
    private val STANDARD_GRAVITY = BigDecimal("9.80665")
    private val POUND_KG = BigDecimal("0.45359237")
    private val FOOT_M = BigDecimal("0.3048")
    private val INCH_M = BigDecimal("0.0254")

    /** Pound-force in newtons: 0.45359237 kg × 9.80665 m/s² = 4.4482216152605 N (exact). */
    private val POUND_FORCE_N = POUND_KG * STANDARD_GRAVITY

    /** Mechanical horsepower: 550 ft·lbf/s = 745.69987158227022 W (exact). */
    private val MECHANICAL_HORSEPOWER_W = BigDecimal(550) * FOOT_M * POUND_FORCE_N

    /** Metric horsepower (PS): 75 kgf·m/s = 735.49875 W (exact). */
    private val METRIC_HORSEPOWER_W = BigDecimal(75) * STANDARD_GRAVITY

    private val byCategory: Map<UnitCategory, List<MeasureUnit>> = listOf(
        // Length, base: meter
        linear("millimeter", UnitCategory.LENGTH, "Millimeter", "mm", "0.001"),
        linear("centimeter", UnitCategory.LENGTH, "Centimeter", "cm", "0.01"),
        linear("meter", UnitCategory.LENGTH, "Meter", "m", "1"),
        linear("kilometer", UnitCategory.LENGTH, "Kilometer", "km", "1000"),
        linear("inch", UnitCategory.LENGTH, "Inch", "in", "0.0254"),
        linear("foot", UnitCategory.LENGTH, "Foot", "ft", "0.3048"),
        linear("yard", UnitCategory.LENGTH, "Yard", "yd", "0.9144"),
        linear("mile", UnitCategory.LENGTH, "Mile", "mi", "1609.344"),

        // Area, base: square meter
        linear("square_meter", UnitCategory.AREA, "Square meter", "m²", "1"),
        linear("square_kilometer", UnitCategory.AREA, "Square kilometer", "km²", "1000000"),
        linear("hectare", UnitCategory.AREA, "Hectare", "ha", "10000"),
        // International acre: 66 ft × 660 ft = 43,560 ft².
        linear("acre", UnitCategory.AREA, "Acre", "ac", "4046.8564224"),
        linear("square_foot", UnitCategory.AREA, "Square foot", "ft²", "0.09290304"),
        linear("square_mile", UnitCategory.AREA, "Square mile", "mi²", "2589988.110336"),

        // Volume, base: cubic meter
        linear("milliliter", UnitCategory.VOLUME, "Milliliter", "mL", "0.000001"),
        linear("liter", UnitCategory.VOLUME, "Liter", "L", "0.001"),
        linear("cubic_meter", UnitCategory.VOLUME, "Cubic meter", "m³", "1"),
        linear("us_gallon", UnitCategory.VOLUME, "US gallon", "gal", "0.003785411784"),
        linear("us_quart", UnitCategory.VOLUME, "US quart", "qt", "0.000946352946"),
        linear("us_fluid_ounce", UnitCategory.VOLUME, "US fluid ounce", "fl oz", "0.0000295735295625"),
        // US customary cup = 8 US fl oz (not the 240 mL "legal" nutrition-label cup).
        linear("us_cup", UnitCategory.VOLUME, "Cup (US)", "cup", "0.0002365882365"),

        // Speed, base: meter per second
        linear("meter_per_second", UnitCategory.SPEED, "Meter per second", "m/s", "1"),
        ratio("kilometer_per_hour", UnitCategory.SPEED, "Kilometer per hour", "km/h", BigDecimal(1000), BigDecimal(3600)),
        linear("mile_per_hour", UnitCategory.SPEED, "Mile per hour", "mph", "0.44704"),
        linear("foot_per_second", UnitCategory.SPEED, "Foot per second", "ft/s", "0.3048"),
        // Knot: one international nautical mile (1852 m) per hour.
        ratio("knot", UnitCategory.SPEED, "Knot", "kn", BigDecimal(1852), BigDecimal(3600)),

        // Weight / mass, base: kilogram
        linear("milligram", UnitCategory.MASS, "Milligram", "mg", "0.000001"),
        linear("gram", UnitCategory.MASS, "Gram", "g", "0.001"),
        linear("kilogram", UnitCategory.MASS, "Kilogram", "kg", "1"),
        linear("tonne", UnitCategory.MASS, "Metric tonne", "t", "1000"),
        linear("ounce", UnitCategory.MASS, "Ounce", "oz", "0.028349523125"),
        linear("pound", UnitCategory.MASS, "Pound", "lb", "0.45359237"),

        // Temperature, base: kelvin. K = (value + offset) × numerator / denominator.
        MeasureUnit("celsius", UnitCategory.TEMPERATURE, "Celsius", "°C", BigDecimal.ONE, BigDecimal.ONE, BigDecimal("273.15")),
        // K = (°F − 32) × 5/9 + 273.15 = (°F + 459.67) × 5/9
        MeasureUnit("fahrenheit", UnitCategory.TEMPERATURE, "Fahrenheit", "°F", BigDecimal(5), BigDecimal(9), BigDecimal("459.67")),
        MeasureUnit("kelvin", UnitCategory.TEMPERATURE, "Kelvin", "K", BigDecimal.ONE),

        // Power, base: watt
        linear("watt", UnitCategory.POWER, "Watt", "W", "1"),
        linear("kilowatt", UnitCategory.POWER, "Kilowatt", "kW", "1000"),
        linear("megawatt", UnitCategory.POWER, "Megawatt", "MW", "1000000"),
        MeasureUnit("metric_horsepower", UnitCategory.POWER, "Metric horsepower", "PS", METRIC_HORSEPOWER_W),
        MeasureUnit("mechanical_horsepower", UnitCategory.POWER, "Mechanical horsepower", "hp", MECHANICAL_HORSEPOWER_W),

        // Pressure, base: pascal
        linear("pascal", UnitCategory.PRESSURE, "Pascal", "Pa", "1"),
        linear("kilopascal", UnitCategory.PRESSURE, "Kilopascal", "kPa", "1000"),
        linear("bar", UnitCategory.PRESSURE, "Bar", "bar", "100000"),
        linear("atmosphere", UnitCategory.PRESSURE, "Atmosphere", "atm", "101325"),
        // psi = lbf / in² = 4.4482216152605 N / 0.00064516 m² ≈ 6894.757293168361 Pa
        ratio("psi", UnitCategory.PRESSURE, "Pound per square inch", "psi", POUND_FORCE_N, INCH_M * INCH_M),
        // Conventional millimeter of mercury: 13.5951 g/cm³ × 1 mm × g = 133.322387415 Pa.
        linear("mmhg", UnitCategory.PRESSURE, "Millimeter of mercury", "mmHg", "133.322387415"),
    ).groupBy { it.category }

    private val defaults: Map<UnitCategory, Pair<String, String>> = mapOf(
        UnitCategory.LENGTH to ("meter" to "foot"),
        UnitCategory.AREA to ("square_meter" to "square_foot"),
        UnitCategory.VOLUME to ("liter" to "us_gallon"),
        UnitCategory.SPEED to ("kilometer_per_hour" to "mile_per_hour"),
        UnitCategory.MASS to ("kilogram" to "pound"),
        UnitCategory.TEMPERATURE to ("celsius" to "fahrenheit"),
        UnitCategory.POWER to ("kilowatt" to "mechanical_horsepower"),
        UnitCategory.PRESSURE to ("bar" to "psi"),
    )

    /** Units of [category] in display order. */
    fun units(category: UnitCategory): List<MeasureUnit> = byCategory.getValue(category)

    /** Unit with [key] inside [category], or null for unknown keys (e.g. stale persisted values). */
    fun find(category: UnitCategory, key: String?): MeasureUnit? =
        if (key == null) null else units(category).firstOrNull { it.key == key }

    /** Default source and destination units of [category]. */
    fun defaultPair(category: UnitCategory): Pair<MeasureUnit, MeasureUnit> {
        val (fromKey, toKey) = defaults.getValue(category)
        return requireNotNull(find(category, fromKey)) to requireNotNull(find(category, toKey))
    }

    private fun linear(key: String, category: UnitCategory, name: String, symbol: String, factor: String) =
        MeasureUnit(key, category, name, symbol, BigDecimal(factor))

    private fun ratio(
        key: String,
        category: UnitCategory,
        name: String,
        symbol: String,
        numerator: BigDecimal,
        denominator: BigDecimal,
    ) = MeasureUnit(key, category, name, symbol, numerator, denominator)
}
