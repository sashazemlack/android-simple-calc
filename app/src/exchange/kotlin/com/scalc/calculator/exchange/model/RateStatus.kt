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
 * Language-independent content of the status block, derived from an [ExchangeState] and the
 * clock by [RateStatusFormatter.describe]. It holds no text, so it can live in a ViewModel that
 * survives a change of the system language; [RateStatusFormatter.render] turns it into the
 * [RateStatus] texts with the current resources.
 */
@Immutable
data class RateStatusInfo(
    val kind: StatusKind,
    /** The cache has not been read yet ("Loading…" rather than "Refreshing…"). */
    val loading: Boolean = false,
    /** Download time of the shown rates; null while no rates are available. */
    val fetchedAtMillis: Long? = null,
    /** Newest provider date of the shown rates (`yyyy-MM-dd`); null while no rates are available. */
    val rateDate: String? = null,
    /** Source of the shown rates (selects the attribution); null while no rates are available. */
    val source: RateSourceKind? = null,
    /** Why rates are unavailable ([StatusKind.Unavailable] only); null before any attempt. */
    val error: ExchangeError? = null,
    /** Rates are shown, but the latest refresh attempt failed. */
    val updateFailed: Boolean = false,
)

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
 * Text templates used by [RateStatusFormatter], always read from the current string resources
 * (`strings_exchange.xml`). Templates with `%1$s` receive one already formatted date or time;
 * [joined] receives two texts.
 */
data class RateStatusStrings(
    val loading: String,
    val refreshing: String,
    val fresh: String,
    val cached: String,
    val stale: String,
    val unavailable: String,
    val lastUpdated: String,
    /** Newest date of blended rates. */
    val rateDate: String,
    /** Date of ECB reference rates (ECB fallback and caches of earlier versions). */
    val rateDateEcb: String,
    val updateFailed: String,
    val errorNoConnection: String,
    val errorServer: String,
    val errorInvalidData: String,
    val sourceFrankfurterBlended: String,
    val sourceFrankfurterEcb: String,
    val sourceEcb: String,
    /** Joins two parts of the detail line, e.g. "%1$s · %2$s". */
    val joined: String,
)

/**
 * Supplies the locale's own date patterns. On Android this is
 * `android.text.format.DateFormat.getBestDateTimePattern`; it is injectable because that API only
 * returns defaults in JVM unit tests. Returning null selects the fixed fallback pattern.
 */
fun interface DatePatternProvider {
    fun bestPattern(locale: Locale, skeleton: String): String?

    companion object {
        /** Always uses the fixed fallback patterns of [RateStatusFormatter]. */
        val Fallback: DatePatternProvider = DatePatternProvider { _, _ -> null }
    }
}

/**
 * Pure formatter for the "last updated" block. It never mixes up the two kinds of timestamps of a
 * [RateTable]: the download time ([RateTable.fetchedAtMillis], in the device time zone) is the
 * "Last updated" value, while the provider's effective date ([RateTable.rateDate], a calendar date
 * without time zone) is only ever shown as "… rates of …".
 */
object RateStatusFormatter {

    /** Skeleton of the download time: day, abbreviated month, year, 24 h time. */
    const val DOWNLOAD_TIME_SKELETON = "dMMMyyyyHHmm"

    /** Skeleton of a provider date: day, abbreviated month, year. */
    const val RATE_DATE_SKELETON = "dMMMyyyy"

    /** Used when [DatePatternProvider] has no pattern for [DOWNLOAD_TIME_SKELETON]. */
    const val DOWNLOAD_TIME_PATTERN = "d MMM yyyy, HH:mm"

    /** Used when [DatePatternProvider] has no pattern for [RATE_DATE_SKELETON]. */
    const val RATE_DATE_PATTERN = "d MMM yyyy"

    private const val PROVIDER_DATE_PATTERN = "yyyy-MM-dd"
    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    /** What the status block has to say about [state] at [now]; contains no text. */
    fun describe(state: ExchangeState, now: Long): RateStatusInfo {
        if (!state.loaded) return RateStatusInfo(StatusKind.Refreshing, loading = true)
        val table = state.table
            ?: return if (state.refreshing) {
                RateStatusInfo(StatusKind.Refreshing)
            } else {
                RateStatusInfo(StatusKind.Unavailable, error = state.lastError)
            }
        val kind = when {
            state.refreshing -> StatusKind.Refreshing
            RefreshPolicy.isStale(now, table) -> StatusKind.Stale
            state.lastAttemptFailed -> StatusKind.Cached
            else -> StatusKind.Fresh
        }
        return RateStatusInfo(
            kind = kind,
            fetchedAtMillis = table.fetchedAtMillis,
            rateDate = table.rateDate,
            source = table.source,
            updateFailed = kind != StatusKind.Refreshing && state.lastAttemptFailed,
        )
    }

