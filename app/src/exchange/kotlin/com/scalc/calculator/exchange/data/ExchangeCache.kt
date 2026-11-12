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
 * int version (2)
 * boolean hasTable
 *   UTF rateDate (newest), long fetchedAtMillis, UTF source,
 *   int rateCount, rateCount × (UTF code, UTF rate as BigDecimal.toString(), UTF rate date),
 *   int nameCount, nameCount × (UTF code, UTF English name)
 * boolean hasEtag, [UTF etag]
 * long lastAutoAttemptMillis, long lastSuccessMillis
 * UTF selectedFrom, UTF selectedTo
 * ```
 *
 * Version 1 (written by earlier app versions) is still read: it has no per-currency rate dates,
 * so its single rate date applies to every currency. Its ETag is dropped, because it validated
 * the ECB-only feed those versions downloaded; the next refresh then downloads the current feed
 * in full. Refresh timestamps and the selection are kept.
 *
 * Rates are stored as decimal strings, so they round-trip exactly. Anything unreadable or
 * inconsistent raises [CorruptionException]; the DataStore's corruption handler then starts over
 * with [defaultValue].
 */
object ExchangeCacheSerializer : Serializer<ExchangeCache> {

    /** Format written by this version. */
    const val VERSION = 2

    /** Format of earlier app versions: one rate date for the whole table. */
    const val VERSION_SINGLE_RATE_DATE = 1

    private const val MAX_ENTRIES = 1_000

    override val defaultValue: ExchangeCache = ExchangeCache()

    override suspend fun readFrom(input: InputStream): ExchangeCache {
        try {
            val data = DataInputStream(input)
            val version = data.readInt()
            if (version != VERSION && version != VERSION_SINGLE_RATE_DATE) {
                throw CorruptionException("Unsupported exchange cache version $version")
            }
            val table = if (data.readBoolean()) readTable(data, perRateDates = version == VERSION) else null
            val etag = if (data.readBoolean()) data.readUTF() else null
            val lastAutoAttempt = data.readLong()
            val lastSuccess = data.readLong()
            val (from, to) = CurrencySelection.sanitize(data.readUTF(), data.readUTF(), table)
            val validEtag = etag.takeIf { version == VERSION }
            return ExchangeCache(table, validEtag, lastAutoAttempt, lastSuccess, from, to)
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
                data.writeUTF(table.rateDateOf(code) ?: table.rateDate)
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

    private fun readTable(data: DataInputStream, perRateDates: Boolean): RateTable {
        val rateDate = data.readUTF()
        if (!RateTable.isValidRateDate(rateDate)) throw CorruptionException("Invalid rate date $rateDate")
        val fetchedAt = data.readLong()
        val sourceName = data.readUTF()
        val source = RateSourceKind.entries.firstOrNull { it.name == sourceName }
            ?: throw CorruptionException("Unknown rate source $sourceName")
        val rateCount = readCount(data)
        val rates = LinkedHashMap<String, BigDecimal>(rateCount)
        val dates = HashMap<String, String>(rateCount)
        repeat(rateCount) {
            val code = data.readUTF()
            rates[code] = data.readUTF().toBigDecimalOrNull() ?: throw CorruptionException("Invalid rate for $code")
            dates[code] = if (perRateDates) data.readUTF() else rateDate
        }
        val nameCount = readCount(data)
        val names = HashMap<String, String>(nameCount)
        repeat(nameCount) { names[data.readUTF()] = data.readUTF() }
        return RateTable.create(rates, dates, names, source, fetchedAt)
            ?: throw CorruptionException("Invalid cached rate table")
    }

    private fun readCount(data: DataInputStream): Int {
        val count = data.readInt()
        if (count !in 0..MAX_ENTRIES) throw CorruptionException("Invalid entry count $count")
        return count
    }
}
