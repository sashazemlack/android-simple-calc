package com.scalc.calculator.exchange.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class RateStatusFormatterTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val hour = 60L * 60L * 1000L
    private val english = TestStrings.ENGLISH

    private fun utcMillis(year: Int, month: Int, day: Int, hourOfDay: Int, minute: Int): Long =
        Calendar.getInstance(utc, Locale.ENGLISH).apply {
            clear()
            set(year, month - 1, day, hourOfDay, minute, 0)
        }.timeInMillis

    private fun format(state: ExchangeState, now: Long, timeZone: TimeZone = utc) =
        RateStatusFormatter.format(state, now, english, timeZone, Locale.ENGLISH)

    private fun loaded(table: RateTable?, failed: ExchangeError? = null, refreshing: Boolean = false) = ExchangeState(
        loaded = true,
        table = table,
        refreshing = refreshing,
        lastAttemptFailed = failed != null,
        lastError = failed,
    )

    @Test
    fun englishTextsComeFromTheStringResources() {
        assertEquals("Rates last updated: %1\$s", english.fresh)
        assertEquals("Exchange rates of %1\$s", english.rateDate)
        assertEquals("ECB reference rates of %1\$s", english.rateDateEcb)
        assertEquals("Source: central bank and other official rates blended by frankfurter.dev", english.sourceFrankfurterBlended)
        assertEquals("Source: European Central Bank", english.sourceEcb)
        assertEquals("%1\$s · %2\$s", english.joined)
        assertEquals("%1\$s · cross rates calculated on device", TestStrings.string("exch_attribution"))
        assertEquals("Couldn't refresh exchange rates", TestStrings.string("exch_feedback_failed"))
    }

    @Test
    fun freshShowsDownloadTimeAndNewestRateDateSeparately() {
        val fetched = utcMillis(2026, 11, 12, 9, 15)
        val table = SampleRates.table(fetchedAtMillis = fetched, rateDate = "2026-11-12", rateDates = mapOf("PLN" to "2026-11-11"))
        val status = format(loaded(table), now = fetched + hour)
        assertEquals(StatusKind.Fresh, status.kind)
        assertEquals("Rates last updated: 12 Nov 2026, 09:15", status.text)
        // The newest rate date, even though PLN is older.
        assertEquals("Exchange rates of 12 Nov 2026", status.detail)
        assertEquals("Source: central bank and other official rates blended by frankfurter.dev", status.attribution)
    }

    @Test
    fun cachedWhenLatestAttemptFailedButRatesAreRecent() {
        val fetched = utcMillis(2026, 11, 10, 18, 30)
        val table = SampleRates.table(fetchedAtMillis = fetched, rateDate = "2026-11-10")
        val status = format(loaded(table, ExchangeError.NoConnection), now = utcMillis(2026, 11, 11, 9, 15))
        assertEquals(StatusKind.Cached, status.kind)
        assertEquals("Using cached rates · Last updated: 10 Nov 2026, 18:30", status.text)
        // "Using cached rates" alone does not say why: the latest refresh attempt failed.
        assertEquals("Exchange rates of 10 Nov 2026 · Update failed. Swipe down to retry.", status.detail)
    }

    @Test
    fun staleAfterTwentyFourHours() {
        val fetched = utcMillis(2026, 11, 9, 18, 30)
        val table = SampleRates.table(fetchedAtMillis = fetched, rateDate = "2026-11-09")

        val stale = format(loaded(table), now = utcMillis(2026, 11, 11, 9, 15))
        assertEquals(StatusKind.Stale, stale.kind)
        assertEquals("Using cached rates (may be outdated) · Last updated: 9 Nov 2026, 18:30", stale.text)
        assertEquals("Exchange rates of 9 Nov 2026", stale.detail)

        val failed = format(loaded(table, ExchangeError.Server), now = utcMillis(2026, 11, 11, 9, 15))
        assertEquals(StatusKind.Stale, failed.kind)
        assertEquals("Exchange rates of 9 Nov 2026 · Update failed. Swipe down to retry.", failed.detail)
    }

    @Test
    fun stalenessBoundaryAndBackwardsClock() {
        val fetched = utcMillis(2026, 11, 10, 12, 0)
        val table = SampleRates.table(fetchedAtMillis = fetched)
        assertEquals(StatusKind.Fresh, format(loaded(table), now = fetched + 24 * hour - 1).kind)
        assertEquals(StatusKind.Stale, format(loaded(table), now = fetched + 24 * hour).kind)
        assertEquals(StatusKind.Stale, format(loaded(table), now = fetched - hour).kind)
    }

    @Test
    fun refreshingKeepsShowingTheCachedDownloadTime() {
        val fetched = utcMillis(2026, 11, 10, 18, 30)
        val table = SampleRates.table(fetchedAtMillis = fetched)
        val withTable = format(loaded(table, refreshing = true), now = fetched + hour)
        assertEquals(StatusKind.Refreshing, withTable.kind)
        assertEquals("Refreshing exchange rates...", withTable.text)
        assertEquals("Last updated: 10 Nov 2026, 18:30", withTable.detail)

        val withoutTable = format(loaded(null, refreshing = true), now = fetched)
        assertEquals(StatusKind.Refreshing, withoutTable.kind)
        assertEquals("Refreshing exchange rates...", withoutTable.text)
        assertNull(withoutTable.detail)
        assertNull(withoutTable.attribution)
    }

    @Test
    fun loadingBeforeTheCacheIsRead() {
        val status = format(ExchangeState(loaded = false, refreshing = true), now = 0L)
        assertEquals(StatusKind.Refreshing, status.kind)
        assertEquals("Loading exchange rates...", status.text)
    }

    @Test
    fun unavailableWithoutRates() {
        val noAttempt = format(loaded(null), now = 0L)
        assertEquals(StatusKind.Unavailable, noAttempt.kind)
        assertEquals("Exchange rates unavailable. Swipe down to retry.", noAttempt.text)
        assertNull(noAttempt.detail)

        assertEquals("No internet connection.", format(loaded(null, ExchangeError.NoConnection), 0L).detail)
        assertEquals(
            "The exchange-rate service is not available right now.",
            format(loaded(null, ExchangeError.Server), 0L).detail,
        )
        assertEquals(
            "The exchange-rate service returned invalid data.",
            format(loaded(null, ExchangeError.InvalidData), 0L).detail,
        )
    }

    @Test
    fun downloadTimeFollowsTheTimeZoneButTheRateDateDoesNot() {
        val fetched = utcMillis(2026, 11, 11, 9, 15)
        val table = SampleRates.table(fetchedAtMillis = fetched, rateDate = "2026-11-10")

        val prague = format(loaded(table), fetched + hour, TimeZone.getTimeZone("Europe/Prague"))
        assertEquals("Rates last updated: 11 Nov 2026, 10:15", prague.text)
        assertEquals("Exchange rates of 10 Nov 2026", prague.detail)

        val losAngeles = format(loaded(table), fetched + hour, TimeZone.getTimeZone("America/Los_Angeles"))
        assertEquals("Rates last updated: 11 Nov 2026, 01:15", losAngeles.text)
        assertEquals("Exchange rates of 10 Nov 2026", losAngeles.detail)
    }

    @Test
    fun ecbSourcesHaveTheirOwnAttributionAndRateDateText() {
        val fetched = utcMillis(2026, 11, 11, 9, 15)
        val direct = format(loaded(SampleRates.table(fetchedAtMillis = fetched, source = RateSourceKind.ECB_DIRECT)), fetched)
        assertEquals("Source: European Central Bank", direct.attribution)
        assertEquals("ECB reference rates of 10 Nov 2026", direct.detail)

        // Tables cached by earlier versions until the next refresh.
        val legacy = format(loaded(SampleRates.table(fetchedAtMillis = fetched, source = RateSourceKind.FRANKFURTER_ECB)), fetched)
        assertEquals("Source: European Central Bank via frankfurter.dev", legacy.attribution)
        assertEquals("ECB reference rates of 10 Nov 2026", legacy.detail)
    }

    @Test
    fun describeContainsNoText() {
        val fetched = utcMillis(2026, 11, 11, 9, 15)
        val table = SampleRates.table(fetchedAtMillis = fetched, rateDate = "2026-11-10")
        assertEquals(
            RateStatusInfo(
                kind = StatusKind.Cached,
                fetchedAtMillis = fetched,
                rateDate = "2026-11-10",
                source = RateSourceKind.FRANKFURTER_BLENDED,
                updateFailed = true,
            ),
            RateStatusFormatter.describe(loaded(table, ExchangeError.Server), fetched + hour),
        )
        assertEquals(RateStatusInfo(StatusKind.Refreshing, loading = true), RateStatusFormatter.describe(ExchangeState(), 0L))
        assertEquals(
            RateStatusInfo(StatusKind.Unavailable, error = ExchangeError.NoConnection),
            RateStatusFormatter.describe(loaded(null, ExchangeError.NoConnection), 0L),
        )
    }

    @Test
    fun theSameStatusRendersInAnotherLanguage() {
        val fetched = utcMillis(2026, 11, 11, 9, 15)
        val info = RateStatusFormatter.describe(
            loaded(SampleRates.table(fetchedAtMillis = fetched, rateDate = "2026-11-10"), ExchangeError.NoConnection),
            fetched + hour,
        )
        val inEnglish = RateStatusFormatter.render(info, english, Locale.ENGLISH, utc)
        val german = RateStatusFormatter.render(info, TestStrings.GERMAN, Locale.GERMAN, utc)

        assertEquals("Using cached rates · Last updated: 11 Nov 2026, 09:15", inEnglish.text)
        assertEquals("Gespeicherte Kurse · Zuletzt aktualisiert: 11 Nov. 2026, 09:15", german.text)
        assertEquals("Wechselkurse vom 10 Nov. 2026 · Aktualisierung fehlgeschlagen.", german.detail)
        assertEquals(TestStrings.GERMAN.sourceFrankfurterBlended, german.attribution)
        assertEquals(
            "Der Wechselkursdienst ist zurzeit nicht erreichbar.",
            RateStatusFormatter.render(RateStatusInfo(StatusKind.Unavailable, error = ExchangeError.Server), TestStrings.GERMAN, Locale.GERMAN).detail,
        )
    }

    @Test
    fun datesUseTheLocalesOwnPatternFromTheProvider() {
        val requested = mutableListOf<Pair<Locale, String>>()
        val patterns = DatePatternProvider { locale, skeleton ->
            requested += locale to skeleton
            when (skeleton) {
                RateStatusFormatter.DOWNLOAD_TIME_SKELETON -> "d. MMM yyyy, HH:mm"
                RateStatusFormatter.RATE_DATE_SKELETON -> "d. MMMM yyyy"
                else -> null
            }
        }
        val fetched = utcMillis(2026, 11, 11, 9, 15)
        val info = RateStatusFormatter.describe(loaded(SampleRates.table(fetchedAtMillis = fetched, rateDate = "2026-11-10")), fetched)
        val status = RateStatusFormatter.render(info, TestStrings.GERMAN, Locale.GERMAN, utc, patterns)

        assertEquals("Kurse zuletzt aktualisiert: 11. Nov. 2026, 09:15", status.text)
        assertEquals("Wechselkurse vom 10. November 2026", status.detail)
        assertEquals(
            listOf(Locale.GERMAN to "dMMMyyyyHHmm", Locale.GERMAN to "dMMMyyyy"),
            requested,
        )
        // Japanese: the locale's pattern with its own symbols.
        assertEquals(
            "2026年11月10日",
            RateStatusFormatter.formatRateDate("2026-11-10", Locale.JAPANESE) { _, _ -> "y年M月d日" },
        )
    }

    @Test
    fun missingOrUnusablePatternsFallBack() {
        val fetched = utcMillis(2026, 11, 11, 9, 15)
        listOf<DatePatternProvider>(
            DatePatternProvider.Fallback,
            DatePatternProvider { _, _ -> "" },
            // A pattern letter java.text.SimpleDateFormat rejects.
            DatePatternProvider { _, _ -> "d MMM yyyy bbb" },
        ).forEach { patterns ->
            assertEquals("11 Nov 2026, 09:15", RateStatusFormatter.formatDownloadTime(fetched, utc, Locale.ENGLISH, patterns))
            assertEquals("10 Nov 2026", RateStatusFormatter.formatRateDate("2026-11-10", Locale.ENGLISH, patterns))
        }
        assertEquals(RateStatusFormatter.DOWNLOAD_TIME_PATTERN, "d MMM yyyy, HH:mm")
        assertEquals(RateStatusFormatter.RATE_DATE_PATTERN, "d MMM yyyy")
    }

    @Test
    fun rateDateFormatting() {
        assertEquals("1 Jan 2026", RateStatusFormatter.formatRateDate("2026-01-01", Locale.ENGLISH))
        // Unparseable provider dates are shown verbatim rather than guessed.
        assertEquals("garbage", RateStatusFormatter.formatRateDate("garbage", Locale.ENGLISH))
        assertTrue(RateStatusFormatter.formatRateDate("2026-11-12", Locale.GERMAN).startsWith("12 Nov"))
    }
}
