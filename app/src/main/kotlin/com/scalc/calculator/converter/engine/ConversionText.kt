package com.scalc.calculator.converter.engine

import com.scalc.calculator.common.MINUS_SIGN
import com.scalc.calculator.common.NumberFormatting
import java.math.BigDecimal

/** Display strings for conversion results and the formula hint under the result. */
object ConversionText {

    /** Significant digits shown for a converted value. */
    const val RESULT_SIGNIFICANT_DIGITS = 12

    /** Significant digits of the "1 m = 3.28084 ft" rate hint. */
    const val HINT_SIGNIFICANT_DIGITS = 6

    private const val TIMES = '×'

    /**
     * Converted value for display: at most 12 significant digits, grouped, Unicode minus sign.
     * An integer part longer than 12 digits that is still shown in plain notation (below 10^15)
     * is shown in full, rounded to the units digit, so no invented trailing zeros appear.
     */
    fun value(value: BigDecimal): String = formatted(value, RESULT_SIGNIFICANT_DIGITS)

    /**
     * One-line explanation of the conversion. Linear units show the rate of one source unit
     * ("1 m = 3.28084 ft"); temperature scales differ in their zero point, so a rate would be
     * misleading and the conventional formula is shown instead ("°F = °C × 9/5 + 32").
     */
    fun formula(from: MeasureUnit, to: MeasureUnit): String {
        if (from.category == UnitCategory.TEMPERATURE && from.key != to.key) {
            temperatureFormula(from, to)?.let { return it }
        }
        val rate = UnitConverter.convert(BigDecimal.ONE, from, to)
        return "1 ${from.symbol} = ${formatted(rate, HINT_SIGNIFICANT_DIGITS)} ${to.symbol}"
    }

    private fun temperatureFormula(from: MeasureUnit, to: MeasureUnit): String? {
        val f = from.symbol
        val t = to.symbol
        val minus = MINUS_SIGN
        return when (from.key to to.key) {
            "celsius" to "fahrenheit" -> "$t = $f $TIMES 9/5 + 32"
            "fahrenheit" to "celsius" -> "$t = ($f $minus 32) $TIMES 5/9"
            "celsius" to "kelvin" -> "$t = $f + 273.15"
            "kelvin" to "celsius" -> "$t = $f $minus 273.15"
            "fahrenheit" to "kelvin" -> "$t = ($f $minus 32) $TIMES 5/9 + 273.15"
            "kelvin" to "fahrenheit" -> "$t = ($f $minus 273.15) $TIMES 9/5 + 32"
            else -> null
        }
    }

    /**
     * Formats [value] with [significantDigits] significant digits, except that an integer part
     * which is longer but still printed in plain notation is never rounded inside: "2,589,988,110,336"
     * instead of "2,589,988,110,340", and "1 mi = 1,609,344 mm" instead of "1,609,340 mm".
     */
    private fun formatted(value: BigDecimal, significantDigits: Int): String {
        val integerDigits = if (value.signum() == 0) 0 else value.precision() - value.scale()
        val digits = if (integerDigits in significantDigits + 1..PLAIN_INTEGER_DIGITS) integerDigits else significantDigits
        return NumberFormatting.groupDigits(
            NumberFormatting.format(value, maxSignificant = digits, negativeSign = MINUS_SIGN.toString()),
        )
    }

    /** Values below 10^15 are shown in plain notation ([NumberFormatting.format]'s default plainUpperExponent). */
    private const val PLAIN_INTEGER_DIGITS = 15
}