    /** Texts of [info] in [locale] with the current [strings]. */
    fun render(
        info: RateStatusInfo,
        strings: RateStatusStrings,
        locale: Locale,
        timeZone: TimeZone = TimeZone.getDefault(),
        patterns: DatePatternProvider = DatePatternProvider.Fallback,
    ): RateStatus {
        if (info.loading) return RateStatus(StatusKind.Refreshing, strings.loading)
        val fetchedAt = info.fetchedAtMillis
            ?: return if (info.kind == StatusKind.Unavailable) {
                RateStatus(StatusKind.Unavailable, strings.unavailable, info.error?.let { errorText(it, strings) })
            } else {
                RateStatus(StatusKind.Refreshing, strings.refreshing)
            }

        val updated = formatDownloadTime(fetchedAt, timeZone, locale, patterns)
        val attribution = info.source?.let { attributionOf(it, strings) }
        if (info.kind == StatusKind.Refreshing) {
            return RateStatus(info.kind, strings.refreshing, String.format(locale, strings.lastUpdated, updated), attribution)
        }
        val ratesOf = info.rateDate?.let { date ->
            val template = if (info.source == RateSourceKind.FRANKFURTER_BLENDED) strings.rateDate else strings.rateDateEcb
            String.format(locale, template, formatRateDate(date, locale, patterns))
        }
        val detail = if (info.updateFailed) join(ratesOf, strings.updateFailed, strings, locale) else ratesOf
        val template = when (info.kind) {
            StatusKind.Stale -> strings.stale
            StatusKind.Cached -> strings.cached
            else -> strings.fresh
        }
        return RateStatus(info.kind, String.format(locale, template, updated), detail, attribution)
    }

    /** [describe] and [render] in one step. */
    fun format(
        state: ExchangeState,
        now: Long,
        strings: RateStatusStrings,
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault(),
        patterns: DatePatternProvider = DatePatternProvider.Fallback,
    ): RateStatus = render(describe(state, now), strings, locale, timeZone, patterns)

    /** The data attribution required for rates from [source]. */
    fun attributionOf(source: RateSourceKind, strings: RateStatusStrings): String = when (source) {
        RateSourceKind.FRANKFURTER_BLENDED -> strings.sourceFrankfurterBlended
        RateSourceKind.FRANKFURTER_ECB -> strings.sourceFrankfurterEcb
        RateSourceKind.ECB_DIRECT -> strings.sourceEcb
    }

    /** Download time such as "11 Nov 2026, 09:15" (English fallback pattern) in [timeZone]. */
    fun formatDownloadTime(
        millis: Long,
        timeZone: TimeZone,
        locale: Locale,
        patterns: DatePatternProvider = DatePatternProvider.Fallback,
    ): String {
        val format = dateFormat(patterns, locale, DOWNLOAD_TIME_SKELETON, DOWNLOAD_TIME_PATTERN)
        return format.apply { this.timeZone = timeZone }.format(Date(millis))
    }

    /**
     * Provider date "2026-11-10" as "10 Nov 2026" (English fallback pattern). Parsed and formatted
     * in UTC so the calendar date never shifts with the device time zone; an unparseable value is
     * returned unchanged.
     */
    fun formatRateDate(
        rateDate: String,
        locale: Locale,
        patterns: DatePatternProvider = DatePatternProvider.Fallback,
    ): String {
        val parser = SimpleDateFormat(PROVIDER_DATE_PATTERN, Locale.US).apply {
            timeZone = UTC
            isLenient = false
        }
        val date = try {
            parser.parse(rateDate)
        } catch (ignored: ParseException) {
            null
        } ?: return rateDate
        return dateFormat(patterns, locale, RATE_DATE_SKELETON, RATE_DATE_PATTERN).apply { timeZone = UTC }.format(date)
    }

    /** The locale's pattern for [skeleton], or [fallback] when there is none or it is unusable. */
    private fun dateFormat(patterns: DatePatternProvider, locale: Locale, skeleton: String, fallback: String): SimpleDateFormat {
        val pattern = patterns.bestPattern(locale, skeleton)?.takeIf { it.isNotBlank() }
        if (pattern != null) {
            try {
                return SimpleDateFormat(pattern, locale)
            } catch (ignored: IllegalArgumentException) {
                // Not understood by this SimpleDateFormat: use the fallback.
            }
        }
        return SimpleDateFormat(fallback, locale)
    }

    private fun join(first: String?, second: String, strings: RateStatusStrings, locale: Locale): String =
        if (first == null) second else String.format(locale, strings.joined, first, second)

    private fun errorText(error: ExchangeError, strings: RateStatusStrings): String = when (error) {
        ExchangeError.NoConnection -> strings.errorNoConnection
        ExchangeError.Server -> strings.errorServer
        ExchangeError.InvalidData -> strings.errorInvalidData
    }
}
