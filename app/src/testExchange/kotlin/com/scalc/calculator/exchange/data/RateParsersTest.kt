package com.scalc.calculator.exchange.data

import com.scalc.calculator.exchange.model.LivePayloads
import com.scalc.calculator.exchange.model.SampleRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal

class RateParsersTest {

    private fun assertRate(expected: String, actual: BigDecimal?) {
        requireNotNull(actual) { "missing rate, expected $expected" }
        assertTrue("expected $expected but was $actual", BigDecimal(expected).compareTo(actual) == 0)
    }

    private fun assertFormatError(block: () -> Unit) {
        try {
            block()
            fail("Expected RatesFormatException")
        } catch (expected: RatesFormatException) {
            // expected
        }
    }

    // --- Frankfurter rates -------------------------------------------------------------------

    @Test
    fun parsesFrankfurterRatesWithPerRowDates() {
        val parsed = FrankfurterParser.parseRates(SampleRates.FRANKFURTER_RATES_JSON)
        assertEquals("2026-11-12", parsed.rateDate)
        // Every well-formed (three-letter) quote; crypto and metals are filtered later, by type.
        assertEquals(
            listOf("ADA", "AUD", "BTC", "CHF", "CZK", "EUR", "GBP", "JPY", "PLN", "TTD", "USD", "XAU"),
            parsed.rates.keys.toList(),
        )
        assertFalse(parsed.rates.containsKey("USDT"))
        assertRate("1.6222", parsed.rates["AUD"])
        assertRate("24.378", parsed.rates["CZK"])
        assertEquals(BigDecimal.ONE, parsed.rates["EUR"])
        assertRate("1.1127", parsed.rates["USD"])
        assertRate("7.5383", parsed.rates["TTD"])
        assertEquals("2026-11-12", parsed.rateDates["USD"])
        assertEquals("2026-11-11", parsed.rateDates["TTD"])
        assertEquals("2026-11-12", parsed.rateDates["EUR"])
        assertEquals(parsed.rates.keys, parsed.rateDates.keys)
    }

    @Test
    fun dropsInvalidRowsAndAddsEur() {
        val json = """
            [
              {"date":"2026-11-11","base":"EUR","quote":"USD","rate":1.1127},
              {"date":"2026-11-11","base":"EUR","quote":"GBP","rate":0},
              {"date":"2026-11-11","base":"EUR","quote":"JPY","rate":-177.18},
              {"date":"2026-11-11","base":"EUR","quote":"CHF"},
              {"date":"2026-11-11","base":"EUR","quote":"PLN","rate":"abc"},
              {"date":"2026-11-11","base":"EUR","quote":"CZK","rate":"24.378"},
              {"date":"2026-11-11","base":"USD","quote":"AUD","rate":1.45},
              {"date":"2026-11-11","base":"EUR","quote":"US","rate":1.0},
              {"date":"11/11/2026","base":"EUR","quote":"HUF","rate":385.1},
              {"date":"2026-11-11","base":"EUR","quote":"SEK","rate":null},
              null, 42, "text"
            ]
        """.trimIndent()
        val parsed = FrankfurterParser.parseRates(json)
        assertEquals(setOf("CZK", "EUR", "USD"), parsed.rates.keys)
        assertRate("24.378", parsed.rates["CZK"])
    }

    @Test
    fun toleratesAByteOrderMark() {
        val bom = Char(0xFEFF).toString()
        assertEquals("2026-11-12", FrankfurterParser.parseRates(bom + SampleRates.FRANKFURTER_RATES_JSON).rateDate)
        assertEquals("2026-11-11", EcbXmlParser.parse(bom + SampleRates.ECB_DAILY_XML).rateDate)
        assertEquals("Euro", FrankfurterParser.parseCurrencies(bom + SampleRates.FRANKFURTER_CURRENCIES_JSON)["EUR"]?.name)
    }

