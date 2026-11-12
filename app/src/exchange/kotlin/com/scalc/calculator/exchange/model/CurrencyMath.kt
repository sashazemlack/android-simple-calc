package com.scalc.calculator.exchange.model

import com.scalc.calculator.common.NumberFormatting
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Local currency conversion using a downloaded [RateTable]. The user's amount never leaves the
 * device: all arithmetic happens here.
 *
 * Every rate is expressed in units per 1 EUR, so a cross rate is `rate[to] / rate[from]`.
 */
object CurrencyMath {

    /** 34 significant digits (IEEE 754 decimal128) – far beyond what is ever displayed. */
    val MATH_CONTEXT: MathContext = MathContext(34, RoundingMode.HALF_EVEN)

    /**
     * Minimum significant digits used by [unitRateText]; rates of 1 and more additionally keep at
     * least [MIN_FRACTION_DIGITS] decimals.
     */
    const val UNIT_RATE_SIGNIFICANT_DIGITS = 6

    /**
     * Decimals a unit rate of 1 or more never drops below: the money precision of converted
     * amounts on the Exchange screen, and of large ECB quotes such as IDR (e.g. 20223.32).
     */
    const val MIN_FRACTION_DIGITS = 2

    /**
     * Converts [amount] of [from] into [to]: `amount × rate[to] ÷ rate[from]`.
     *
     * The product is exact and only the division is rounded (to [MATH_CONTEXT]), so there is a
     * single rounding step. Returns [amount] unchanged when both codes are equal and present, and
     * null when either code is missing from [table].
     */
    fun convert(amount: BigDecimal, from: String, to: String, table: RateTable): BigDecimal? {
        val fromRate = table.rateOf(from) ?: return null
        val toRate = table.rateOf(to) ?: return null
        if (from == to) return amount
        if (amount.signum() == 0) return BigDecimal.ZERO
        return amount.multiply(toRate).divide(fromRate, MATH_CONTEXT)
    }

    /** Value of one unit of [from] in [to] (e.g. 1 EUR = 1.1127 USD), or null if a code is missing. */
    fun unitRate(from: String, to: String, table: RateTable): BigDecimal? =
        convert(BigDecimal.ONE, from, to, table)

    /**
     * Display text such as "1 EUR = 1.1127 USD" or "1 EUR = 20,223.32 IDR", or null if a code is
     * missing from [table].
     *
     * The rate keeps at least [maxSignificant] significant digits and, when it is 1 or more, never
     * fewer than [MIN_FRACTION_DIGITS] decimals (both rounded HALF_UP, trailing zeros stripped),
     * so it always agrees with the converted value of one unit shown on the same screen. Integer
     * digits are grouped.
     */
    fun unitRateText(
        from: String,
        to: String,
        table: RateTable,
        maxSignificant: Int = UNIT_RATE_SIGNIFICANT_DIGITS,
    ): String? {
        val rate = unitRate(from, to, table) ?: return null
        val digits = if (rate.abs() >= BigDecimal.ONE) {
            // precision - scale = number of integer digits for |rate| >= 1.
            maxOf(maxSignificant, rate.precision() - rate.scale() + MIN_FRACTION_DIGITS)
        } else {
            maxSignificant
        }
        val formatted = NumberFormatting.groupDigits(NumberFormatting.format(rate, digits))
        return "1 $from = $formatted $to"
    }
}
