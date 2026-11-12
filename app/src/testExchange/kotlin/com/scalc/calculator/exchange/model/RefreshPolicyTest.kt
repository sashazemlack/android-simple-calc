package com.scalc.calculator.exchange.model

import com.scalc.calculator.exchange.model.RefreshPolicy.AUTO_REFRESH_INTERVAL_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPolicyTest {

    private val t0 = 1_794_000_000_000L
    private val hour = 60L * 60L * 1000L

    @Test
    fun intervalIsTwentyFourHours() {
        assertEquals(24 * hour, AUTO_REFRESH_INTERVAL_MS)
    }

    @Test
    fun dueWhenNeverAttempted() {
        assertTrue(RefreshPolicy.isAutoRefreshDue(t0, 0L, 0L))
    }

    @Test
    fun notDueWithinTwentyFourHoursOfTheLastAttemptOrSuccess() {
        assertFalse(RefreshPolicy.isAutoRefreshDue(t0 + hour, t0, 0L))
        assertFalse(RefreshPolicy.isAutoRefreshDue(t0 + 24 * hour - 1, t0, 0L))
        // A recent manual success also postpones the automatic refresh.
        assertFalse(RefreshPolicy.isAutoRefreshDue(t0 + 30 * hour, t0, t0 + 10 * hour))
    }

    @Test
    fun dueAfterExactlyTwentyFourHours() {
        assertTrue(RefreshPolicy.isAutoRefreshDue(t0 + 24 * hour, t0, t0))
        assertTrue(RefreshPolicy.isAutoRefreshDue(t0 + 48 * hour, t0, 0L))
    }

    @Test
    fun dueWhenTheClockMovedBackwards() {
        assertTrue(RefreshPolicy.isAutoRefreshDue(t0 - 1, t0, 0L))
        assertTrue(RefreshPolicy.isAutoRefreshDue(t0 - hour, 0L, t0))
    }

    @Test
    fun staleness() {
        val table = SampleRates.table(fetchedAtMillis = t0)
        assertFalse(RefreshPolicy.isStale(t0, table))
        assertFalse(RefreshPolicy.isStale(t0 + 24 * hour - 1, table))
        assertTrue(RefreshPolicy.isStale(t0 + 24 * hour, table))
        assertTrue(RefreshPolicy.isStale(t0 - 1, table))
    }
}
