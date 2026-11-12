package com.scalc.calculator.calc.ui

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Deterministic in-memory [DataStore] for JVM tests (no files, no background dispatcher). */
internal class InMemoryDataStore<T>(initial: T) : DataStore<T> {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    /** When true, every write fails like a full or read-only disk. */
    var failWrites = false

    /** Number of upcoming [data] collections that fail with an IOException (transient read errors). */
    var failNextReads = 0

    val current: T get() = state.value

    override val data: Flow<T> = flow {
        if (failNextReads > 0) {
            failNextReads--
            throw IOException("Simulated read failure")
        }
        emitAll(state)
    }

    override suspend fun updateData(transform: suspend (t: T) -> T): T = mutex.withLock {
        if (failWrites) throw IOException("Simulated storage failure")
        transform(state.value).also { state.value = it }
    }
}
