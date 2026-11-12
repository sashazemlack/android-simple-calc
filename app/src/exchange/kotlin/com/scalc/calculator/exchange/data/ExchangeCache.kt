package com.scalc.calculator.exchange.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.scalc.calculator.exchange.model.CurrencySelection
import com.scalc.calculator.exchange.model.RateSourceKind
import com.scalc.calculator.exchange.model.RateTable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal

/**
 * Everything the Exchange feature persists. Contains no personal data and never the typed amount:
 * only public reference rates, the server validator, refresh timestamps (epoch millis, 0 = never)
 * and the selected currency codes.
 */
data class ExchangeCache(
    val table: RateTable? = null,
    val etag: String? = null,
    val lastAutoAttemptMillis: Long = 0L,
    val lastSuccessMillis: Long = 0L,
    val selectedFrom: String = CurrencySelection.DEFAULT_FROM,
    val selectedTo: String = CurrencySelection.DEFAULT_TO,
)

/**
 * Compact versioned binary format for [ExchangeCache] (no JSON dependency), in this order:
 *
 * ```
 * int version
 * boolean hasTable
 *   UTF rateDate, long fetchedAtMillis, UTF source,
 *   int rateCount, rateCount × (UTF code, UTF rate as BigDecimal.toString()),
 *   int nameCount, nameCount × (UTF code, UTF name)
 * boolean hasEtag, [UTF etag]
 * long lastAutoAttemptMillis, long lastSuccessMillis
 * UTF selectedFrom, UTF selectedTo
 * ```
 *
 * Rates are stored as decimal strings, so they round-trip exactly. Anything unreadable or
 * inconsistent raises [CorruptionException]; the DataStore's corruption handler then starts over
 * with [defaultValue].
 */
object ExchangeCacheSerializer : Serializer<ExchangeCache> {

    private const val VERSION = 1
    private const val MAX_ENTRIES = 1_000

    override val defaultValue: ExchangeCache = ExchangeCache()

    override suspend fun readFrom(input: InputStream): ExchangeCache {
        try {
            val data = DataInputStream(input)
            val version = data.readInt()
            if (version != VERSION) throw CorruptionException("Unsupported exchange cache version $version")
            val table = if (data.readBoolean()) readTable(data) else null
            val etag = if (data.readBoolean()) data.readUTF() else null
            val lastAutoAttempt = data.readLong()
            val lastSuccess = data.readLong()
            val (from, to) = CurrencySelection.sanitize(data.readUTF(), data.readUTF(), table)
            return ExchangeCache(table, etag, lastAutoAttempt, lastSuccess, from, to)
        } catch (e: CorruptionException) {
            throw e
        } catch (e: IOException) {
            throw CorruptionException("Unreadable exchange cache", e)
        }
    }

    override suspend fun writeTo(t: ExchangeCache, output: OutputStream) {
        val data = DataOutputStream(output)
        data.writeInt(VERSION)
        val table = t.table
        data.writeBoolean(table != null)
        if (table != null) {
            data.writeUTF(table.rateDate)
            data.writeLong(table.fetchedAtMillis)
            data.writeUTF(table.source.name)
            data.writeInt(table.rates.size)
            table.rates.forEach { (code, rate) ->
                data.writeUTF(code)
                data.writeUTF(rate.toString())
            }
            data.writeInt(table.currencies.size)
            table.currencies.forEach {
                data.writeUTF(it.code)
                data.writeUTF(it.name)
            }
        }
        data.writeBoolean(t.etag != null)
        t.etag?.let(data::writeUTF)
        data.writeLong(t.lastAutoAttemptMillis)
        data.writeLong(t.lastSuccessMillis)
        data.writeUTF(t.selectedFrom)
        data.writeUTF(t.selectedTo)
        data.flush()
    }

    private fun readTable(data: DataInputStream): RateTable {
        val rateDate = data.readUTF()
        val fetchedAt = data.readLong()
        val sourceName = data.readUTF()
        val source = RateSourceKind.entries.firstOrNull { it.name == sourceName }
            ?: throw CorruptionException("Unknown rate source $sourceName")
        val rateCount = readCount(data)
        val rates = LinkedHashMap<String, BigDecimal>(rateCount)
        repeat(rateCount) {
            val code = data.readUTF()
            rates[code] = data.readUTF().toBigDecimalOrNull() ?: throw CorruptionException("Invalid rate for $code")
        }
        val nameCount = readCount(data)
        val names = HashMap<String, String>(nameCount)
        repeat(nameCount) { names[data.readUTF()] = data.readUTF() }
        return RateTable.create(rates, rateDate, names, source, fetchedAt)
            ?: throw CorruptionException("Invalid cached rate table")
    }

    private fun readCount(data: DataInputStream): Int {
        val count = data.readInt()
        if (count !in 0..MAX_ENTRIES) throw CorruptionException("Invalid entry count $count")
        return count
    }
}
