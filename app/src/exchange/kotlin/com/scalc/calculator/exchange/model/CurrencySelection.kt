package com.scalc.calculator.exchange.model

import java.util.Locale

/** Rules that keep the source/destination currency pair valid for the available rates. */
object CurrencySelection {

    const val DEFAULT_FROM = CurrencyCodes.EUR
    const val DEFAULT_TO = CurrencyCodes.USD

    /**
     * Normalizes a user/persisted choice: trims and upper-cases [code] and returns it if it can be
     * selected, i.e. it is part of [table] (or merely well-formed while no table exists yet).
     * Returns null for anything else.
     */
    fun normalize(code: String, table: RateTable?): String? {
        val normalized = code.trim().uppercase(Locale.ROOT)
        if (!CurrencyCodes.isWellFormed(normalized)) return null
        return if (table == null || table.contains(normalized)) normalized else null
    }

    /**
     * Returns a pair that exists in [table]. A code that is still available is kept as is (equal
     * codes are allowed when the user chose them). A code that disappeared falls back to EUR/USD
     * or, if those are unavailable, to the first codes of the table, preferring a pair of two
     * different currencies. Without a table, malformed codes fall back to the defaults.
     */
    fun sanitize(from: String, to: String, table: RateTable?): Pair<String, String> {
        if (table == null) {
            return (normalize(from, null) ?: DEFAULT_FROM) to (normalize(to, null) ?: DEFAULT_TO)
        }
        val keptFrom = normalize(from, table)
        val keptTo = normalize(to, table)
        val codes = table.currencies.map { it.code }
        val newFrom = keptFrom ?: pick(listOf(DEFAULT_FROM, DEFAULT_TO) + codes, codes, avoid = keptTo)
        val newTo = keptTo ?: pick(listOf(DEFAULT_TO, DEFAULT_FROM) + codes, codes, avoid = newFrom)
        return newFrom to newTo
    }

    private fun pick(candidates: List<String>, available: List<String>, avoid: String?): String =
        candidates.firstOrNull { it in available && it != avoid } ?: available.first()
}
