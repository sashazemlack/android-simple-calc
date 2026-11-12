package com.scalc.calculator.exchange.data

import androidx.datastore.core.DataStore
import com.scalc.calculator.exchange.model.ExchangeError
import com.scalc.calculator.exchange.model.RateTable
import com.scalc.calculator.exchange.model.SampleRates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Repository work runs on the [TestScope] itself (not backgroundScope, whose tasks
 * advanceUntilIdle does not drive), so every step is deterministic on virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeRepositoryTest {

    private val t0 = 1_794_000_000_000L
    private val minute = 60L * 1000L
    private val hour = 60L * minute
    private val day = 24L * hour

    private var now = t0
    private val store = InMemoryDataStore(ExchangeCache())
    private val client = FakeRatesClient()

    private fun TestScope.repository(
        cache: DataStore<ExchangeCache> = store,
        ratesClient: RatesClient = client,
    ) = ExchangeRepository(cache, ratesClient, this) { now }

    private fun TestScope.activateAt(repository: ExchangeRepository, time: Long) {
        now = time
        repository.onScreenActive()
        advanceUntilIdle()
    }

    private fun tableAt(fetchedAt: Long, codes: Set<String> = SampleRates.RATES.keys): RateTable =
        SampleRates.table(codes = codes).copy(fetchedAtMillis = fetchedAt)

    // --- Loading -----------------------------------------------------------------------------

    @Test
    fun loadsTheCacheBeforeReportingLoaded() = runTest {
        val cached = ExchangeCache(table = tableAt(t0), etag = "\"v1\"", lastSuccessMillis = t0, selectedFrom = "GBP", selectedTo = "CZK")
        store.updateData { cached }
        val repository = repository()
        assertFalse(repository.state.value.loaded)

        advanceUntilIdle()
        val state = repository.state.value
        assertTrue(state.loaded)
        assertEquals(cached.table, state.table)
        assertEquals("GBP", state.selectedFrom)
        assertEquals("CZK", state.selectedTo)
        assertEquals(t0, state.lastSuccessMillis)
        assertEquals(0, client.calls)
    }

    @Test
    fun unreadableStorageStartsWithDefaults() = runTest {
        val repository = repository(cache = BrokenDataStore())
        advanceUntilIdle()
        val state = repository.state.value
        assertTrue(state.loaded)
        assertNull(state.table)
        assertEquals("EUR", state.selectedFrom)
        assertEquals("USD", state.selectedTo)

        // Even without working storage, a refresh still serves rates for this session.
        repository.refreshNow()
        advanceUntilIdle()
        assertNotNull(repository.state.value.table)
        assertFalse(repository.state.value.lastAttemptFailed)
    }

    // --- Daily throttling --------------------------------------------------------------------

    @Test
    fun automaticRefreshRunsAtMostOncePerDay() = runTest {
        val repository = repository()

        activateAt(repository, t0)
        assertEquals(1, client.calls)
        assertEquals(t0, store.value.lastAutoAttemptMillis)
        assertEquals(t0, store.value.lastSuccessMillis)
        assertEquals(t0, store.value.table?.fetchedAtMillis)

        activateAt(repository, t0 + hour)
        assertEquals(1, client.calls)
        activateAt(repository, t0 + day - 1)
        assertEquals(1, client.calls)

        activateAt(repository, t0 + day)
        assertEquals(2, client.calls)
        assertEquals(t0 + day, store.value.lastAutoAttemptMillis)
    }

    @Test
    fun automaticAttemptIsPersistedBeforeTheRequest() = runTest {
        val gate = CompletableDeferred<FetchResult>()
        client.handler = { gate.await() }
        val repository = repository()

        now = t0
        repository.onScreenActive()
        runCurrent()
        assertEquals(1, client.calls)
        assertEquals(t0, store.value.lastAutoAttemptMillis)
        assertEquals(0L, store.value.lastSuccessMillis)
        assertTrue(repository.state.value.refreshing)

        gate.complete(FetchResult.Downloaded(SampleRates.table(), "\"v1\""))
        advanceUntilIdle()
        assertFalse(repository.state.value.refreshing)
    }

    @Test
    fun failedAutomaticAttemptBlocksRetriesForADay() = runTest {
        client.handler = { throw IOException("offline") }
        val repository = repository()

        activateAt(repository, t0)
        assertEquals(1, client.calls)
        assertTrue(repository.state.value.lastAttemptFailed)

        activateAt(repository, t0 + hour)
        activateAt(repository, t0 + day - minute)
        assertEquals(1, client.calls)

        activateAt(repository, t0 + day)
        assertEquals(2, client.calls)
    }

    @Test
    fun clockMovedBackwardsTriggersOneRefreshAndThenThrottlesAgain() = runTest {
        val repository = repository()
        activateAt(repository, t0)
        assertEquals(1, client.calls)

        activateAt(repository, t0 - 2 * hour)
        assertEquals(2, client.calls)
        assertEquals(t0 - 2 * hour, store.value.lastAutoAttemptMillis)
        assertEquals(t0 - 2 * hour, store.value.lastSuccessMillis)

        activateAt(repository, t0 - 2 * hour + minute)
        assertEquals(2, client.calls)
        activateAt(repository, t0 - 2 * hour + day)
        assertEquals(3, client.calls)
    }

    @Test
    fun clockMovedBackwardsWhileOfflineDoesNotRetryOnEveryActivation() = runTest {
        val repository = repository()
        activateAt(repository, t0)
        client.handler = { throw IOException("offline") }

        activateAt(repository, t0 - 2 * hour)
        assertEquals(2, client.calls)
        activateAt(repository, t0 - hour)
        assertEquals(2, client.calls)
    }

    @Test
    fun restartKeepsTheSchedule() = runTest {
        val first = repository()
        activateAt(first, t0)
        assertEquals(1, client.calls)

        // "Restart": a new repository instance on the same storage.
        val second = repository()
        activateAt(second, t0 + 2 * hour)
        assertEquals(1, client.calls)
        assertEquals(store.value.table, second.state.value.table)

        activateAt(second, t0 + day)
        assertEquals(2, client.calls)
    }

    @Test
    fun restartAfterFailedAttemptKeepsTheSchedule() = runTest {
        client.handler = { throw IOException("offline") }
        activateAt(repository(), t0)
        assertEquals(1, client.calls)

        client.handler = { FetchResult.Downloaded(SampleRates.table(), "\"v1\"") }
        val restarted = repository()
        activateAt(restarted, t0 + hour)
        assertEquals(1, client.calls)
        // Session-only failure flags do not survive a restart.
        assertFalse(restarted.state.value.lastAttemptFailed)
    }

    // --- Manual refresh ----------------------------------------------------------------------

    @Test
    fun manualRefreshBypassesThrottling() = runTest {
        val repository = repository()
        activateAt(repository, t0)
        assertEquals(1, client.calls)

        now = t0 + minute
        repository.refreshNow()
        assertTrue(repository.state.value.refreshing)
        advanceUntilIdle()
        assertEquals(2, client.calls)

        now = t0 + 2 * minute
        repository.refreshNow()
        advanceUntilIdle()
        assertEquals(3, client.calls)

        // Manual refreshes do not count as automatic attempts but do record the success.
        assertEquals(t0, store.value.lastAutoAttemptMillis)
        assertEquals(t0 + 2 * minute, store.value.lastSuccessMillis)
        assertEquals(t0 + 2 * minute, repository.state.value.table?.fetchedAtMillis)
        assertTrue(repository.state.value.refreshedThisSession)
    }

    @Test
    fun recentManualSuccessPostponesTheAutomaticRefresh() = runTest {
        val repository = repository()
        now = t0
        repository.refreshNow()
        advanceUntilIdle()
        assertEquals(1, client.calls)

        activateAt(repository, t0 + hour)
        assertEquals(1, client.calls)
        activateAt(repository, t0 + day)
        assertEquals(2, client.calls)
    }

    @Test
    fun refreshesAreSingleFlight() = runTest {
        val gate = CompletableDeferred<Unit>()
        client.handler = {
            gate.await()
            FetchResult.Downloaded(SampleRates.table(), "\"v1\"")
        }
        val repository = repository()

        repository.refreshNow()
        repository.refreshNow()
        runCurrent()
        repository.refreshNow()
        repository.onScreenActive()
        runCurrent()
        assertEquals(1, client.calls)
        assertTrue(repository.state.value.refreshing)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, client.calls)
        assertFalse(repository.state.value.refreshing)

        repository.refreshNow()
        advanceUntilIdle()
        assertEquals(2, client.calls)
    }

    // --- Results -----------------------------------------------------------------------------

    @Test
    fun successStoresTableEtagAndTimestamps() = runTest {
        val repository = repository()
        activateAt(repository, t0)

        val state = repository.state.value
        assertEquals(SampleRates.table().copy(fetchedAtMillis = t0), state.table)
        assertEquals("\"v1\"", store.value.etag)
        assertEquals(t0, state.lastSuccessMillis)
        assertFalse(state.lastAttemptFailed)
        assertNull(state.lastError)
        assertTrue(state.refreshedThisSession)
        assertEquals(listOf<String?>(null), client.etags)
    }

    @Test
    fun notModifiedKeepsTheTableAndRenewsTimestamps() = runTest {
        val repository = repository()
        activateAt(repository, t0)
        val original = repository.state.value.table!!

        client.handler = { FetchResult.NotModified }
        activateAt(repository, t0 + day + hour)

        assertEquals(listOf(null, "\"v1\""), client.etags)
        val state = repository.state.value
        assertEquals(original.copy(fetchedAtMillis = t0 + day + hour), state.table)
        assertEquals(t0 + day + hour, state.lastSuccessMillis)
        assertEquals(t0 + day + hour, store.value.table?.fetchedAtMillis)
        assertEquals("\"v1\"", store.value.etag)
        assertFalse(state.lastAttemptFailed)
    }

    @Test
    fun notModifiedWithoutCachedTableIsInvalidData() = runTest {
        client.handler = { FetchResult.NotModified }
        val repository = repository()
        activateAt(repository, t0)
        assertNull(repository.state.value.table)
        assertEquals(ExchangeError.InvalidData, repository.state.value.lastError)
    }

    @Test
    fun networkFailureKeepsTheCachedTable() = runTest {
        val repository = repository()
        activateAt(repository, t0)
        val cached = repository.state.value.table

        client.handler = { throw IOException("Unable to resolve host") }
        now = t0 + hour
        repository.refreshNow()
        advanceUntilIdle()

        val state = repository.state.value
        assertSame(cached, state.table)
        assertEquals(cached, store.value.table)
        assertEquals(t0, state.lastSuccessMillis)
        assertTrue(state.lastAttemptFailed)
        assertEquals(ExchangeError.NoConnection, state.lastError)
        assertFalse(state.refreshing)
    }

    @Test
    fun failuresAreClassified() = runTest {
        val repository = repository()
        val cases = listOf(
            RatesServerException(500, "HTTP 500") to ExchangeError.Server,
            RatesFormatException("bad json") to ExchangeError.InvalidData,
            IOException("timeout") to ExchangeError.NoConnection,
            IllegalStateException("unexpected") to ExchangeError.InvalidData,
        )
        cases.forEach { (failure, expected) ->
            client.handler = { throw failure }
            repository.refreshNow()
            advanceUntilIdle()
            assertEquals(expected, repository.state.value.lastError)
            assertTrue(repository.state.value.lastAttemptFailed)
            assertNull(repository.state.value.table)
        }

        client.handler = { FetchResult.Downloaded(SampleRates.table(), null) }
        repository.refreshNow()
        advanceUntilIdle()
        assertFalse(repository.state.value.lastAttemptFailed)
        assertNull(repository.state.value.lastError)
    }

    @Test
    fun realClientErrorsMapToExchangeErrors() = runTest {
        fun clientFor(transport: HttpTransport) =
            HttpRatesClient(transport, StandardTestDispatcher(testScheduler))

        val cases = listOf(
            FakeTransport { _, _ -> HttpResponse(500, "") } to ExchangeError.Server,
            FakeTransport { url, _ ->
                if (url == HttpRatesClient.ECB_DAILY_URL) HttpResponse(200, "<html/>") else HttpResponse(200, "{bad json")
            } to ExchangeError.InvalidData,
            FakeTransport { _, _ -> throw IOException("offline") } to ExchangeError.NoConnection,
        )
        cases.forEach { (transport, expected) ->
            val repository = repository(cache = InMemoryDataStore(ExchangeCache()), ratesClient = clientFor(transport))
            repository.refreshNow()
            advanceUntilIdle()
            assertEquals(expected, repository.state.value.lastError)
            assertNull(repository.state.value.table)
        }
    }

    // --- Selection ---------------------------------------------------------------------------

    @Test
    fun selectionIsPersistedAndSwapped() = runTest {
        val repository = repository()
        activateAt(repository, t0)

        repository.setSelection("gbp", "JPY")
        assertEquals("GBP", repository.state.value.selectedFrom)
        assertEquals("JPY", repository.state.value.selectedTo)
        assertEquals("GBP", store.value.selectedFrom)
        assertEquals("JPY", store.value.selectedTo)

        repository.swap()
        assertEquals("JPY", repository.state.value.selectedFrom)
        assertEquals("GBP", repository.state.value.selectedTo)
        assertEquals("JPY", store.value.selectedFrom)
        assertEquals("GBP", store.value.selectedTo)

        val restarted = repository()
        advanceUntilIdle()
        assertEquals("JPY", restarted.state.value.selectedFrom)
        assertEquals("GBP", restarted.state.value.selectedTo)
    }

    @Test
    fun unknownCodesAreIgnored() = runTest {
        val repository = repository()
        activateAt(repository, t0)

        repository.setSelection("XYZ", "CZK")
        assertEquals("EUR", repository.state.value.selectedFrom)
        assertEquals("CZK", repository.state.value.selectedTo)
    }

    @Test
    fun selectionWorksBeforeAnyRatesExist() = runTest {
        client.handler = { throw IOException("offline") }
        val repository = repository()
        advanceUntilIdle()

        repository.swap()
        assertEquals("USD", store.value.selectedFrom)
        assertEquals("EUR", store.value.selectedTo)
    }

    @Test
    fun selectionFallsBackWhenACurrencyDisappears() = runTest {
        val repository = repository()
        activateAt(repository, t0)
        repository.setSelection("PLN", "CHF")

        client.handler = { FetchResult.Downloaded(SampleRates.table(codes = setOf("USD", "GBP", "CHF")), "\"v2\"") }
        now = t0 + hour
        repository.refreshNow()
        advanceUntilIdle()

        assertEquals("EUR", repository.state.value.selectedFrom)
        assertEquals("CHF", repository.state.value.selectedTo)
        assertEquals("EUR", store.value.selectedFrom)
    }
}
