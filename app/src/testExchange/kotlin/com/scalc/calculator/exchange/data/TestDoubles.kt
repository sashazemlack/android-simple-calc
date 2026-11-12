package com.scalc.calculator.exchange.data

import androidx.datastore.core.DataStore
import com.scalc.calculator.exchange.model.SampleRates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Deterministic in-memory [DataStore]; runs entirely on the caller's (test) dispatcher. */
class InMemoryDataStore<T>(initial: T) : DataStore<T> {
    private val flow = MutableStateFlow(initial)
    private val mutex = Mutex()
    var writes = 0
        private set

    val value: T get() = flow.value

    override val data: Flow<T> = flow

    override suspend fun updateData(transform: suspend (t: T) -> T): T = mutex.withLock {
        transform(flow.value).also {
            flow.value = it
            writes++
        }
    }
}

/** A [DataStore] whose storage is unreadable and unwritable. */
class BrokenDataStore<T> : DataStore<T> {
    override val data: Flow<T> = flow { throw IOException("disk unavailable") }
    override suspend fun updateData(transform: suspend (t: T) -> T): T = throw IOException("disk unavailable")
}

/** Counting [RatesClient]; its behavior can be swapped per test step. */
class FakeRatesClient(
    var handler: suspend (etag: String?) -> FetchResult = { FetchResult.Downloaded(SampleRates.table(), "\"v1\"") },
) : RatesClient {
    val etags = mutableListOf<String?>()
    val calls: Int get() = etags.size

    override suspend fun fetch(etag: String?): FetchResult {
        etags += etag
        return handler(etag)
    }
}

/** Records requests and answers them through [handler] (may throw like a real network). */
class FakeTransport(
    var handler: (url: String, headers: Map<String, String>) -> HttpResponse,
) : HttpTransport {
    val requests = mutableListOf<Pair<String, Map<String, String>>>()

    override fun get(url: String, headers: Map<String, String>): HttpResponse {
        requests += url to headers
        return handler(url, headers)
    }
}
