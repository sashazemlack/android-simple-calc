package com.scalc.calculator.exchange.model

/**
 * Daily update policy. Automatic refreshes happen at most once per [AUTO_REFRESH_INTERVAL_MS];
 * manual pull-to-refresh bypasses this policy entirely.
 *
 * A timestamp later than "now" means the device clock was moved backwards. Such timestamps are
 * not trusted: the data counts as due/stale so the app cannot get stuck on old rates until the
 * clock catches up again.
 */
object RefreshPolicy {

    const val AUTO_REFRESH_INTERVAL_MS: Long = 24L * 60L * 60L * 1000L

    /**
     * True when an automatic refresh should run: nothing was ever attempted or downloaded, the
     * later of [lastAutoAttemptMillis] and [lastSuccessMillis] is at least 24 h old, or it lies in
     * the future (clock moved backwards). A failed automatic attempt therefore also blocks further
     * automatic attempts for 24 h, while a recent manual success postpones the next automatic one.
     */
    fun isAutoRefreshDue(now: Long, lastAutoAttemptMillis: Long, lastSuccessMillis: Long): Boolean {
        val last = maxOf(lastAutoAttemptMillis, lastSuccessMillis)
        if (last <= 0L) return true
        return isOutdated(now, last)
    }

    /** True when [table] was downloaded (or re-confirmed) 24 h ago or earlier, or "in the future". */
    fun isStale(now: Long, table: RateTable): Boolean = isOutdated(now, table.fetchedAtMillis)

    private fun isOutdated(now: Long, timestamp: Long): Boolean =
        now < timestamp || now - timestamp >= AUTO_REFRESH_INTERVAL_MS
}
