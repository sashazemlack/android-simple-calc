package com.scalc.calculator.exchange.ui

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import com.scalc.calculator.R
import com.scalc.calculator.exchange.model.Currency
import com.scalc.calculator.exchange.model.CurrencyNames
import com.scalc.calculator.exchange.model.DatePatternProvider
import com.scalc.calculator.exchange.model.RateStatus
import com.scalc.calculator.exchange.model.RateStatusFormatter
import com.scalc.calculator.exchange.model.RateStatusStrings
import com.scalc.calculator.ui.components.PickerItem
import java.util.Locale
import java.util.TimeZone

/**
 * Status-block templates from the current string resources. Templates are read unformatted (they
 * still contain `%1$s`); [RateStatusFormatter] fills them in.
 */
internal fun rateStatusStrings(resources: Resources): RateStatusStrings = RateStatusStrings(
    loading = resources.getString(R.string.exch_status_loading),
    refreshing = resources.getString(R.string.exch_status_refreshing),
    fresh = resources.getString(R.string.exch_status_fresh),
    cached = resources.getString(R.string.exch_status_cached),
    stale = resources.getString(R.string.exch_status_stale),
    unavailable = resources.getString(R.string.exch_status_unavailable),
    lastUpdated = resources.getString(R.string.exch_status_last_updated),
    rateDate = resources.getString(R.string.exch_status_rate_date),
    rateDateEcb = resources.getString(R.string.exch_status_rate_date_ecb),
    updateFailed = resources.getString(R.string.exch_status_update_failed),
    errorNoConnection = resources.getString(R.string.exch_error_no_connection),
    errorServer = resources.getString(R.string.exch_error_server),
    errorInvalidData = resources.getString(R.string.exch_error_invalid_data),
    sourceFrankfurterBlended = resources.getString(R.string.exch_source_frankfurter_blended),
    sourceFrankfurterEcb = resources.getString(R.string.exch_source_frankfurter),
    sourceEcb = resources.getString(R.string.exch_source_ecb),
    joined = resources.getString(R.string.exch_status_joined),
)

/**
 * The locale's own date patterns from `android.text.format.DateFormat.getBestDateTimePattern`. In
 * JVM unit tests that API returns null, so the formatter's fixed fallback patterns apply.
 */
internal val AndroidDatePatterns = DatePatternProvider { locale, skeleton ->
    DateFormat.getBestDateTimePattern(locale, skeleton)
}

/** Everything language-dependent needed to turn an [ExchangeUiState] into texts. */
@Immutable
internal data class ExchangeLocalization(
    /** The system language (primary locale of the current configuration). */
    val locale: Locale,
    val strings: RateStatusStrings,
    val datePatterns: DatePatternProvider = DatePatternProvider.Fallback,
)

/** Language-dependent texts of the Exchange screen, see [localize]. */
@Immutable
internal data class LocalizedExchangeTexts(
    /** Display name of the source currency in the current language. */
    val fromName: String,
    /** Display name of the destination currency in the current language. */
    val toName: String,
    val status: RateStatus,
    /** Formatted date of the selected pair's rate, or null when it is not older than the newest rates. */
    val pairRateDate: String?,
)

/**
 * The current localization: re-created whenever the configuration (system language) or the
 * resources change, so texts never stay in the language the ViewModel was created with.
 */
@Composable
internal fun rememberExchangeLocalization(): ExchangeLocalization {
    val configuration = LocalConfiguration.current
    val resources = LocalResources.current
    return remember(configuration, resources) {
        ExchangeLocalization(
            locale = configuration.locales.get(0) ?: Locale.getDefault(),
            strings = rateStatusStrings(resources),
            datePatterns = AndroidDatePatterns,
        )
    }
}

/** Texts of this state in the language of [localization]; times are shown in [timeZone]. */
internal fun ExchangeUiState.localize(
    localization: ExchangeLocalization,
    timeZone: TimeZone = TimeZone.getDefault(),
): LocalizedExchangeTexts = LocalizedExchangeTexts(
    fromName = CurrencyNames.displayName(from, localization.locale),
    toName = CurrencyNames.displayName(to, localization.locale),
    status = RateStatusFormatter.render(status, localization.strings, localization.locale, timeZone, localization.datePatterns),
    pairRateDate = pairRateDate?.let { RateStatusFormatter.formatRateDate(it, localization.locale, localization.datePatterns) },
)

/**
 * Picker rows for [currencies]: the code as title, the name in [locale] as subtitle, and the
 * English name as an extra search keyword, so "dollar" finds USD in every language.
 */
internal fun currencyPickerItems(currencies: List<Currency>, locale: Locale): List<PickerItem> =
    currencies.map { currency ->
        val name = CurrencyNames.displayName(currency, locale)
        PickerItem(
            key = currency.code,
            title = currency.code,
            subtitle = name,
            keywords = listOf(currency.name).filter { it != name && it != currency.code },
        )
    }
