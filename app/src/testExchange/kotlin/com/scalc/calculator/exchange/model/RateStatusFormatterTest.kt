package com.scalc.calculator.exchange.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class RateStatusFormatterTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val hour = 60L * 60L * 1000L

    private fun utcMillis(year: Int, month: Int, day: Int, hourOfDay: Int, minute: Int): Long =
        Calendar.getInstance(utc, Locale.ENGLISH).apply {
            clear()
            set(year, month - 1, day, hourOfDay, minute, 0)
        }.timeInMillis

    private fun format(state: ExchangeState, now: Long, timeZone: TimeZone = utc) =
        RateStatusFormatter.format(state, now, timeZone, Locale.ENGLISH)

    private fun loaded(table: RateTable?, failed: ExchangeError? = null, refreshing: Boolean = false) = ExchangeState(
        loaded = true,
        table = table,
        refreshing = refreshing,
        lastAttemptFailed = failed != null,
        lastError = failed,
    )

    @Test
    fun freshShowsDownloadTimeAndRateDateSeparately() {
        val fetched = utcMillis(2026, 11, 11, 9, 15)
        val table = SampleRates.table(fetchedAtMillis = fetched, rateDate = "2026-11-10")
        val status = format(loaded(table), now = fetched + hour)
        assertEquals(StatusKind.Fresh, status.kind)
        assertEquals("Rates last updated: 11 Nov 2026, 09:15", status.text)
        assertEquals("ECB reference rates of 10 Nov 2026", status.detail)
        assertEquals("Source: European Central Bank (via frankfurter.dev)", status.attribution)
    }

    @Test
    fun cachedWhenLatestAttemptFailedButRatesAreRecent() {
        val fetched = utcMillis(2026, 11, 10, 18, 30)
        val table = SampleRates.table(fetchedAtMillis = fetched, rateDate = "2026-11-10")
        val status = format(loaded(table, ExchangeError.NoConnection), now = utcMillis(2026, 11, 11, 9, 15))
        assertEquals(StatusKind.Cached, status.kind)
        assertEquals("Using cached rates · Last updated: 10 Nov 2026, 18:30", status.text)
        assertEquals("ECB reference rates of 10 Nov 2026", status.detail)
    }

    @Test
    fun staleAfterTwentyFourHours() {
        val fetched = utcMillis(2026, 11, 9, 18, 30)
        val table = SampleRates.table(fetchedAtMillis = fetched, rateDate = "2026-11-09")

        val stale = format(loaded(table), now = utcMillis(2026, 11, 11, 9, 15))
        assertEquals(StatusKind.Stale, stale.kind)
        assertEquals("Using cached rates (may be outdated) · Last updated: 9 Nov 2026, 18:30", stale.text)
        assertEquals("ECB reference rates of 9 Nov 2026", stale.detail)

        val failed = format(loaded(table, ExchangeError.Server), now = utcMillis(2026, 11, 11, 9, 15))
        assertEquals(StatusKind.Stale, failed.kind)
        assertEquals("ECB reference rates of 9 Nov 2026 · Update failed. Swipe down to retry.", failed.detail)
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
        assertEquals("ECB reference rates of 10 Nov 2026", prague.detail)

        val losAngeles = format(loaded(table), fetched + hour, TimeZone.getTimeZone("America/Los_Angeles"))
        assertEquals("Rates last updated: 11 Nov 2026, 01:15", losAngeles.text)
        assertEquals("ECB reference rates of 10 Nov 2026", losAngeles.detail)
    }

    @Test
    fun ecbDirectSourceAttribution() {
        val fetched = utcMillis(2026, 11, 11, 9, 15)
        val table = SampleRates.table(fetchedAtMillis = fetched, source = RateSourceKind.ECB_DIRECT)
        assertEquals("Source: European Central Bank", format(loaded(table), fetched).attribution)
    }

    @Test
    fun customTemplatesAreUsed() {
        val fetched = utcMillis(2026, 11, 11, 9, 15)
        val table = SampleRates.table(fetchedAtMillis = fetched, rateDate = "2026-11-10")
        val strings = RateStatusStrings(fresh = "Updated %1\$s", rateDate = "Rates of %1\$s")
        val status = RateStatusFormatter.format(loaded(table), fetched, utc, Locale.ENGLISH, strings)
        assertEquals("Updated 11 Nov 2026, 09:15", status.text)
        assertEquals("Rates of 10 Nov 2026", status.detail)
    }

    @Test
    fun rateDateFormatting() {
        assertEquals("1 Jan 2026", RateStatusFormatter.formatRateDate("2026-01-01", Locale.ENGLISH))
        // Unparseable provider dates are shown verbatim rather than guessed.
        assertEquals("garbage", RateStatusFormatter.formatRateDate("garbage", Locale.ENGLISH))
    }
}
