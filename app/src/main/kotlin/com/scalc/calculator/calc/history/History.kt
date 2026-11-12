package com.scalc.calculator.calc.history

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * One completed calculation. [timestamp] is epoch milliseconds (System.currentTimeMillis()).
 *
 * [carryValue] is the exact value (BigDecimal string, 34 significant digits at most) of the
 * previous result that [expression] started with when it was computed, or null. "1÷3 = ×3 ="
 * stores "0.333333333333333×3 = 1" with the exact third, so restoring the entry reproduces 1
 * instead of 0.999999999999999.
 */
data class HistoryEntry(
    val expression: String,
    val result: String,
    val timestamp: Long,
    val carryValue: String? = null,
)

/** Persisted history, newest first. */
data class HistoryData(val entries: List<HistoryEntry> = emptyList())

/**
 * Compact versioned binary format (no JSON dependency):
 * int version, int count, then per entry: UTF expression, UTF result, long timestamp and, since
 * version 2, boolean hasCarry followed by UTF carryValue when true. Version 1 files (without the
 * carry fields) are still read, so an update never discards the existing history.
 */
object HistorySerializer : Serializer<HistoryData> {
    private const val VERSION = 2
    private const val VERSION_WITHOUT_CARRY = 1

    override val defaultValue: HistoryData = HistoryData()

    override suspend fun readFrom(input: InputStream): HistoryData {
        try {
            val data = DataInputStream(input)
            val version = data.readInt()
            if (version != VERSION && version != VERSION_WITHOUT_CARRY) {
                throw CorruptionException("Unsupported history version $version")
            }
            val count = data.readInt()
            if (count < 0 || count > 10_000) throw CorruptionException("Invalid history size $count")
            val entries = ArrayList<HistoryEntry>(count)
            repeat(count) {
                val expression = data.readUTF()
                val result = data.readUTF()
                val timestamp = data.readLong()
                val carryValue = if (version >= VERSION && data.readBoolean()) data.readUTF() else null
                entries += HistoryEntry(expression, result, timestamp, carryValue)
            }
            return HistoryData(entries)
        } catch (e: EOFException) {
            throw CorruptionException("Truncated history file", e)
        } catch (e: IOException) {
            throw CorruptionException("Unreadable history file", e)
        }
    }

    override suspend fun writeTo(t: HistoryData, output: OutputStream) {
        val data = DataOutputStream(output)
        data.writeInt(VERSION)
        data.writeInt(t.entries.size)
        t.entries.forEach {
            data.writeUTF(it.expression)
            data.writeUTF(it.result)
            data.writeLong(it.timestamp)
            val carryValue = it.carryValue
            data.writeBoolean(carryValue != null)
            if (carryValue != null) data.writeUTF(carryValue)
        }
        data.flush()
    }
}

/** Calculation history (max [maxEntries] most recent entries, newest first), survives restarts. */
class HistoryRepository(
    private val dataStore: DataStore<HistoryData>,
    private val maxEntries: Int = MAX_ENTRIES,
) {
    /**
     * The entries, newest first. A read error shows an empty history once and the read is
     * retried with backoff (1 s, 2 s, … up to 30 s), so a transient IO failure does not freeze
     * the list for the rest of the process.
     */
    val entries: Flow<List<HistoryEntry>> = dataStore.data
        .retryWhen { cause, attempt ->
            if (cause !is IOException) return@retryWhen false
            if (attempt == 0L) emit(HistoryData())
            delay(minOf(MAX_RETRY_DELAY_MILLIS, FIRST_RETRY_DELAY_MILLIS shl attempt.coerceAtMost(MAX_BACKOFF_SHIFT).toInt()))
            true
        }
        .map { it.entries }

    suspend fun add(entry: HistoryEntry) {
        dataStore.updateData { current -> HistoryData((listOf(entry) + current.entries).take(maxEntries)) }
    }

    suspend fun clear() {
        dataStore.updateData { HistoryData() }
    }

    companion object {
        const val MAX_ENTRIES = 100
        private const val FIRST_RETRY_DELAY_MILLIS = 1_000L
        private const val MAX_RETRY_DELAY_MILLIS = 30_000L
        private const val MAX_BACKOFF_SHIFT = 5L
    }
}
