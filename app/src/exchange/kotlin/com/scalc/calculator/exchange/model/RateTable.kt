package com.scalc.calculator.exchange.model

import androidx.compose.runtime.Immutable
import java.math.BigDecimal

/** Where a [RateTable] was downloaded from. */
enum class RateSourceKind {
    /**
     * Frankfurter v2 default feed (primary source): Frankfurter's own blend of the daily rates of
     * central banks and other official providers. Each currency carries its own effective date.
     */
    FRANKFURTER_BLENDED,

    /**
     * Frankfurter v2 restricted to the European Central Bank provider: the primary source of
     * earlier app versions. Only found in caches written by those versions (until the next refresh).
     */
    FRANKFURTER_ECB,

    /** The ECB's own daily XML file (fallback when Frankfurter is unavailable). */
    ECB_DIRECT,
}

/**
 * A complete set of euro-based rates as stored and displayed by the app.
 *
 * Two different kinds of points in time are kept deliberately apart:
 * - [rateDates] are the provider's effective dates of the individual rates (`yyyy-MM-dd`);
 *   [rateDate] is the newest of them. Blended rates of different currencies may have different
 *   dates (e.g. a currency whose sources did not publish today);
 * - [fetchedAtMillis] is when this app last downloaded the table or had it re-confirmed by the
 *   server (HTTP 304). It is 0 for a table that was just parsed and not yet stored.
 *
 * Instances created through [create] are always valid: [rates] contains [base] = 1 plus at least one
 * other currency, every rate is positive, every code has a date at most [MAX_RATE_AGE_DAYS] older
 * than [rateDate], and [currencies] lists exactly the codes of [rates], sorted by code.
 */
@Immutable
data class RateTable(
    val base: String = CurrencyCodes.EUR,
    /** Units of each currency per 1 [base] (EUR); includes the base itself with rate 1. */
    val rates: Map<String, BigDecimal>,
    /** The newest effective date of [rateDates] (`yyyy-MM-dd`). */
    val rateDate: String,
    val fetchedAtMillis: Long,
    /** One entry per code in [rates], sorted by code; names are English (see [Currency]). */
    val currencies: List<Currency>,
    val source: RateSourceKind,
    /** Effective date (`yyyy-MM-dd`) of every code in [rates]; the base currency has [rateDate]. */
    val rateDates: Map<String, String>,
) {
    /** Units of [code] per 1 EUR, or null if the provider does not publish [code]. */
    fun rateOf(code: String): BigDecimal? = rates[code]

    fun contains(code: String): Boolean = rates.containsKey(code)

    /** The currency entry for [code], or null if it is not part of this table. */
    fun currencyOf(code: String): Currency? = currencies.firstOrNull { it.code == code }

    /** Effective date of the rate of [code], or null if it is not part of this table. */
    fun rateDateOf(code: String): String? = rateDates[code]

    /**
     * Effective date of the cross rate [from] -> [to]: the older of the two currencies' dates, or
     * null if a code is not part of this table.
     */
    fun pairRateDate(from: String, to: String): String? {
        val fromDate = rateDates[from] ?: return null
        val toDate = rateDates[to] ?: return null
        return minOf(fromDate, toDate)
    }

    companion object {
        /** Rates more than this many days older than the newest rate of a table are dropped as stale. */
        const val MAX_RATE_AGE_DAYS = 7L

        private val DATE_PATTERN = Regex("""\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])""")

        /** True if [date] has the provider's `yyyy-MM-dd` shape with a plausible month and day. */
        fun isValidRateDate(date: String): Boolean = DATE_PATTERN.matches(date)

        /**
         * Days since 1970-01-01 of a provider date, or null if it is not [isValidRateDate].
         * Pure arithmetic (proleptic Gregorian calendar), so it needs no java.time (API 26).
         */
        fun epochDay(date: String): Long? {
            if (!isValidRateDate(date)) return null
            val month = date.substring(5, 7).toInt()
            val day = date.substring(8, 10).toInt()
            val year = date.substring(0, 4).toLong() - if (month <= 2) 1 else 0
            val era = year.floorDiv(400L)
            val yearOfEra = year - era * 400
            val dayOfYear = (153 * ((month + 9) % 12) + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era * 146_097 + dayOfEra - 719_468
        }

        /**
         * Builds a validated, normalized table in which every rate has the same effective
         * [rateDate] (ECB data, old caches). Returns null when the data is unusable.
         */
        fun create(
            rates: Map<String, BigDecimal>,
            rateDate: String,
            names: Map<String, String>,
            source: RateSourceKind,
            fetchedAtMillis: Long = 0L,
        ): RateTable? {
            if (!isValidRateDate(rateDate)) return null
            return create(rates, rates.keys.associateWith { rateDate }, names, source, fetchedAtMillis)
        }

        /**
         * Builds a validated, normalized table or returns null when the data is unusable.
         *
         * Rows with malformed codes, non-positive rates or without a valid entry in [rateDates] are
         * dropped, as are rows more than [MAX_RATE_AGE_DAYS] older than the newest remaining row
         * (stale). The base currency is forced to exactly 1 with the newest date, and the result
         * must contain at least one currency besides EUR. [names] supplies English names; a code
         * without a non-blank name is named by itself.
         */
        fun create(
            rates: Map<String, BigDecimal>,
            rateDates: Map<String, String>,
            names: Map<String, String>,
            source: RateSourceKind,
            fetchedAtMillis: Long = 0L,
        ): RateTable? {
            val valid = sortedMapOf<String, BigDecimal>()
            val days = HashMap<String, Long>()
            rates.forEach { (code, rate) ->
                val day = rateDates[code]?.let(::epochDay) ?: return@forEach
                if (code != CurrencyCodes.EUR && CurrencyCodes.isWellFormed(code) && rate.signum() > 0) {
                    valid[code] = rate
                    days[code] = day
                }
            }
            val newestDay = days.values.maxOrNull() ?: return null
            valid.keys.retainAll { code -> newestDay - days.getValue(code) <= MAX_RATE_AGE_DAYS }
            val dates = valid.keys.associateWithTo(HashMap()) { code -> rateDates.getValue(code) }
            val newest = dates.values.max()
            valid[CurrencyCodes.EUR] = BigDecimal.ONE
            dates[CurrencyCodes.EUR] = newest
            val currencies = valid.keys.map { code ->
                Currency(code, names[code]?.trim()?.takeIf { it.isNotEmpty() } ?: code)
            }
            return RateTable(
                base = CurrencyCodes.EUR,
                rates = LinkedHashMap(valid),
                rateDate = newest,
                fetchedAtMillis = fetchedAtMillis,
                currencies = currencies,
                source = source,
                rateDates = dates,
            )
        }
    }
}
