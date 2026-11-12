package com.scalc.calculator.exchange.data

import androidx.datastore.core.DataStore
import com.scalc.calculator.exchange.model.CurrencySelection
import com.scalc.calculator.exchange.model.ExchangeError
import com.scalc.calculator.exchange.model.ExchangeState
import com.scalc.calculator.exchange.model.RefreshPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/**
 * Single source of truth for exchange rates, refresh scheduling and the selected currency pair.
 *
 * - The persisted [ExchangeCache] is read once on creation; [ExchangeState.loaded] turns true
 *   afterwards. All later changes go through this class (the only writer of the file), which
 *   keeps an in-memory copy and writes every change through to [cache].
 * - Automatic refreshes ([onScreenActive]) follow [RefreshPolicy]: at most one attempt per 24 h,
 *   recorded *before* the request so that failures and restarts cannot cause request bursts.
 * - Manual refreshes ([refreshNow]) bypass the policy.
 * - Refreshes are single-flight: while one runs, further requests are ignored.
 * - A failed refresh never touches the cached table; it only sets
 *   [ExchangeState.lastAttemptFailed] and [ExchangeState.lastError].
 *
 * Network and disk work runs in [scope] (application lifetime) and never on the caller's thread.
 * The typed amount is never passed to this class.
 */
