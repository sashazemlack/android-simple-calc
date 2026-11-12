package com.scalc.calculator.exchange.data

import com.scalc.calculator.exchange.model.RateSourceKind
import com.scalc.calculator.exchange.model.RateTable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import java.util.Currency as JavaCurrency

/**
 * Downloads the European Central Bank euro reference rates.
 *
 * 1. Primary: Frankfurter v2 restricted to the ECB provider ([RATES_URL]); supports conditional
 *    requests (`If-None-Match` -> 304). Currency names come from [CURRENCIES_URL].
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
        val providerNames = fetchProviderNames(parsed.rates.keys)
        val table = RateTable.create(
            rates = parsed.rates,
            rateDate = parsed.rateDate,
            names = CurrencyNames.resolve(parsed.rates.keys, providerNames),
            source = RateSourceKind.FRANKFURTER_ECB,
        ) ?: throw RatesFormatException("Unusable Frankfurter rate table")
        val newEtag = response.etag?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_ETAG_LENGTH }
        return FetchResult.Downloaded(table, newEtag)
    }

    /** Names are optional: any failure falls back to platform names in [CurrencyNames.resolve]. */
    private fun fetchProviderNames(codes: Set<String>): Map<String, String> = try {
        val response = transport.get(CURRENCIES_URL, mapOf("Accept" to JSON_ACCEPT, "User-Agent" to USER_AGENT))
        if (response.statusCode in 200..299) FrankfurterParser.parseCurrencyNames(response.body, codes) else emptyMap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyMap()
    }

    private fun fetchFromEcb(): RateTable {
        val response = transport.get(ECB_DAILY_URL, mapOf("Accept" to XML_ACCEPT, "User-Agent" to USER_AGENT))
        ensureSuccess(response)
        val parsed = EcbXmlParser.parse(response.body)
        return RateTable.create(
            rates = parsed.rates,
            rateDate = parsed.rateDate,
            names = CurrencyNames.resolve(parsed.rates.keys, emptyMap()),
            source = RateSourceKind.ECB_DIRECT,
        ) ?: throw RatesFormatException("Unusable ECB rate table")
    }

    private fun ensureSuccess(response: HttpResponse) {
        if (response.statusCode in 200..299) return
        val detail = FrankfurterParser.errorMessage(response.body)?.let { ": $it" }.orEmpty()
        throw RatesServerException(response.statusCode, "HTTP ${response.statusCode}$detail")
    }

    companion object {
        const val RATES_URL = "https://api.frankfurter.dev/v2/rates?providers=ECB"
        const val CURRENCIES_URL = "https://api.frankfurter.dev/v2/currencies?providers=ECB"
        const val ECB_DAILY_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"

        /** Generic client identification; deliberately contains no device or user information. */
        const val USER_AGENT = "CalculatorExchange/1.0 (Android)"
        private const val JSON_ACCEPT = "application/json"
        private const val XML_ACCEPT = "application/xml, text/xml"
        private const val MAX_ETAG_LENGTH = 512
    }
}

/** English display names for currency codes. */
object CurrencyNames {

    /** Name for every code in [codes]: the provider's name, else the platform's, else the code itself. */
    fun resolve(codes: Collection<String>, providerNames: Map<String, String>): Map<String, String> =
        codes.associateWith { code -> providerNames[code]?.takeIf { it.isNotBlank() } ?: platformName(code) ?: code }

    /** `java.util.Currency` English display name, or null if the platform does not know [code]. */
    fun platformName(code: String): String? =
        runCatching { JavaCurrency.getInstance(code).getDisplayName(Locale.ENGLISH) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != code }
}
