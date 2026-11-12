package com.scalc.calculator.exchange.ui

import com.scalc.calculator.common.NumberFormatting
import com.scalc.calculator.exchange.model.Currency
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Display rules for converted amounts on the Exchange screen. Locale-independent like the rest of
 * the app: '.' as decimal separator and ',' as digit grouping.
 */
internal object ExchangeFormatting {

    /** Shown instead of a converted amount while no rates are available. */
    const val UNAVAILABLE = "—"

    /** Money precision for amounts of 1 and more, e.g. "13,909.31". */
    const val STANDARD_FRACTION_DIGITS = 2

    /**
     * Significant digits kept for amounts below 1, so 1 JPY shows as "0.005644" EUR instead of a
     * misleading "0.01".
     */
    const val SMALL_VALUE_SIGNIFICANT_DIGITS = 4

    /** Values that would need more fraction digits than this use scientific notation ("5.2E-9"). */
    const val MAX_FRACTION_DIGITS = 10

    /**
     * Formats a converted amount like a price: exactly two decimals for zero and |value| >= 1
     * ("30,472.50"), [SMALL_VALUE_SIGNIFICANT_DIGITS] significant digits below 1 ("0.005644"), and
     * the integer part grouped.
     */
    fun convertedAmount(value: BigDecimal): String {
        val magnitude = value.abs()
        if (value.signum() != 0 && magnitude < BigDecimal.ONE) {
            // floor(log10(|value|)) for 0 < |value| < 1, e.g. -3 for 0.0056 (unaffected by trailing zeros).
            val exponent = magnitude.precision() - magnitude.scale() - 1
            val fractionDigits = -exponent - 1 + SMALL_VALUE_SIGNIFICANT_DIGITS
            val small = if (fractionDigits > MAX_FRACTION_DIGITS) {
                NumberFormatting.format(value, SMALL_VALUE_SIGNIFICANT_DIGITS)
            } else {
                NumberFormatting.formatFixedMax(value, fractionDigits)
            }
            // A value that rounds up to 1 (0.999995) is shown like any other amount of 1 or more.
            val roundedBelowOne = small.toBigDecimalOrNull()?.abs()?.let { it < BigDecimal.ONE } ?: true
            if (roundedBelowOne) return NumberFormatting.groupDigits(small)
        }
        val fixed = value.setScale(STANDARD_FRACTION_DIGITS, RoundingMode.HALF_UP).toPlainString()
        return NumberFormatting.groupDigits(fixed)
    }
}

/** Order of the currency pickers: common currencies first, then all others by code. */
internal object CurrencyOrdering {

    /** Commonly used currencies, pinned in this order when the provider publishes them. */
    val PINNED: List<String> = listOf("EUR", "USD", "GBP", "CZK", "JPY", "CHF", "PLN")

    /** [currencies] with the [PINNED] ones first (in pinned order), the rest sorted by code. */
    fun sort(currencies: List<Currency>): List<Currency> {
        val byCode = currencies.associateBy { it.code }
        val pinned = PINNED.mapNotNull { byCode[it] }
        val others = currencies.filter { it.code !in PINNED }.sortedBy { it.code }
        return pinned + others
    }
}
