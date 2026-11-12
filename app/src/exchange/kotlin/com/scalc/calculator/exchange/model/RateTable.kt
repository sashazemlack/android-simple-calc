package com.scalc.calculator.exchange.model

import androidx.compose.runtime.Immutable
import java.math.BigDecimal

/** Where a [RateTable] was downloaded from. Both carry the official ECB euro reference rates. */
enum class RateSourceKind {
    /** Frankfurter v2 API restricted to the European Central Bank provider (primary source). */
    FRANKFURTER_ECB,

    /** The ECB's own daily XML file (fallback when Frankfurter is unavailable). */
    ECB_DIRECT,
}

/**
 * A complete set of euro reference rates as stored and displayed by the app.
 *
 * Two different points in time are kept deliberately apart:
 * - [rateDate] is the provider's effective date of the rates (an ECB business day, `yyyy-MM-dd`);
 * - [fetchedAtMillis] is when this app last downloaded the table or had it re-confirmed by the
 *   server (HTTP 304). It is 0 for a table that was just parsed and not yet stored.
 *
 * Instances created through [create] are always valid: [rates] contains [base] = 1 plus at least one
 * other currency, every rate is positive, and [currencies] lists exactly the codes of [rates],
 * sorted by code.
 */
@Immutable
data class RateTable(
    val base: String = CurrencyCodes.EUR,
    /** Units of each currency per 1 [base] (EUR); includes the base itself with rate 1. */
    val rates: Map<String, BigDecimal>,
    val rateDate: String,
    val fetchedAtMillis: Long,
    /** One entry per code in [rates], sorted by code. */
    val currencies: List<Currency>,
    val source: RateSourceKind,
) {
    /** Units of [code] per 1 EUR, or null if the provider does not publish [code]. */
    fun rateOf(code: String): BigDecimal? = rates[code]

    fun contains(code: String): Boolean = rates.containsKey(code)

    /** The currency entry for [code], or null if it is not part of this table. */
    fun currencyOf(code: String): Currency? = currencies.firstOrNull { it.code == code }

    companion object {
        private val DATE_PATTERN = Regex("""\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])""")

        /** True if [date] has the provider's `yyyy-MM-dd` shape with a plausible month and day. */
        fun isValidRateDate(date: String): Boolean = DATE_PATTERN.matches(date)

        /**
         * Builds a validated, normalized table or returns null when the data is unusable.
         *
         * Rows with malformed codes or non-positive rates are dropped, the base currency is forced
         * to exactly 1, and the result must contain at least one currency besides EUR.
         * [names] supplies display names; a code without a non-blank name is displayed as itself.
         */
        fun create(
            rates: Map<String, BigDecimal>,
            rateDate: String,
            names: Map<String, String>,
            source: RateSourceKind,
            fetchedAtMillis: Long = 0L,
        ): RateTable? {
            if (!isValidRateDate(rateDate)) return null
            val valid = sortedMapOf<String, BigDecimal>()
            rates.forEach { (code, rate) ->
                if (code != CurrencyCodes.EUR && CurrencyCodes.isWellFormed(code) && rate.signum() > 0) {
                    valid[code] = rate
                }
            }
            if (valid.isEmpty()) return null
            valid[CurrencyCodes.EUR] = BigDecimal.ONE
            val currencies = valid.keys.map { code ->
                Currency(code, names[code]?.trim()?.takeIf { it.isNotEmpty() } ?: code)
            }
            return RateTable(
                base = CurrencyCodes.EUR,
                rates = LinkedHashMap(valid),
                rateDate = rateDate,
                fetchedAtMillis = fetchedAtMillis,
                currencies = currencies,
                source = source,
            )
        }
    }
}
