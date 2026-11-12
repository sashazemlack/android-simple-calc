package com.scalc.calculator.exchange.data

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

    /** Serves the standard sample responses; [rates] and [ecb] can be overridden per test. */
    private fun transport(
        rates: () -> HttpResponse = { ok(SampleRates.FRANKFURTER_RATES_JSON, "W/\"r-2026-11-11\"") },
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
    fun downloadsFrankfurterRatesWithNames() = runTest {
        val transport = transport()
        val result = client(transport).fetch(etag = null)

        result as FetchResult.Downloaded
        assertEquals("W/\"r-2026-11-11\"", result.etag)
        val table = result.table
        assertEquals(RateSourceKind.FRANKFURTER_ECB, table.source)
        assertEquals("2026-11-11", table.rateDate)
        assertEquals(0L, table.fetchedAtMillis)
        assertEquals(listOf("AUD", "CHF", "CZK", "EUR", "GBP", "JPY", "PLN", "USD"), table.currencies.map { it.code })
        assertEquals(0, BigDecimal("24.378").compareTo(table.rateOf("CZK")))
        assertEquals("Czech Koruna", table.currencyOf("CZK")?.name)
        assertEquals("United States Dollar", table.currencyOf("USD")?.name)
        // PLN is missing from the provider's list: platform name fallback.
        assertEquals("Polish Zloty", table.currencyOf("PLN")?.name)
        assertNull(table.currencyOf("BGN"))
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
    fun fallsBackToEcbWhenFrankfurterFails() = runTest {
        val transport = transport(rates = { HttpResponse(503, """{"status":503,"message":"down"}""") })
        val result = client(transport).fetch(etag = "\"v1\"")

        result as FetchResult.Downloaded
        assertNull(result.etag)
        assertEquals(RateSourceKind.ECB_DIRECT, result.table.source)
        assertEquals("2026-11-11", result.table.rateDate)
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
    fun currencyNameFailureStillDeliversRates() = runTest {
        val transport = transport(currencies = { throw IOException("reset") })
        val table = (client(transport).fetch(etag = null) as FetchResult.Downloaded).table
        assertEquals(RateSourceKind.FRANKFURTER_ECB, table.source)
        assertEquals("Czech Koruna", table.currencyOf("CZK")?.name)
        assertEquals("Euro", table.currencyOf("EUR")?.name)
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
