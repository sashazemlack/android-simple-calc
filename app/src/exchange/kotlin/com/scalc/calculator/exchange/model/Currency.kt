package com.scalc.calculator.exchange.model

import androidx.compose.runtime.Immutable

/**
 * A currency offered by the rate provider: ISO 4217 [code] (e.g. "CZK") and its English [name]
 * (the provider's, else the platform's, else the code). The name shown to the user is resolved in
 * the current language by [CurrencyNames.displayName]; [name] is its fallback and a search keyword.
 */
@Immutable
data class Currency(val code: String, val name: String)

/** ISO 4217 code helpers shared by parsing, persistence and selection. */
object CurrencyCodes {
    /** The base currency of all rates. */
    const val EUR = "EUR"
    const val USD = "USD"

    /** True for a well-formed ISO 4217 alphabetic code: exactly three ASCII upper-case letters. */
    fun isWellFormed(code: String): Boolean = code.length == 3 && code.all { it in 'A'..'Z' }
}