class ExchangeRepository(
    private val cache: DataStore<ExchangeCache>,
    private val client: RatesClient,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(ExchangeState())

    /** Current rates, refresh status and selection. */
    val state: StateFlow<ExchangeState> = mutableState.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()

    /** Serializes cache mutations so memory, [state] and disk always see them in the same order. */
    private val cacheMutex = Mutex()

    /** Makes the automatic-refresh "is it due? then record the attempt" step atomic. */
    private val scheduleMutex = Mutex()

    private val refreshInFlight = AtomicBoolean(false)

    /** In-memory copy of the persisted cache; replaced only while holding [cacheMutex]. */
    @Volatile
    private var current = ExchangeCache()

    init {
        scope.launch {
            val stored = readStoredCache()
            cacheMutex.withLock {
                current = stored
                mutableState.update { it.withCache(stored).copy(loaded = true) }
            }
            loaded.complete(Unit)
        }
    }

    /**
     * Call when the Exchange screen opens or becomes visible again (tab selected, app back in the
     * foreground). After the cache is loaded, starts an automatic refresh only if
     * [RefreshPolicy.isAutoRefreshDue]; the attempt time is persisted first. Otherwise the cached
     * rates are used and nothing is requested.
     */
    fun onScreenActive() {
        scope.launch {
            loaded.await()
            val due = scheduleMutex.withLock {
                val now = clock()
                val snapshot = current
                val isDue = !refreshInFlight.get() &&
                    RefreshPolicy.isAutoRefreshDue(now, snapshot.lastAutoAttemptMillis, snapshot.lastSuccessMillis)
                if (isDue) {
                    // Timestamps from the future (clock moved backwards) are clamped, otherwise every
                    // activation would count as due until the clock catches up.
                    mutate { it.copy(lastAutoAttemptMillis = now, lastSuccessMillis = minOf(it.lastSuccessMillis, now)) }
                }
                isDue
            }
            if (due) startRefresh()
        }
    }

    /** Manual pull-to-refresh: downloads immediately, regardless of the 24 h policy. */
    fun refreshNow() {
        startRefresh()
    }

    /**
     * Stores a new currency pair. Codes are normalized (trimmed, upper case); a code that is not
     * part of the cached rates (or malformed while there are none) is ignored and keeps its
     * previous value.
     */
    suspend fun setSelection(from: String, to: String) {
        mutate { cached ->
            cached.copy(
                selectedFrom = CurrencySelection.normalize(from, cached.table) ?: cached.selectedFrom,
                selectedTo = CurrencySelection.normalize(to, cached.table) ?: cached.selectedTo,
            )
        }
    }

    /** Exchanges source and destination currency. */
    suspend fun swap() {
        mutate { it.copy(selectedFrom = it.selectedTo, selectedTo = it.selectedFrom) }
    }

    private fun startRefresh() {
        if (!refreshInFlight.compareAndSet(false, true)) return
        mutableState.update { it.copy(refreshing = true) }
        scope.launch {
            loaded.await()
            refresh()
        }.invokeOnCompletion {
            // Also covers cancellation; reset the flag last so a new refresh cannot be overwritten.
            mutableState.update { if (it.refreshing) it.copy(refreshing = false) else it }
            refreshInFlight.set(false)
        }
    }

    private suspend fun refresh() {
        val snapshot = current
        val etag = snapshot.etag?.takeIf { snapshot.table != null }
        val result = try {
            client.fetch(etag)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            markFailed(e.toExchangeError())
            return
        }
        val now = clock()
        when (result) {
            is FetchResult.Downloaded -> mutate(::markSucceeded) { cached ->
                val table = result.table.copy(fetchedAtMillis = now)
                val (from, to) = CurrencySelection.sanitize(cached.selectedFrom, cached.selectedTo, table)
                cached.copy(
                    table = table,
                    etag = result.etag,
                    lastSuccessMillis = now,
                    lastAutoAttemptMillis = minOf(cached.lastAutoAttemptMillis, now),
                    selectedFrom = from,
                    selectedTo = to,
                )
            }
            FetchResult.NotModified -> {
                if (snapshot.table == null) {
                    markFailed(ExchangeError.InvalidData)
                    return
                }
                mutate(::markSucceeded) { cached ->
                    cached.copy(
                        table = cached.table?.copy(fetchedAtMillis = now),
                        lastSuccessMillis = now,
                        lastAutoAttemptMillis = minOf(cached.lastAutoAttemptMillis, now),
                    )
                }
            }
        }
    }

    private fun markSucceeded(state: ExchangeState): ExchangeState =
        state.copy(refreshing = false, lastAttemptFailed = false, lastError = null, refreshedThisSession = true)

    private fun markFailed(error: ExchangeError) {
        mutableState.update { it.copy(refreshing = false, lastAttemptFailed = true, lastError = error) }
    }

    /**
     * Applies [transform] to the cache, publishes the result (together with the session-only
     * changes of [session]) and writes it to disk. A failed disk write keeps the in-memory value,
     * so the app keeps working; the next successful write persists everything.
     */
    private suspend fun mutate(
        session: (ExchangeState) -> ExchangeState = { it },
        transform: (ExchangeCache) -> ExchangeCache,
    ) {
        loaded.await()
        cacheMutex.withLock {
            val previous = current
            val next = transform(previous)
            current = next
            mutableState.update { session(it.withCache(next)) }
            if (next != previous) {
                try {
                    cache.updateData { next }
                } catch (e: IOException) {
                    // Disk full or similar: keep the in-memory state.
                }
            }
        }
    }

    private suspend fun readStoredCache(): ExchangeCache {
        val stored = try {
            cache.data.first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Unreadable storage must not break the feature: start like a fresh install.
            ExchangeCache()
        }
        val (from, to) = CurrencySelection.sanitize(stored.selectedFrom, stored.selectedTo, stored.table)
        return stored.copy(selectedFrom = from, selectedTo = to)
    }

    private fun ExchangeState.withCache(cached: ExchangeCache): ExchangeState = copy(
        table = cached.table,
        lastSuccessMillis = cached.lastSuccessMillis,
        lastAutoAttemptMillis = cached.lastAutoAttemptMillis,
        selectedFrom = cached.selectedFrom,
        selectedTo = cached.selectedTo,
    )
}

/** Maps a [RatesClient] failure to the user-facing error category. */
internal fun Throwable.toExchangeError(): ExchangeError = when (this) {
    is RatesServerException -> ExchangeError.Server
    is RatesFormatException -> ExchangeError.InvalidData
    is IOException, is SecurityException -> ExchangeError.NoConnection
    else -> ExchangeError.InvalidData
}
