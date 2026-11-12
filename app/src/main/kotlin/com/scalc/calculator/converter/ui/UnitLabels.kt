package com.scalc.calculator.converter.ui

import androidx.annotation.StringRes
import com.scalc.calculator.R
import com.scalc.calculator.converter.engine.MeasureUnit
import com.scalc.calculator.converter.engine.UnitCategory
import com.scalc.calculator.ui.components.Glyph

/** Localized category title. */
@get:StringRes
internal val UnitCategory.titleRes: Int
    get() = when (this) {
        UnitCategory.LENGTH -> R.string.conv_category_length
        UnitCategory.AREA -> R.string.conv_category_area
        UnitCategory.VOLUME -> R.string.conv_category_volume
        UnitCategory.SPEED -> R.string.conv_category_speed
        UnitCategory.MASS -> R.string.conv_category_mass
        UnitCategory.TEMPERATURE -> R.string.conv_category_temperature
        UnitCategory.POWER -> R.string.conv_category_power
        UnitCategory.PRESSURE -> R.string.conv_category_pressure
    }

/** Line icon shown on the category tile. */
internal val UnitCategory.glyph: Glyph
    get() = when (this) {
        UnitCategory.LENGTH -> Glyph.Length
        UnitCategory.AREA -> Glyph.Area
        UnitCategory.VOLUME -> Glyph.Volume
        UnitCategory.SPEED -> Glyph.Speed
        UnitCategory.MASS -> Glyph.Mass
        UnitCategory.TEMPERATURE -> Glyph.Temperature
        UnitCategory.POWER -> Glyph.Power
        UnitCategory.PRESSURE -> Glyph.Pressure
    }

/**
 * Localized unit name. Every key of [com.scalc.calculator.converter.engine.UnitCatalog] must be
 * listed here (verified by a unit test); the engine's English [MeasureUnit.name] is the reference.
 */
@get:StringRes
internal val MeasureUnit.nameRes: Int
    get() = when (key) {
        "millimeter" -> R.string.conv_unit_millimeter
        "centimeter" -> R.string.conv_unit_centimeter
        "meter" -> R.string.conv_unit_meter
        "kilometer" -> R.string.conv_unit_kilometer
        "inch" -> R.string.conv_unit_inch
        "foot" -> R.string.conv_unit_foot
        "yard" -> R.string.conv_unit_yard
        "mile" -> R.string.conv_unit_mile

        "square_meter" -> R.string.conv_unit_square_meter
        "square_kilometer" -> R.string.conv_unit_square_kilometer
        "hectare" -> R.string.conv_unit_hectare
        "acre" -> R.string.conv_unit_acre
        "square_foot" -> R.string.conv_unit_square_foot
        "square_mile" -> R.string.conv_unit_square_mile

        "milliliter" -> R.string.conv_unit_milliliter
        "liter" -> R.string.conv_unit_liter
        "cubic_meter" -> R.string.conv_unit_cubic_meter
        "us_gallon" -> R.string.conv_unit_us_gallon
        "us_quart" -> R.string.conv_unit_us_quart
        "us_fluid_ounce" -> R.string.conv_unit_us_fluid_ounce
        "us_cup" -> R.string.conv_unit_us_cup

        "meter_per_second" -> R.string.conv_unit_meter_per_second
        "kilometer_per_hour" -> R.string.conv_unit_kilometer_per_hour
        "mile_per_hour" -> R.string.conv_unit_mile_per_hour
        "foot_per_second" -> R.string.conv_unit_foot_per_second
        "knot" -> R.string.conv_unit_knot

        "milligram" -> R.string.conv_unit_milligram
        "gram" -> R.string.conv_unit_gram
        "kilogram" -> R.string.conv_unit_kilogram
        "tonne" -> R.string.conv_unit_tonne
        "ounce" -> R.string.conv_unit_ounce
        "pound" -> R.string.conv_unit_pound

        "celsius" -> R.string.conv_unit_celsius
        "fahrenheit" -> R.string.conv_unit_fahrenheit
        "kelvin" -> R.string.conv_unit_kelvin

        "watt" -> R.string.conv_unit_watt
        "kilowatt" -> R.string.conv_unit_kilowatt
        "megawatt" -> R.string.conv_unit_megawatt
        "metric_horsepower" -> R.string.conv_unit_metric_horsepower
        "mechanical_horsepower" -> R.string.conv_unit_mechanical_horsepower

        "pascal" -> R.string.conv_unit_pascal
        "kilopascal" -> R.string.conv_unit_kilopascal
        "bar" -> R.string.conv_unit_bar
        "atmosphere" -> R.string.conv_unit_atmosphere
        "psi" -> R.string.conv_unit_psi
        "mmhg" -> R.string.conv_unit_mmhg

        else -> error("No name resource for unit '$key'")
    }
