package com.scalc.calculator.exchange.data

import com.scalc.calculator.exchange.model.LivePayloads
import com.scalc.calculator.exchange.model.RateSourceKind
import com.scalc.calculator.exchange.model.SampleRates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class HttpRatesClientTest {

    private val ok = { body: String, etag: String? -> HttpResponse(200, body, etag) }

    /** Serves the standard sample responses; [rates], [currencies] and [ecb] can be overridden per test. */
    private fun transport(
        rates: () -> HttpResponse = { ok(SampleRates.FRANKFURTER_RATES_JSON, "W/\"r-2026-11-12\"") },
        currencies: () -> HttpResponse = { ok(SampleRates.FRANKFURTER_CURRENCIES_JSON, null) },
        ecb: () -> HttpResponse = { ok(SampleRates.ECB_DAILY_XML, "\"ecb\"") },
    ) = FakeTransport { url, _ ->
        when (url) {
            HttpRatesClient.RATES_URL -> rates()
            HttpRatesClient.CURRENCIES_URL -> currencies()
            HttpRatesClient.ECB_DAILY_URL -> ecb()
            else -> throw AssertionError("Unexpected URL $url")
        }
    }

    private fun TestScope.client(transport: HttpTransport) =
        HttpRatesClient(transport, UnconfinedTestDispatcher(testScheduler))

    private suspend inline fun <reified T : Throwable> assertThrows(crossinline block: suspend () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw AssertionError("Expected ${T::class.java.simpleName} but got $e", e)
        }
        fail("Expected ${T::class.java.simpleName}")
        throw IllegalStateException()
    }

    @Test
    fun usesFrankfurtersDefaultBlendedFeed() {
        assertEquals("https://api.frankfurter.dev/v2/rates", HttpRatesClient.RATES_URL)
        assertEquals("https://api.frankfurter.dev/v2/currencies", HttpRatesClient.CURRENCIES_URL)
        assertEquals("https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml", HttpRatesClient.ECB_DAILY_URL)
    }

    @Test
    fun downloadsBlendedFiatRatesWithNamesAndDates() = runTest {
        val transport = transport()
        val result = client(transport).fetch(etag = null)

        result as FetchResult.Downloaded
        assertEquals("W/\"r-2026-11-12\"", result.etag)
        val table = result.table
        assertEquals(RateSourceKind.FRANKFURTER_BLENDED, table.source)
        assertEquals("2026-11-12", table.rateDate)
        assertEquals(0L, table.fetchedAtMillis)
        // Crypto-assets (ADA, BTC, USDT) and the metal XAU are dropped; EUR is the base with rate 1.
        assertEquals(SampleRates.FRANKFURTER_FIAT, table.currencies.map { it.code })
        assertEquals(BigDecimal.ONE, table.rateOf("EUR"))
        assertEquals(0, BigDecimal("24.378").compareTo(table.rateOf("CZK")))
        assertEquals("Czech Koruna", table.currencyOf("CZK")?.name)
        assertEquals("United States Dollar", table.currencyOf("USD")?.name)
        // PLN is missing from the provider's list: known to the platform, so kept with its name.
        assertEquals("Polish Zloty", table.currencyOf("PLN")?.name)
        // Per-currency dates.
        assertEquals("2026-11-11", table.rateDateOf("TTD"))
        assertEquals("2026-11-12", table.rateDateOf("USD"))
        assertEquals("2026-11-12", table.rateDateOf("EUR"))
    }

    @Test
    fun requestsAreFixedHttpsUrlsWithGenericHeaders() = runTest {
        val transport = transport()
        client(transport).fetch(etag = null)

        assertEquals(listOf(HttpRatesClient.RATES_URL, HttpRatesClient.CURRENCIES_URL), transport.requests.map { it.first })
        transport.requests.forEach { (url, headers) ->
            assertTrue(url.startsWith("https://"))
            assertEquals("application/json", headers["Accept"])
            assertEquals("CalculatorExchange/1.0 (Android)", headers["User-Agent"])
        }
        assertFalse(transport.requests.first().second.containsKey("If-None-Match"))
    }

    @Test
    fun conditionalRequestReturnsNotModified() = runTest {
        val transport = transport(rates = { HttpResponse(304, "", "\"v1\"") })
        val result = client(transport).fetch(etag = "\"v1\"")

        assertSame(FetchResult.NotModified, result)
        assertEquals(1, transport.requests.size)
        assertEquals("\"v1\"", transport.requests.single().second["If-None-Match"])
    }

    @Test
    fun withoutTheCurrencyListOnlyPlatformKnownFiatCurrenciesAreKept() = runTest {
        listOf<() -> HttpResponse>(
            { throw IOException("reset") },
            { HttpResponse(500, "") },
            { ok("<html>oops</html>", null) },
        ).forEach { failure ->
            val table = (client(transport(currencies = failure)).fetch(etag = null) as FetchResult.Downloaded).table
            assertEquals(RateSourceKind.FRANKFURTER_BLENDED, table.source)
            // ADA, BTC (unknown to java.util.Currency) and XAU (a metal) are dropped.
            assertEquals(SampleRates.FRANKFURTER_FIAT, table.currencies.map { it.code })
            // Names come from the platform.
            assertEquals("Czech Koruna", table.currencyOf("CZK")?.name)
            assertEquals("US Dollar", table.currencyOf("USD")?.name)
            assertEquals("Euro", table.currencyOf("EUR")?.name)
        }
    }

    @Test
    fun liveResponsesGiveAllFiatCurrenciesWithTheirDates() = runTest {
        val transport = transport(
            rates = { ok(LivePayloads.RATES_JSON, "\"067e18bbb2b31b6ee303df32523901fd\"") },
            currencies = { ok(LivePayloads.CURRENCIES_JSON, null) },
        )
        val table = (client(transport).fetch(etag = null) as FetchResult.Downloaded).table
        val catalog = FrankfurterParser.parseCurrencies(LivePayloads.CURRENCIES_JSON)

        assertEquals(LivePayloads.FIAT_COUNT, table.currencies.size)
        assertEquals(catalog.filterValues { it.type == "currency" }.keys, table.rates.keys)
        listOf("ADA", "BTC", "ETH", "SOL", "XRP", "USDT", "XAU", "XAG", "XPD", "XPT").forEach {
            assertFalse("$it must not be offered", table.contains(it))
        }
        // Fiat codes unknown to the platform stay, with the provider's English name.
        assertEquals("COMESA Dollar", table.currencyOf("CMD")?.name)
        assertTrue(table.contains("CNH"))
        assertEquals(LivePayloads.NEWEST_DATE, table.rateDate)
        assertEquals(LivePayloads.FIAT_OF_NEWEST_DATE, table.rateDates.values.count { it == LivePayloads.NEWEST_DATE })
        assertEquals(LivePayloads.FIAT_COUNT - LivePayloads.FIAT_OF_NEWEST_DATE, table.rateDates.values.count { it == "2026-11-11" })
        assertEquals("2026-11-11", table.rateDateOf("TTD"))
        assertEquals(0, BigDecimal("1.1144").compareTo(table.rateOf("USD")))
    }

    @Test
    fun liveRatesWithoutTheCurrencyListDropCryptoMetalsAndUnknownCodes() = runTest {
        val transport = transport(
            rates = { ok(LivePayloads.RATES_JSON, null) },
            currencies = { throw IOException("timeout") },
        )
        val table = (client(transport).fetch(etag = null) as FetchResult.Downloaded).table
        val fiat = FrankfurterParser.parseCurrencies(LivePayloads.CURRENCIES_JSON).filterValues { it.type == "currency" }.keys

        // Only codes that the provider also lists as fiat: no crypto-asset or metal slips through.
        assertTrue(table.rates.keys.all { it in fiat })
        listOf("ADA", "BTC", "ETH", "SOL", "XRP", "XAU", "XAG", "XPD", "XPT").forEach { assertFalse(it, table.contains(it)) }
        // Not ISO 4217, hence unknown to the platform: dropped without the provider's list.
        listOf("CMD", "CNH", "GGP", "IMP", "JEP").forEach { assertFalse(it, table.contains(it)) }
        listOf("EUR", "USD", "GBP", "CZK", "JPY", "CHF", "PLN", "TTD").forEach { assertTrue(it, table.contains(it)) }
        assertTrue("only ${table.rates.size} currencies", table.rates.size >= 140)
        assertEquals("US Dollar", table.currencyOf("USD")?.name)
    }

    @Test
    fun liveResponsesFitTheResponseSizeLimitWithHeadroom() {
        val largest = maxOf(LivePayloads.RATES_JSON.toByteArray().size, LivePayloads.CURRENCIES_JSON.toByteArray().size)
        assertTrue(UrlConnectionTransport.MAX_BODY_BYTES >= 20 * largest)
    }

    @Test
    fun fallsBackToEcbWhenFrankfurterFails() = runTest {
        val transport = transport(rates = { HttpResponse(503, """{"status":503,"message":"down"}""") })
        val result = client(transport).fetch(etag = "\"v1\"")

        result as FetchResult.Downloaded
        assertNull(result.etag)
        assertEquals(RateSourceKind.ECB_DIRECT, result.table.source)
        assertEquals("2026-11-11", result.table.rateDate)
        assertEquals(setOf("2026-11-11"), result.table.rateDates.values.toSet())
        assertEquals(0, BigDecimal("1.1127").compareTo(result.table.rateOf("USD")))
        assertEquals("US Dollar", result.table.currencyOf("USD")?.name)
        assertEquals(HttpRatesClient.ECB_DAILY_URL, transport.requests.last().first)
        assertFalse(transport.requests.last().second.containsKey("If-None-Match"))
    }

    @Test
    fun fallsBackToEcbWhenFrankfurterDataIsInvalid() = runTest {
        val transport = transport(rates = { ok("<html>oops</html>", null) })
        val result = client(transport).fetch(etag = null) as FetchResult.Downloaded
        assertEquals(RateSourceKind.ECB_DIRECT, result.table.source)
    }

    @Test
    fun fallsBackToEcbWhenFrankfurterHasNoFiatRates() = runTest {
        val cryptoOnly = """[{"date":"2026-11-12","base":"EUR","quote":"BTC","rate":0.0000151}]"""
        val result = client(transport(rates = { ok(cryptoOnly, null) })).fetch(etag = null) as FetchResult.Downloaded
        assertEquals(RateSourceKind.ECB_DIRECT, result.table.source)
    }

    @Test
    fun bothSourcesHttpErrorThrowsServerException() = runTest {
        val transport = transport(
            rates = { HttpResponse(500, """{"status":500,"message":"boom"}""") },
            ecb = { HttpResponse(502, "") },
        )
        val error = assertThrows<RatesServerException> { client(transport).fetch(null) }
        assertEquals(500, error.statusCode)
        assertTrue(error.message!!.contains("boom"))
        assertEquals(1, error.suppressed.size)
    }

    @Test
    fun bothSourcesOfflineThrowsIOException() = runTest {
        val transport = FakeTransport { _, _ -> throw IOException("Unable to resolve host") }
        assertThrows<IOException> { client(transport).fetch(null) }
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun bothSourcesMalformedThrowsFormatException() = runTest {
        val transport = transport(rates = { ok("{not json", null) }, ecb = { ok("<html/>", null) })
        assertThrows<RatesFormatException> { client(transport).fetch(null) }
    }

    @Test
    fun unexpectedNotModifiedWithoutValidatorIsNotTrusted() = runTest {
        val transport = transport(rates = { HttpResponse(304, "") })
        val result = client(transport).fetch(etag = null) as FetchResult.Downloaded
        assertEquals(RateSourceKind.ECB_DIRECT, result.table.source)
    }

    @Test
    fun fiatSelectionRules() {
        val catalog = mapOf(
            "USD" to ProviderCurrency("United States Dollar", "currency"),
            "BTC" to ProviderCurrency("Bitcoin", "crypto"),
            "XAU" to ProviderCurrency("Gold", "metal"),
            "CMD" to ProviderCurrency("COMESA Dollar", "currency"),
            "GBP" to ProviderCurrency("British Pound", null),
        )
        val codes = listOf("BTC", "CMD", "CZK", "EUR", "GBP", "USD", "XAU", "XAG", "ZZZ")
        // Typed entries decide; untyped (GBP) or missing (CZK, XAG, ZZZ) ones follow the platform.
        assertEquals(setOf("CMD", "CZK", "EUR", "GBP", "USD"), FiatCurrencies.select(codes, catalog))
        assertEquals(setOf("CZK", "EUR", "GBP", "USD"), FiatCurrencies.select(codes, null))
        assertTrue(FiatCurrencies.isPlatformFiat("JPY"))
        assertFalse(FiatCurrencies.isPlatformFiat("XAU"))
        assertFalse(FiatCurrencies.isPlatformFiat("ADA"))
    }

    @Test
    fun urlConnectionTransportRejectsNonHttpsUrls() {
        val transport = UrlConnectionTransport()
        listOf("http://api.frankfurter.dev/v2/rates", "ftp://example.org/x", "not a url").forEach { url ->
            try {
                transport.get(url, emptyMap())
                fail("Expected IOException for $url")
            } catch (expected: IOException) {
                // expected: rejected before any connection is made
            }
        }
    }
}