    @Test
    fun keepsOlderRowsWithTheirOwnDateAndTheNewestRowPerQuote() {
        val json = """
            [
              {"date":"2025-12-31","base":"EUR","quote":"BGN","rate":1.9558},
              {"date":"2026-11-10","base":"EUR","quote":"USD","rate":1.1000},
              {"date":"2026-11-11","base":"EUR","quote":"USD","rate":1.1127},
              {"date":"2026-11-09","base":"EUR","quote":"USD","rate":1.0900},
              {"date":"2026-11-11","base":"EUR","quote":"CZK","rate":24.378}
            ]
        """.trimIndent()
        val parsed = FrankfurterParser.parseRates(json)
        assertEquals("2026-11-11", parsed.rateDate)
        assertRate("1.1127", parsed.rates["USD"])
        assertEquals("2026-11-11", parsed.rateDates["USD"])
        // Kept here with its date; RateTable.create drops it as stale (see RateTableTest).
        assertEquals("2025-12-31", parsed.rateDates["BGN"])
    }

    @Test
    fun malformedOrEmptyFrankfurterResponsesAreFormatErrors() {
        assertFormatError { FrankfurterParser.parseRates("") }
        assertFormatError { FrankfurterParser.parseRates("   ") }
        assertFormatError { FrankfurterParser.parseRates("[") }
        assertFormatError { FrankfurterParser.parseRates("not json") }
        assertFormatError { FrankfurterParser.parseRates("""{"status":422,"message":"Invalid provider"}""") }
        assertFormatError { FrankfurterParser.parseRates("[]") }
        assertFormatError {
            FrankfurterParser.parseRates("""[{"date":"2026-11-11","base":"EUR","quote":"EUR","rate":1.0}]""")
        }
        assertFormatError {
            FrankfurterParser.parseRates("""[{"date":"2026-11-11","base":"EUR","quote":"USD","rate":-1}]""")
        }
    }

    @Test
    fun extractsErrorMessages() {
        assertEquals("Invalid provider", FrankfurterParser.errorMessage("""{"status":422,"message":"Invalid provider"}"""))
        assertNull(FrankfurterParser.errorMessage("<html>Bad gateway</html>"))
        assertNull(FrankfurterParser.errorMessage(""))
    }

    // --- Frankfurter currencies --------------------------------------------------------------

    @Test
    fun parsesCurrencyNamesAndTypes() {
        val currencies = FrankfurterParser.parseCurrencies(SampleRates.FRANKFURTER_CURRENCIES_JSON)
        assertEquals(ProviderCurrency("Czech Koruna", "currency"), currencies["CZK"])
        assertEquals(ProviderCurrency("United States Dollar", "currency"), currencies["USD"])
        assertEquals(ProviderCurrency("Bitcoin", "crypto"), currencies["BTC"])
        assertEquals(ProviderCurrency("Tether", "crypto"), currencies["USDT"])
        assertEquals(ProviderCurrency("Gold (Troy Ounce)", "metal"), currencies["XAU"])
        assertFalse(currencies.containsKey("PLN"))
    }

    @Test
    fun missingOrBlankNamesAndTypesAreNull() {
        val json = """
            [{"iso_code":"usd","name":"US Dollar","type":"Currency"},{"iso_code":"CZK"},
             {"iso_code":"GBP","name":"  ","type":null},{"name":"x","type":"currency"}]
        """.trimIndent()
        val currencies = FrankfurterParser.parseCurrencies(json)
        assertEquals(
            mapOf(
                "USD" to ProviderCurrency("US Dollar", "currency"),
                "CZK" to ProviderCurrency(null, null),
                "GBP" to ProviderCurrency(null, null),
            ),
            currencies,
        )
    }

    @Test
    fun malformedCurrenciesAreFormatErrors() {
        assertFormatError { FrankfurterParser.parseCurrencies("") }
        assertFormatError { FrankfurterParser.parseCurrencies("{}") }
    }

    // --- Saved live responses (2026-11-12) ---------------------------------------------------

    @Test
    fun parsesTheLiveBlendedFeed() {
        val parsed = FrankfurterParser.parseRates(LivePayloads.RATES_JSON)
        assertEquals(LivePayloads.NEWEST_DATE, parsed.rateDate)
        // 200 rows: 189 three-letter quotes (EUR included), 11 four-letter crypto quotes dropped.
        assertEquals(189, parsed.rates.size)
        assertRate("1.1144", parsed.rates["USD"])
        assertRate("2136851", parsed.rates["IRR"])
        // Exponent notation (1.5119e-05) is read exactly.
        assertRate("0.000015119", parsed.rates["BTC"])
        assertEquals("2026-11-11", parsed.rateDates["TTD"])
        assertEquals("2026-11-12", parsed.rateDates["CZK"])
        assertEquals(setOf("2026-11-11", "2026-11-12"), parsed.rateDates.values.toSet())
    }

