package com.scalc.calculator.exchange.data

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
    fun parsesFrankfurterRates() {
        val parsed = FrankfurterParser.parseRates(SampleRates.FRANKFURTER_RATES_JSON)
        assertEquals("2026-11-11", parsed.rateDate)
        assertEquals(listOf("AUD", "CHF", "CZK", "EUR", "GBP", "JPY", "PLN", "USD"), parsed.rates.keys.toList())
        assertRate("1.6222", parsed.rates["AUD"])
        assertRate("0.9234", parsed.rates["CHF"])
        assertRate("24.378", parsed.rates["CZK"])
        assertEquals(BigDecimal.ONE, parsed.rates["EUR"])
        assertRate("0.83618", parsed.rates["GBP"])
        assertRate("177.18", parsed.rates["JPY"])
        assertRate("4.4038", parsed.rates["PLN"])
        assertRate("1.1127", parsed.rates["USD"])
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
        assertEquals("2026-11-11", FrankfurterParser.parseRates(bom + SampleRates.FRANKFURTER_RATES_JSON).rateDate)
        assertEquals("2026-11-11", EcbXmlParser.parse(bom + SampleRates.ECB_DAILY_XML).rateDate)
    }

    @Test
    fun keepsOnlyTheLatestDate() {
        val json = """
            [
              {"date":"2025-12-31","base":"EUR","quote":"BGN","rate":1.9558},
              {"date":"2026-11-11","base":"EUR","quote":"USD","rate":1.1127},
              {"date":"2026-11-11","base":"EUR","quote":"CZK","rate":24.378}
            ]
        """.trimIndent()
        val parsed = FrankfurterParser.parseRates(json)
        assertEquals("2026-11-11", parsed.rateDate)
        assertFalse(parsed.rates.containsKey("BGN"))
        assertEquals(setOf("CZK", "EUR", "USD"), parsed.rates.keys)
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
    fun parsesCurrencyNamesForTableCodesOnly() {
        val codes = SampleRates.RATES.keys
        val names = FrankfurterParser.parseCurrencyNames(SampleRates.FRANKFURTER_CURRENCIES_JSON, codes)
        assertEquals("Czech Koruna", names["CZK"])
        assertEquals("Euro", names["EUR"])
        assertEquals("United States Dollar", names["USD"])
        // Historical currency not in the rate table.
        assertFalse(names.containsKey("BGN"))
        // Not listed by the provider: resolved later from the platform.
        assertFalse(names.containsKey("PLN"))
        assertTrue(names.keys.all { it in codes })
    }

    @Test
    fun skipsCurrencyEntriesWithoutName() {
        val json = """[{"iso_code":"usd","name":"US Dollar"},{"iso_code":"CZK"},{"iso_code":"GBP","name":"  "},{"name":"x"}]"""
        val names = FrankfurterParser.parseCurrencyNames(json, setOf("USD", "CZK", "GBP"))
        assertEquals(mapOf("USD" to "US Dollar"), names)
    }

    @Test
    fun malformedCurrenciesAreFormatErrors() {
        assertFormatError { FrankfurterParser.parseCurrencyNames("", setOf("USD")) }
        assertFormatError { FrankfurterParser.parseCurrencyNames("{}", setOf("USD")) }
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
