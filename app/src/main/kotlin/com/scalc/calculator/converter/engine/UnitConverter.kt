package com.scalc.calculator.converter.engine

import java.math.BigDecimal
import java.math.MathContext

/** Exact-decimal unit conversion through the category's canonical base unit. */
object UnitConverter {

    /** 34 significant digits (IEEE 754 decimal128), far beyond what is ever displayed. */
    val MATH_CONTEXT: MathContext = MathContext.DECIMAL128

    /**
     * Converts [value] expressed in [from] into [to].
     *
     * Both affine relations are folded into one fraction so that only one rounded division
     * happens per conversion:
     *
     * ```
     * result = (value + from.offset) × from.numerator × to.denominator
     *          ÷ (from.denominator × to.numerator) − to.offset
     * ```
     * Exactly representable results (e.g. 1 in → 2.54 cm, 98.6 °F → 37 °C) are therefore exact.
     *
     * @throws IllegalArgumentException when the units belong to different categories.
     */
    fun convert(value: BigDecimal, from: MeasureUnit, to: MeasureUnit): BigDecimal {
        require(from.category == to.category) { "Cannot convert ${from.category} to ${to.category}" }
        if (from.key == to.key) return value
        val dividend = (value + from.offset) * from.numerator * to.denominator
        val divisor = from.denominator * to.numerator
        val scaled = dividend.divide(divisor, MATH_CONTEXT)
        return if (to.offset.signum() == 0) scaled else (scaled - to.offset).round(MATH_CONTEXT)
    }

    /**
     * True when [value] in the temperature [unit] lies below absolute zero (0 K), i.e. it
     * describes a physically impossible temperature. Always false for other categories.
     */
    fun isBelowAbsoluteZero(value: BigDecimal, unit: MeasureUnit): Boolean =
        unit.category == UnitCategory.TEMPERATURE && (value + unit.offset).signum() < 0
}
