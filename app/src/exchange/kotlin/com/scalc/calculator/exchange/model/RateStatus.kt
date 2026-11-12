package com.scalc.calculator.exchange.model

import androidx.compose.runtime.Immutable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Category of the rate status shown above the keypad; lets the UI pick a color or icon. */
enum class StatusKind {
    /** Rates are being downloaded (or the cache is still being read). */
    Refreshing,

    /** Rates downloaded less than 24 h ago and the latest attempt did not fail. */
    Fresh,

    /** Rates less than 24 h old, but the latest refresh attempt failed (e.g. offline). */
    Cached,

    /** Rates downloaded 24 h ago or earlier: may be outdated. */
    Stale,

    /** No rates at all. */
    Unavailable,
}

/**
 * Status block content: a primary [text] line, an optional secondary [detail] line and the data
 * [attribution] required by the provider (null when no rates are shown).
 */
@Immutable
data class RateStatus(
    val kind: StatusKind,
    val text: String,
    val detail: String? = null,
    val attribution: String? = null,
)

/**
 * Text templates used by [RateStatusFormatter]. Defaults are English; the UI may build an instance
 * from string resources. Templates with `%1$s` receive one already formatted date or time.
 */
data class RateStatusStrings(
    val loading: String = "Loading exchange rates...",
    val refreshing: String = "Refreshing exchange rates...",
    val fresh: String = "Rates last updated: %1\$s",
    val cached: String = "Using cached rates · Last updated: %1\$s",
    val stale: String = "Using cached rates (may be outdated) · Last updated: %1\$s",
    val unavailable: String = "Exchange rates unavailable. Swipe down to retry.",
    val lastUpdated: String = "Last updated: %1\$s",
    val rateDate: String = "ECB reference rates of %1\$s",
    val updateFailed: String = "Update failed. Swipe down to retry.",
    val errorNoConnection: String = "No internet connection.",
    val errorServer: String = "The exchange-rate service is not available right now.",
    val errorInvalidData: String = "The exchange-rate service returned invalid data.",
    val sourceFrankfurter: String = "Source: European Central Bank (via frankfurter.dev)",
    val sourceEcb: String = "Source: European Central Bank",
    val separator: String = " · ",
)

/**
 * Pure formatter for the "last updated" block. It never mixes up the two timestamps of a
 * [RateTable]: the download time ([RateTable.fetchedAtMillis], shown as [DOWNLOAD_TIME_PATTERN] in
 * the device time zone) is the "Last updated" value, while the provider's effective date
 * ([RateTable.rateDate], a calendar date without time zone) is only ever shown as
 * "ECB reference rates of ...".
 */
object RateStatusFormatter {

    const val DOWNLOAD_TIME_PATTERN = "d MMM yyyy, HH:mm"
    const val RATE_DATE_PATTERN = "d MMM yyyy"
    private const val PROVIDER_DATE_PATTERN = "yyyy-MM-dd"
    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    fun format(
        state: ExchangeState,
        now: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault(),
        strings: RateStatusStrings = RateStatusStrings(),
    ): RateStatus {
        if (!state.loaded) return RateStatus(StatusKind.Refreshing, strings.loading)
        val table = state.table
        if (table == null) {
            return if (state.refreshing) {
                RateStatus(StatusKind.Refreshing, strings.refreshing)
            } else {
                RateStatus(StatusKind.Unavailable, strings.unavailable, state.lastError?.let { errorText(it, strings) })
            }
        }

        val updated = formatDownloadTime(table.fetchedAtMillis, timeZone, locale)
        val ratesOf = String.format(locale, strings.rateDate, formatRateDate(table.rateDate, locale))
        val attribution = when (table.source) {
            RateSourceKind.FRANKFURTER_ECB -> strings.sourceFrankfurter
            RateSourceKind.ECB_DIRECT -> strings.sourceEcb
        }
        return when {
            state.refreshing -> RateStatus(
                StatusKind.Refreshing,
                strings.refreshing,
                String.format(locale, strings.lastUpdated, updated),
                attribution,
            )
            RefreshPolicy.isStale(now, table) -> RateStatus(
                StatusKind.Stale,
                String.format(locale, strings.stale, updated),
                if (state.lastAttemptFailed) ratesOf + strings.separator + strings.updateFailed else ratesOf,
                attribution,
            )
            state.lastAttemptFailed -> RateStatus(
                StatusKind.Cached,
                String.format(locale, strings.cached, updated),
                ratesOf,
                attribution,
            )
            else -> RateStatus(StatusKind.Fresh, String.format(locale, strings.fresh, updated), ratesOf, attribution)
        }
    }

    /** Download time such as "11 Nov 2026, 09:15" in [timeZone]. */
    fun formatDownloadTime(millis: Long, timeZone: TimeZone, locale: Locale): String =
        SimpleDateFormat(DOWNLOAD_TIME_PATTERN, locale).apply { this.timeZone = timeZone }.format(Date(millis))

    /**
     * Provider date "2026-11-10" as "10 Nov 2026". Parsed and formatted in UTC so the calendar date
     * never shifts with the device time zone; an unparseable value is returned unchanged.
     */
    fun formatRateDate(rateDate: String, locale: Locale): String {
        val parser = SimpleDateFormat(PROVIDER_DATE_PATTERN, Locale.US).apply {
            timeZone = UTC
            isLenient = false
        }
        val date = try {
            parser.parse(rateDate)
        } catch (ignored: ParseException) {
            null
        } ?: return rateDate
        return SimpleDateFormat(RATE_DATE_PATTERN, locale).apply { timeZone = UTC }.format(date)
    }

    private fun errorText(error: ExchangeError, strings: RateStatusStrings): String = when (error) {
        ExchangeError.NoConnection -> strings.errorNoConnection
        ExchangeError.Server -> strings.errorServer
        ExchangeError.InvalidData -> strings.errorInvalidData
    }
}
