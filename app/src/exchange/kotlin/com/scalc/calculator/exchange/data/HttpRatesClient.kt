package com.scalc.calculator.exchange.data

import com.scalc.calculator.exchange.model.CurrencyCodes
import com.scalc.calculator.exchange.model.CurrencyNames
import com.scalc.calculator.exchange.model.RateSourceKind
import com.scalc.calculator.exchange.model.RateTable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import kotlin.coroutines.cancellation.CancellationException
import java.util.Currency as JavaCurrency

/**
 * Downloads euro-based exchange rates of fiat currencies.
 *
 * 1. Primary: Frankfurter v2's default feed ([RATES_URL]), rates blended across its daily
 *    providers (mostly central banks), each currency with its own effective date. Supports
 *    conditional requests (`If-None-Match` -> 304). Currency names and types come from
 *    [CURRENCIES_URL]; crypto-assets and precious metals are dropped ([FiatCurrencies]).
 * 2. Fallback, when the primary fails for any reason: the ECB's own daily XML ([ECB_DAILY_URL]).
 *
 * Requests contain no user data (only fixed URLs, `Accept` and a generic `User-Agent`). Work runs
 * on [ioDispatcher]. If both sources fail, the primary failure is thrown with the fallback failure
 * attached as suppressed exception.
 */
class HttpRatesClient(
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RatesClient {

    override suspend fun fetch(etag: String?): FetchResult = withContext(ioDispatcher) {
        try {
            fetchFromFrankfurter(etag)
        } catch (e: CancellationException) {
            throw e
        } catch (primary: Exception) {
            try {
                FetchResult.Downloaded(fetchFromEcb(), etag = null)
            } catch (e: CancellationException) {
                throw e
            } catch (fallback: Exception) {
                primary.addSuppressed(fallback)
                throw primary
            }
        }
    }

    private fun fetchFromFrankfurter(etag: String?): FetchResult {
        val headers = buildMap {
            put("Accept", JSON_ACCEPT)
            put("User-Agent", USER_AGENT)
            if (etag != null) put("If-None-Match", etag)
        }
        val response = transport.get(RATES_URL, headers)
        if (response.statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            if (etag != null) return FetchResult.NotModified
            throw RatesServerException(response.statusCode, "Unexpected 304 for an unconditional request")
        }
        ensureSuccess(response)
        val parsed = FrankfurterParser.parseRates(response.body)
        val catalog = fetchCurrencies()
        val fiat = FiatCurrencies.select(parsed.rates.keys, catalog)
        val rates = parsed.rates.filterKeys { it in fiat }
        val providerNames = catalog.orEmpty().mapNotNull { (code, entry) -> entry.name?.let { code to it } }.toMap()
        val table = RateTable.create(
            rates = rates,
            rateDates = parsed.rateDates,
            names = CurrencyNames.englishNames(rates.keys, providerNames),
            source = RateSourceKind.FRANKFURTER_BLENDED,
        ) ?: throw RatesFormatException("Unusable Frankfurter rate table")
        val newEtag = response.etag?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_ETAG_LENGTH }
        return FetchResult.Downloaded(table, newEtag)
    }

    /**
     * Names and types of the provider's currencies, or null if they cannot be downloaded or read.
     * They are optional: [FiatCurrencies] and [CurrencyNames] then fall back to the platform.
     */
    private fun fetchCurrencies(): Map<String, ProviderCurrency>? = try {
        val response = transport.get(CURRENCIES_URL, mapOf("Accept" to JSON_ACCEPT, "User-Agent" to USER_AGENT))
        if (response.statusCode in 200..299) FrankfurterParser.parseCurrencies(response.body) else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun fetchFromEcb(): RateTable {
        val response = transport.get(ECB_DAILY_URL, mapOf("Accept" to XML_ACCEPT, "User-Agent" to USER_AGENT))
        ensureSuccess(response)
        val parsed = EcbXmlParser.parse(response.body)
        return RateTable.create(
            rates = parsed.rates,
            rateDates = parsed.rateDates,
            names = CurrencyNames.englishNames(parsed.rates.keys, emptyMap()),
            source = RateSourceKind.ECB_DIRECT,
        ) ?: throw RatesFormatException("Unusable ECB rate table")
    }

    private fun ensureSuccess(response: HttpResponse) {
        if (response.statusCode in 200..299) return
        val detail = FrankfurterParser.errorMessage(response.body)?.let { ": $it" }.orEmpty()
        throw RatesServerException(response.statusCode, "HTTP ${response.statusCode}$detail")
    }

    companion object {
        /** Frankfurter's default feed: rates blended across all daily providers. */
        const val RATES_URL = "https://api.frankfurter.dev/v2/rates"
        const val CURRENCIES_URL = "https://api.frankfurter.dev/v2/currencies"
        const val ECB_DAILY_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"

        /** Generic client identification; deliberately contains no device or user information. */
        const val USER_AGENT = "CalculatorExchange/1.0 (Android)"
        private const val JSON_ACCEPT = "application/json"
        private const val XML_ACCEPT = "application/xml, text/xml"
        private const val MAX_ETAG_LENGTH = 512
    }
}

/**
 * Decides which quoted codes are fiat currencies; crypto-assets (e.g. ADA, BTC) and precious
 * metals (e.g. XAU) are not offered.
 */
object FiatCurrencies {

    /** ISO 4217 codes known to `java.util.Currency` that are not money: precious metals, test, none. */
    private val NON_FIAT_ISO_CODES = setOf("XAG", "XAU", "XPD", "XPT", "XTS", "XXX")

    private val platformCodes: Set<String> by lazy {
        runCatching { JavaCurrency.getAvailableCurrencies().mapTo(HashSet()) { it.currencyCode } }.getOrDefault(emptySet())
    }

    /**
     * The fiat codes among [codes] (EUR always counts). With the provider's [catalog], a code is
     * fiat when its type is `"currency"`; a code the catalog does not describe, or every code when
     * the catalog is unavailable (null), is fiat when [isPlatformFiat].
     */
    fun select(codes: Collection<String>, catalog: Map<String, ProviderCurrency>?): Set<String> =
        codes.filterTo(sortedSetOf()) { code ->
            val type = catalog?.get(code)?.type
            when {
                code == CurrencyCodes.EUR -> true
                type != null -> type == ProviderCurrency.TYPE_FIAT
                else -> isPlatformFiat(code)
            }
        }

    /** True when the platform's `java.util.Currency` knows [code] and it is not a metal or technical code. */
    fun isPlatformFiat(code: String): Boolean = code !in NON_FIAT_ISO_CODES && code in platformCodes
}
