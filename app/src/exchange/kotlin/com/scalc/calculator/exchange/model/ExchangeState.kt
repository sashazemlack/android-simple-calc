package com.scalc.calculator.exchange.model

import androidx.compose.runtime.Immutable

/** Why the latest refresh attempt failed. */
enum class ExchangeError {
    /** No network, DNS failure, timeout or TLS failure. */
    NoConnection,

    /** The provider answered with an HTTP error status. */
    Server,

    /** The provider answered, but the content was empty, malformed or incomplete. */
    InvalidData,
}

/**
 * Everything the Exchange screen needs to render, as published by the exchange repository.
 *
 * Persisted fields ([table], [lastSuccessMillis], [lastAutoAttemptMillis], [selectedFrom],
 * [selectedTo]) survive restarts; [refreshing], [lastAttemptFailed], [lastError] and
 * [refreshedThisSession] describe the current process only. The typed amount is deliberately not
 * part of this state: it is never persisted.
 */
@Immutable
data class ExchangeState(
    /** False until the on-disk cache has been read; the other persisted fields are defaults until then. */
    val loaded: Boolean = false,
    /** Cached rates, or null if rates were never downloaded successfully. */
    val table: RateTable? = null,
    /** A network refresh is running. */
    val refreshing: Boolean = false,
    /** The most recent refresh attempt of this session failed (cleared by the next success). */
    val lastAttemptFailed: Boolean = false,
    /** Reason of the most recent failure, null after a success or before any attempt. */
    val lastError: ExchangeError? = null,
    /** At least one refresh succeeded (download or HTTP 304) in this process. */
    val refreshedThisSession: Boolean = false,
    /** Epoch millis of the last successful download or re-confirmation; 0 if never. */
    val lastSuccessMillis: Long = 0L,
    /** Epoch millis of the last automatic (24 h policy) attempt, successful or not; 0 if never. */
    val lastAutoAttemptMillis: Long = 0L,
    val selectedFrom: String = CurrencySelection.DEFAULT_FROM,
    val selectedTo: String = CurrencySelection.DEFAULT_TO,
) {
    /** Currencies available for selection (sorted by code); empty while no rates are cached. */
    val currencies: List<Currency> get() = table?.currencies.orEmpty()
}
