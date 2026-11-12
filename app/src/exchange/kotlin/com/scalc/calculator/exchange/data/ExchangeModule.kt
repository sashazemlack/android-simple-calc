package com.scalc.calculator.exchange.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide wiring of the Exchange feature (Exchange flavor only; no DI framework).
 * The repository lives as long as the process, so a refresh started on the Exchange tab keeps
 * running while the user switches to the calculator, and DataStore stays a per-file singleton.
 */
object ExchangeModule {

    const val CACHE_FILE_NAME = "exchange_cache.bin"
    private const val TAG = "ExchangeModule"

    @Volatile
    private var instance: ExchangeRepository? = null

    /** The shared repository, created on first use from the application context. */
    fun repository(context: Context): ExchangeRepository =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

    private fun create(appContext: Context): ExchangeRepository {
        // Last line of defense: the repository handles expected failures itself, but an
        // unexpected bug in background work must not crash the app.
        val errorHandler = CoroutineExceptionHandler { _, error -> Log.w(TAG, "Exchange background work failed", error) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + errorHandler)
        val cache = DataStoreFactory.create(
            serializer = ExchangeCacheSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { ExchangeCache() },
            produceFile = { appContext.dataStoreFile(CACHE_FILE_NAME) },
        )
        return ExchangeRepository(cache = cache, client = HttpRatesClient(), scope = scope)
    }
}
