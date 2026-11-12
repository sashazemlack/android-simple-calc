package com.scalc.calculator.exchange.ui

import androidx.compose.runtime.Immutable
import com.scalc.calculator.exchange.model.Currency
import com.scalc.calculator.exchange.model.RateStatusInfo

/**
 * Everything the Exchange screen renders. Derived values (converted amount, unit rates, status)
 * are recomputed by [ExchangeViewModel] whenever the amount, the selected pair, the rate table or
 * the clock tick changes; there is no "equals" step.
 *
 * The state contains no language-dependent text: currency names are English (see [Currency]) and
 * the status is a [RateStatusInfo]. The screen localizes both with the current configuration
 * ([localize]), so a change of the system language takes effect immediately although this
 * activity-scoped state survives the activity's recreation.
 *
 * @property loaded false until the exchange cache has been read from disk.
 * @property amountText typed amount in display form (grouped digits).
 * @property from source currency (code + English name); shown even while no rates are available.
 * @property to destination currency (code + English name).
 * @property convertedText converted amount in display form, or [ExchangeFormatting.UNAVAILABLE].
 * @property conversionAvailable true when [convertedText] is a real value.
 * @property unitRateText e.g. "1 EUR = 1.1127 USD", null without rates.
 * @property inverseRateText e.g. "1 USD = 0.898715 EUR", null without rates or for equal codes.
 * @property pairRateDate provider date (`yyyy-MM-dd`) of the selected pair's rate when it is older
 *   than the newest rate date shown in the status block; null otherwise.
 * @property status content of the status block above the keypad.
 * @property refreshing a network refresh (automatic or manual) is running.
 * @property currencies picker entries, common currencies pinned first; empty without rates.
 */
@Immutable
data class ExchangeUiState(
    val loaded: Boolean,
    val amountText: String,
    val from: Currency,
    val to: Currency,
    val convertedText: String,
    val conversionAvailable: Boolean,
    val unitRateText: String?,
    val inverseRateText: String?,
    val pairRateDate: String?,
    val status: RateStatusInfo,
    val refreshing: Boolean,
    val currencies: List<Currency>,
) {
    /** Rates are cached, so currencies can be selected. */
    val ratesAvailable: Boolean get() = currencies.isNotEmpty()
}

/** Result of a manual refresh (pull-to-refresh or accessibility action), reported once. */
enum class RefreshOutcome { Updated, Failed }