    @Test
    fun parsesTheLiveCurrencyList() {
        val currencies = FrankfurterParser.parseCurrencies(LivePayloads.CURRENCIES_JSON)
        assertEquals(200, currencies.size)
        assertEquals(LivePayloads.FIAT_COUNT, currencies.values.count { it.type == ProviderCurrency.TYPE_FIAT })
        assertEquals(35, currencies.values.count { it.type == "crypto" })
        assertEquals(setOf("XAG", "XAU", "XPD", "XPT"), currencies.filterValues { it.type == "metal" }.keys)
        assertEquals("United States Dollar", currencies["USD"]?.name)
        assertEquals("COMESA Dollar", currencies["CMD"]?.name)
        assertTrue(currencies.values.all { it.name != null })
    }

    // --- ECB XML -----------------------------------------------------------------------------

    @Test
    fun parsesEcbDailyXmlWithSingleQuotes() {
        val parsed = EcbXmlParser.parse(SampleRates.ECB_DAILY_XML)
        assertEquals("2026-11-11", parsed.rateDate)
        assertEquals(setOf("AUD", "CHF", "CZK", "EUR", "GBP", "JPY", "PLN", "USD"), parsed.rates.keys)
        assertEquals(BigDecimal.ONE, parsed.rates["EUR"])
        assertRate("1.1127", parsed.rates["USD"])
        assertRate("177.18", parsed.rates["JPY"])
        // One date for the whole file.
        assertEquals(parsed.rates.keys, parsed.rateDates.keys)
        assertEquals(setOf("2026-11-11"), parsed.rateDates.values.toSet())
    }

    @Test
    fun parsesDoubleQuotesWhitespaceAttributeOrderAndPrefixes() {
        val xml = """
            <?xml version="1.0"?>
            <env:Envelope xmlns:env="x" xmlns:e="y">
              <!-- <Cube currency="XXX" rate="9"/> -->
              <e:Cube>
                <e:Cube   time = "2026-11-11" >
                  <e:Cube rate="1.1127"   currency="USD" />
                  <e:Cube
                      currency = 'CZK'
                      rate = '24.378'/>
                  <Cube currency="GBP" rate="0.83618"></Cube>
                  <Cube currency="BAD" rate="-1"/>
                  <Cube currency="NAN" rate="abc"/>
                </e:Cube>
              </e:Cube>
              <Cubes currency="SEK" rate="11.2"/>
            </env:Envelope>
        """.trimIndent()
        val parsed = EcbXmlParser.parse(xml)
        assertEquals("2026-11-11", parsed.rateDate)
        assertEquals(setOf("CZK", "EUR", "GBP", "USD"), parsed.rates.keys)
        assertRate("24.378", parsed.rates["CZK"])
        assertRate("0.83618", parsed.rates["GBP"])
    }

    @Test
    fun usesTheMostRecentDayOfAHistoryFile() {
        val xml = """
            <Cube>
              <Cube time='2026-11-10'><Cube currency='USD' rate='1.1100'/></Cube>
              <Cube time='2026-11-11'><Cube currency='USD' rate='1.1127'/><Cube currency='JPY' rate='177.18'/></Cube>
              <Cube time='2026-11-09'><Cube currency='USD' rate='1.1000'/></Cube>
            </Cube>
        """.trimIndent()
        val parsed = EcbXmlParser.parse(xml)
        assertEquals("2026-11-11", parsed.rateDate)
        assertRate("1.1127", parsed.rates["USD"])
        assertEquals(setOf("EUR", "JPY", "USD"), parsed.rates.keys)
    }

    @Test
    fun malformedEcbXmlIsAFormatError() {
        assertFormatError { EcbXmlParser.parse("") }
        assertFormatError { EcbXmlParser.parse("<html><body>Maintenance</body></html>") }
        // Rates without a date cannot be trusted.
        assertFormatError { EcbXmlParser.parse("<Cube><Cube currency='USD' rate='1.1127'/></Cube>") }
        assertFormatError { EcbXmlParser.parse("<Cube time='yesterday'><Cube currency='USD' rate='1.1'/></Cube>") }
        assertFormatError { EcbXmlParser.parse("<Cube time='2026-11-11'></Cube>") }
        assertFormatError { EcbXmlParser.parse("""{"rates":{}}""") }
    }
}
