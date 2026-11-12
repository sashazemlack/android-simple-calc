package com.scalc.calculator.exchange.data

import com.scalc.calculator.exchange.model.CurrencyCodes
import com.scalc.calculator.exchange.model.RateTable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale

/**
 * Rates as published: units per 1 EUR, always including EUR = 1 and at least one other
 * well-formed code with a positive rate. [rateDates] holds the provider's effective date of every
 * code in [rates]; [rateDate] is the newest of them (and the date of EUR).
 */
data class ParsedRates(
    val rateDate: String,
    val rates: Map<String, BigDecimal>,
    val rateDates: Map<String, String>,
)

/**
 * One entry of Frankfurter's `/v2/currencies`: the English [name] and the [type] (`"currency"` for
 * fiat money, `"crypto"`, `"metal"`); either is null when the entry does not provide it.
 */
data class ProviderCurrency(val name: String?, val type: String?) {
    companion object {
        /** [type] of fiat currencies. */
        const val TYPE_FIAT = "currency"
    }
}

/**
 * Parsers for the Frankfurter v2 JSON API (`/v2/rates`, `/v2/currencies`) using the platform's
 * org.json. Invalid rows are dropped; a response without usable rows is a [RatesFormatException].
 */
object FrankfurterParser {

    private const val MAX_NAME_LENGTH = 100
    private const val MAX_TYPE_LENGTH = 32

    /**
     * Parses `[{"date":"2026-11-12","base":"EUR","quote":"USD","rate":1.1127}, ...]`.
     *
     * Rows need base EUR, a three-letter quote, a `yyyy-MM-dd` date and a positive rate. Every row
     * keeps its own date (blended rates of different currencies may have different dates); if a
     * quote occurs more than once, its most recent row wins. Rates that are too old to be used
     * are dropped later by [RateTable.create], after crypto-assets and metals have been removed.
     */
    fun parseRates(body: String): ParsedRates {
        val array = parseArray(body)
        val rates = sortedMapOf<String, BigDecimal>()
        val dates = HashMap<String, String>()
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            if (row.optString("base").trim() != CurrencyCodes.EUR) continue
            val quote = row.optString("quote").trim()
            if (!CurrencyCodes.isWellFormed(quote) || quote == CurrencyCodes.EUR) continue
            val date = row.optString("date").trim()
            if (!RateTable.isValidRateDate(date)) continue
            val rate = decimalOrNull(row.opt("rate"))?.takeIf { it.signum() > 0 } ?: continue
            val previous = dates[quote]
            if (previous == null || date > previous) {
                rates[quote] = rate
                dates[quote] = date
            }
        }
        val latestDate = dates.values.maxOrNull() ?: throw RatesFormatException("No valid exchange-rate rows")
        rates[CurrencyCodes.EUR] = BigDecimal.ONE
        dates[CurrencyCodes.EUR] = latestDate
        return ParsedRates(latestDate, LinkedHashMap(rates), dates)
    }

    /**
     * Parses `[{"iso_code":"CZK","name":"Czech Koruna","type":"currency",...}, ...]` into
     * code (upper case) -> [ProviderCurrency]. Entries without a code are skipped; blank or
     * overlong names and types count as missing.
     */
    fun parseCurrencies(body: String): Map<String, ProviderCurrency> {
        val array = parseArray(body)
        val currencies = HashMap<String, ProviderCurrency>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val code = item.optString("iso_code").trim().uppercase(Locale.ROOT)
            if (code.isEmpty()) continue
            currencies[code] = ProviderCurrency(
                name = textOrNull(item.opt("name"), MAX_NAME_LENGTH),
                type = textOrNull(item.opt("type"), MAX_TYPE_LENGTH)?.lowercase(Locale.ROOT),
            )
        }
        return currencies
    }

    /** Extracts the `message` of an error body like `{"status":422,"message":"..."}`, if any. */
    fun errorMessage(body: String): String? = try {
        JSONObject(body.removePrefix(BOM)).optString("message").trim().takeIf { it.isNotEmpty() }
    } catch (e: JSONException) {
        null
    }

    private fun parseArray(body: String): JSONArray {
        val text = body.removePrefix(BOM).trim()
        if (text.isEmpty()) throw RatesFormatException("Empty response")
        return try {
            JSONArray(text)
        } catch (e: JSONException) {
            throw RatesFormatException("Malformed JSON array", e)
        }
    }

    private fun textOrNull(value: Any?, maxLength: Int): String? =
        (value as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= maxLength }

    /** Converts an org.json number (or numeric string) without going through binary rounding errors. */
    private fun decimalOrNull(value: Any?): BigDecimal? = when (value) {
        is BigDecimal -> value
        is BigInteger -> BigDecimal(value)
        is Int, is Long, is Short, is Byte -> BigDecimal.valueOf((value as Number).toLong())
        // Double.toString yields the shortest decimal that round-trips, i.e. the text sent by the server.
        is Double -> if (value.isFinite()) BigDecimal.valueOf(value) else null
        is Float -> if (value.isFinite()) value.toString().toBigDecimalOrNull() else null
        is Number -> value.toString().toBigDecimalOrNull()
        is String -> value.trim().toBigDecimalOrNull()
        else -> null
    }
}

/**
 * Small pure-Kotlin parser for the ECB daily reference-rate XML
 * (`<Cube time='2026-11-11'><Cube currency='USD' rate='1.1127'/>...</Cube>`).
 *
 * It only looks at `Cube` start tags, accepts single or double quotes, arbitrary whitespace,
 * any attribute order and an optional namespace prefix, and ignores comments. If the document
 * contains several dates (e.g. the 90-day history file), the most recent one is used.
 */
object EcbXmlParser {

    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val CUBE_TAG = Regex("""<\s*(?:[A-Za-z_][\w.-]*:)?Cube(?=[\s/>])([^>]*)>""")
    private val ATTRIBUTE = Regex("""([A-Za-z_][\w.:-]*)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

    fun parse(body: String): ParsedRates {
        val xml = COMMENT.replace(body.removePrefix(BOM), "")
        val byDate = HashMap<String, MutableMap<String, BigDecimal>>()
        var currentDate: String? = null
        for (tag in CUBE_TAG.findAll(xml)) {
            val attributes = parseAttributes(tag.groupValues[1])
            attributes["time"]?.let { time ->
                currentDate = time.trim().takeIf { RateTable.isValidRateDate(it) }
            }
            val date = currentDate ?: continue
            val code = attributes["currency"]?.trim()?.uppercase(Locale.ROOT) ?: continue
            if (!CurrencyCodes.isWellFormed(code) || code == CurrencyCodes.EUR) continue
            val rate = attributes["rate"]?.trim()?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: continue
            byDate.getOrPut(date) { sortedMapOf() }[code] = rate
        }
        val latest = byDate.keys.maxOrNull() ?: throw RatesFormatException("No exchange rates in ECB XML")
        val rates = sortedMapOf<String, BigDecimal>()
        rates.putAll(byDate.getValue(latest))
        rates[CurrencyCodes.EUR] = BigDecimal.ONE
        return ParsedRates(latest, LinkedHashMap(rates), rates.keys.associateWith { latest })
    }

    private fun parseAttributes(text: String): Map<String, String> =
        ATTRIBUTE.findAll(text).associate { match ->
            val value = match.groups[2]?.value ?: match.groups[3]?.value.orEmpty()
            match.groupValues[1].substringAfter(':') to value
        }
}

private const val BOM = "\uFEFF"
