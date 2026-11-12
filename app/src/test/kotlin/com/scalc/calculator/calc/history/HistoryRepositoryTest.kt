package com.scalc.calculator.calc.history

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.scalc.calculator.calc.ui.InMemoryDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** [HistoryRepository] on real files through the production DataStore implementation. */
class HistoryRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val jobs = mutableListOf<Job>()

    @After
    fun cancelStores() {
        jobs.forEach { it.cancel() }
    }

    private fun historyFile() = File(temporaryFolder.root, "history.bin")

    /** A DataStore on [file] whose scope is cancelled through the returned job (or after the test). */
    private fun openStore(file: File): Pair<DataStore<HistoryData>, Job> {
        val job = Job().also { jobs += it }
        val store = DataStoreFactory.create(
            storage = JvmFileStorage(HistorySerializer) { file },
            corruptionHandler = ReplaceFileCorruptionHandler { HistoryData() },
            scope = CoroutineScope(Dispatchers.IO + job),
        )
        return store to job
    }

    private fun entry(i: Int) = HistoryEntry(expression = "$i×2", result = "${i * 2}", timestamp = 1_794_000_000_000L + i)

    @Test
    fun keepsTheHundredNewestEntriesNewestFirst() = runTest {
        val (store, _) = openStore(historyFile())
        val repository = HistoryRepository(store)
        repeat(105) { repository.add(entry(it)) }

        val entries = repository.entries.first()
        assertEquals(HistoryRepository.MAX_ENTRIES, entries.size)
        assertEquals(entry(104), entries.first())
        assertEquals(entry(5), entries.last())
        assertEquals((104 downTo 5).map(::entry), entries)
    }

    @Test
    fun historySurvivesANewRepositoryOnTheSameFile() = runTest {
        val file = historyFile()
        val (firstStore, firstJob) = openStore(file)
        HistoryRepository(firstStore).apply {
            add(entry(1))
            add(entry(2))
        }
        firstJob.cancelAndJoin()

        val (secondStore, _) = openStore(file)
        val reopened = HistoryRepository(secondStore)
        assertEquals(listOf(entry(2), entry(1)), reopened.entries.first())

        reopened.clear()
        assertTrue(reopened.entries.first().isEmpty())
    }

    @Test
    fun unicodeExpressionsArePersisted() = runTest {
        val (store, _) = openStore(historyFile())
        val repository = HistoryRepository(store)
        val scientific = HistoryEntry("√(16)+sin⁻¹(1)×π−e^(2)÷3!", "−1.5E-9", 42L)
        repository.add(scientific)
        assertEquals(listOf(scientific), repository.entries.first())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun transientReadErrorIsRetriedInsteadOfEndingTheFlow() = runTest {
        val store = InMemoryDataStore(HistoryData()).apply { failNextReads = 2 }
        val repository = HistoryRepository(store)
        val seen = mutableListOf<List<HistoryEntry>>()
        val collector = launch { repository.entries.collect { seen += it } }
        runCurrent()
        assertEquals("an empty history is shown once", listOf(emptyList<HistoryEntry>()), seen)

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals("second attempt failed too, nothing new", 1, seen.size)

        advanceTimeBy(2_001)
        runCurrent()
        repository.add(entry(1))
        runCurrent()
        assertEquals(listOf(entry(1)), seen.last())

        repository.add(entry(2))
        runCurrent()
        assertEquals(listOf(entry(2), entry(1)), seen.last())
        collector.cancel()
    }

    @Test
    fun corruptFileIsReplacedByAnEmptyHistory() = runTest {
        val file = historyFile()
        file.writeBytes(byteArrayOf(0, 0, 0, 9, 1, 2))
        val (store, _) = openStore(file)
        val repository = HistoryRepository(store)
        assertTrue(repository.entries.first().isEmpty())
        repository.add(entry(1))
        assertEquals(listOf(entry(1)), repository.entries.first())
    }
}
