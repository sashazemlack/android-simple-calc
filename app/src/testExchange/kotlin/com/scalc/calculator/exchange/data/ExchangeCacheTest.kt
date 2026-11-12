package com.scalc.calculator.exchange.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.scalc.calculator.exchange.model.RateSourceKind
import com.scalc.calculator.exchange.model.SampleRates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val t0 = 1_794_000_000_000L
    private val hour = 60L * 60L * 1000L
    private var now = t0
    private val storeJobs = mutableListOf<Job>()

    private val fullCache = ExchangeCache(
        table = SampleRates.table(fetchedAtMillis = t0, source = RateSourceKind.ECB_DIRECT),
        etag = "W/\"abc\"",
        lastAutoAttemptMillis = t0 - 5,
        lastSuccessMillis = t0,
        selectedFrom = "CZK",
        selectedTo = "JPY",
    )

    @After
    fun closeStores() {
        storeJobs.forEach { it.cancel() }
    }

    private fun newStoreScope(): Pair<CoroutineScope, Job> {
        val job = Job()
        storeJobs += job
        return CoroutineScope(Dispatchers.IO + job) to job
    }

    /**
     * Real DataStore on a real file with the production serializer and corruption handler
     * (see [NioFileStorage] for why the file replacement differs from `FileStorage` in JVM tests).
     */
    private fun openStore(file: File): Pair<DataStore<ExchangeCache>, Job> {
        val (scope, job) = newStoreScope()
        val store = DataStoreFactory.create(
            storage = NioFileStorage(ExchangeCacheSerializer) { file },
            corruptionHandler = ReplaceFileCorruptionHandler { ExchangeCache() },
            scope = scope,
        )
        return store to job
    }

    /** Exactly the production configuration of [ExchangeModule] (DataStore's own FileStorage). */
    private fun openProductionStore(file: File): Pair<DataStore<ExchangeCache>, Job> {
        val (scope, job) = newStoreScope()
        val store = DataStoreFactory.create(
            serializer = ExchangeCacheSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { ExchangeCache() },
            scope = scope,
            produceFile = { file },
        )
        return store to job
    }

    private fun TestScope.repositoryOn(store: DataStore<ExchangeCache>, client: RatesClient = FakeRatesClient()) =
        ExchangeRepository(store, client, this) { now }

    private suspend fun roundTrip(cache: ExchangeCache): ExchangeCache {
        val bytes = ByteArrayOutputStream().also { ExchangeCacheSerializer.writeTo(cache, it) }.toByteArray()
        return ExchangeCacheSerializer.readFrom(ByteArrayInputStream(bytes))
    }

    private suspend fun assertCorrupt(bytes: ByteArray) {
        try {
            ExchangeCacheSerializer.readFrom(ByteArrayInputStream(bytes))
            fail("Expected CorruptionException")
        } catch (expected: CorruptionException) {
            // expected
        }
    }

    // --- Serializer --------------------------------------------------------------------------

    @Test
    fun serializerRoundTripsExactly() = runTest {
        assertEquals(fullCache, roundTrip(fullCache))
        assertEquals(ExchangeCache(), roundTrip(ExchangeCache()))
        val restored = roundTrip(fullCache).table!!
        assertEquals(SampleRates.RATES.keys, restored.rates.keys)
        assertEquals("Czech Koruna", restored.currencyOf("CZK")?.name)
    }

    @Test
    fun serializerKeepsPerCurrencyDatesAndWritesTheCurrentVersion() = runTest {
        val blended = fullCache.copy(
            table = SampleRates.table(
                fetchedAtMillis = t0,
                rateDate = "2026-11-12",
                rateDates = mapOf("PLN" to "2026-11-11", "JPY" to "2026-11-06"),
            ),
        )
        val restored = roundTrip(blended)
        assertEquals(blended, restored)
        assertEquals(RateSourceKind.FRANKFURTER_BLENDED, restored.table?.source)
        assertEquals("2026-11-11", restored.table?.rateDateOf("PLN"))
        assertEquals("2026-11-06", restored.table?.rateDateOf("JPY"))
        assertEquals("2026-11-12", restored.table?.rateDate)

        val bytes = ByteArrayOutputStream().also { ExchangeCacheSerializer.writeTo(blended, it) }.toByteArray()
        assertEquals(2, ExchangeCacheSerializer.VERSION)
        assertEquals(ExchangeCacheSerializer.VERSION, DataInputStream(ByteArrayInputStream(bytes)).readInt())
    }

    /** Bytes in the format of earlier app versions (version 1: one rate date for all currencies). */
    private fun versionOneBytes(source: String = RateSourceKind.FRANKFURTER_ECB.name): ByteArray =
        ByteArrayOutputStream().also { out ->
            DataOutputStream(out).apply {
                writeInt(ExchangeCacheSerializer.VERSION_SINGLE_RATE_DATE)
                writeBoolean(true)
                writeUTF("2026-11-10")
                writeLong(t0)
                writeUTF(source)
                writeInt(SampleRates.RATES.size)
                SampleRates.RATES.forEach { (code, rate) ->
                    writeUTF(code)
                    writeUTF(rate.toString())
                }
                writeInt(SampleRates.NAMES.size)
                SampleRates.NAMES.forEach { (code, name) ->
                    writeUTF(code)
                    writeUTF(name)
                }
                writeBoolean(true)
                writeUTF("W/\"ecb-only\"")
                writeLong(t0 - 5)
                writeLong(t0)
                writeUTF("CZK")
                writeUTF("JPY")
            }
        }.toByteArray()

    @Test
    fun versionOneCachesAreMigrated() = runTest {
        val migrated = ExchangeCacheSerializer.readFrom(ByteArrayInputStream(versionOneBytes()))
        val table = migrated.table!!

        assertEquals(RateSourceKind.FRANKFURTER_ECB, table.source)
        assertEquals(SampleRates.RATES.keys, table.rates.keys)
        assertEquals(0, BigDecimal("24.378").compareTo(table.rateOf("CZK")))
        assertEquals("Czech Koruna", table.currencyOf("CZK")?.name)
        assertEquals(t0, table.fetchedAtMillis)
        // The single old date applies to every currency.
        assertEquals("2026-11-10", table.rateDate)
        assertEquals(SampleRates.RATES.keys.associateWith { "2026-11-10" }, table.rateDates)
        // The validator belonged to the old ECB-only URL: dropped, so the next refresh downloads in full.
        assertNull(migrated.etag)
        // Schedule and selection are kept.
        assertEquals(t0 - 5, migrated.lastAutoAttemptMillis)
        assertEquals(t0, migrated.lastSuccessMillis)
        assertEquals("CZK" to "JPY", migrated.selectedFrom to migrated.selectedTo)

        // Written back in the current format, it reads the same.
        assertEquals(migrated, roundTrip(migrated))
        // Version 1 files of the ECB fallback stay ECB tables.
        assertEquals(
            RateSourceKind.ECB_DIRECT,
            ExchangeCacheSerializer.readFrom(ByteArrayInputStream(versionOneBytes(source = "ECB_DIRECT"))).table?.source,
        )
    }

    @Test
    fun versionOneFileIsMigratedOnDisk() = runTest {
        val file = File(temporaryFolder.root, ExchangeModule.CACHE_FILE_NAME)
        file.writeBytes(versionOneBytes())

        val (store, job) = openStore(file)
        val repository = repositoryOn(store)
        val state = repository.state.first { it.loaded }
        assertEquals(RateSourceKind.FRANKFURTER_ECB, state.table?.source)
        assertEquals("2026-11-10", state.table?.rateDateOf("USD"))
        assertEquals("CZK", state.selectedFrom)
        assertEquals(t0, state.lastSuccessMillis)

        // The next write stores the current format.
        repository.setSelection("GBP", "USD")
        job.cancelAndJoin()
        assertEquals(ExchangeCacheSerializer.VERSION, DataInputStream(file.inputStream()).use { it.readInt() })
        val (reopened, _) = openStore(file)
        assertEquals(state.table, reopened.data.first().table)
        assertEquals("GBP", reopened.data.first().selectedFrom)
    }

    @Test
    fun serializerRejectsCorruptData() = runTest {
        val valid = ByteArrayOutputStream().also { ExchangeCacheSerializer.writeTo(fullCache, it) }.toByteArray()
        assertCorrupt(ByteArray(0))
        assertCorrupt(valid.copyOf(valid.size / 2))
        assertCorrupt(byteArrayOf(0, 0, 0, 99) + valid.copyOfRange(4, valid.size))
        assertCorrupt(byteArrayOf(0, 0, 0, 3) + valid.copyOfRange(4, valid.size))
        assertCorrupt(ByteArray(64) { (it * 37).toByte() })

        // Structurally valid but semantically wrong: unknown source.
        val unknownSource = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).apply {
                writeInt(1)
                writeBoolean(true)
                writeUTF("2026-11-10")
                writeLong(t0)
                writeUTF("SOMEWHERE_ELSE")
                writeInt(0)
                writeInt(0)
            }
        }.toByteArray()
        assertCorrupt(unknownSource)

        // A table with nothing but EUR is not usable.
        val onlyEur = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).apply {
                writeInt(1)
                writeBoolean(true)
                writeUTF("2026-11-10")
                writeLong(t0)
                writeUTF(RateSourceKind.FRANKFURTER_ECB.name)
                writeInt(1)
                writeUTF("EUR")
                writeUTF("1")
                writeInt(0)
            }
        }.toByteArray()
        assertCorrupt(onlyEur)

        // Version 2 rows need a valid date.
        val undatedRate = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).apply {
                writeInt(ExchangeCacheSerializer.VERSION)
                writeBoolean(true)
                writeUTF("2026-11-10")
                writeLong(t0)
                writeUTF(RateSourceKind.FRANKFURTER_BLENDED.name)
                writeInt(1)
                writeUTF("USD")
                writeUTF("1.1127")
                writeUTF("soon")
                writeInt(0)
            }
        }.toByteArray()
        assertCorrupt(undatedRate)
    }

    @Test
    fun serializerRepairsAnInvalidSelection() = runTest {
        val restored = roundTrip(fullCache.copy(selectedFrom = "BGN", selectedTo = "zz"))
        assertEquals("EUR", restored.selectedFrom)
        assertEquals("USD", restored.selectedTo)
    }

    // --- Real file persistence ---------------------------------------------------------------

    @Test
    fun cacheSurvivesARestart() = runTest {
        val file = File(temporaryFolder.root, ExchangeModule.CACHE_FILE_NAME)
        val (storeA, jobA) = openStore(file)
        val repositoryA = repositoryOn(storeA)

        now = t0
        repositoryA.onScreenActive()
        val stateA = repositoryA.state.first { it.table != null && !it.refreshing }
        repositoryA.setSelection("GBP", "CZK")
        repositoryA.swap()
        jobA.cancelAndJoin()
        assertTrue(file.length() > 0)

        val (storeB, _) = openStore(file)
        val counting = FakeRatesClient()
        val repositoryB = repositoryOn(storeB, counting)
        val stateB = repositoryB.state.first { it.loaded }

        assertEquals(stateA.table, stateB.table)
        assertEquals(t0, stateB.table?.fetchedAtMillis)
        assertEquals(t0, stateB.lastSuccessMillis)
        assertEquals(t0, stateB.lastAutoAttemptMillis)
        assertEquals("CZK", stateB.selectedFrom)
        assertEquals("GBP", stateB.selectedTo)
        assertEquals("\"v1\"", storeB.data.first().etag)

        // The persisted schedule still applies after the restart. (A due refresh would publish
        // the new attempt time before any disk or network I/O.)
        now = t0 + hour
        repositoryB.onScreenActive()
        advanceUntilIdle()
        assertEquals(t0, repositoryB.state.value.lastAutoAttemptMillis)
        assertEquals(0, counting.calls)
    }

    @Test
    fun corruptedFileFallsBackToDefaultsAndRecovers() = runTest {
        val file = File(temporaryFolder.root, ExchangeModule.CACHE_FILE_NAME)
        file.writeBytes(ByteArray(200) { (it * 31 + 7).toByte() })

        val (store, job) = openStore(file)
        val repository = repositoryOn(store)
        val state = repository.state.first { it.loaded }
        assertNull(state.table)
        assertEquals("EUR", state.selectedFrom)
        assertEquals("USD", state.selectedTo)
        assertEquals(0L, state.lastAutoAttemptMillis)

        repository.refreshNow()
        val refreshed = repository.state.first { it.table != null && !it.refreshing }
        assertFalse(refreshed.lastAttemptFailed)
        repository.setSelection("JPY", "PLN")
        job.cancelAndJoin()

        val (reopened, _) = openStore(file)
        val cached = reopened.data.first()
        assertEquals(refreshed.table, cached.table)
        assertEquals("JPY", cached.selectedFrom)
        assertEquals("PLN", cached.selectedTo)
    }

    @Test
    fun productionFileStorageRoundTrip() = runTest {
        // One write per session: DataStore's FileStorage can always create a new file, even on
        // Windows JVM hosts (see NioFileStorage).
        val file = File(temporaryFolder.root, ExchangeModule.CACHE_FILE_NAME)
        val (writer, writerJob) = openProductionStore(file)
        writer.updateData { fullCache }
        writerJob.cancelAndJoin()

        val (reader, _) = openProductionStore(file)
        assertEquals(fullCache, reader.data.first())
    }
}
