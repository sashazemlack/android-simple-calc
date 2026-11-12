package com.scalc.calculator.exchange.model

import java.math.BigDecimal

/** Realistic rate fixtures shared by the Exchange unit tests. */
object SampleRates {

    const val RATE_DATE = "2026-11-10"

    /** Units per 1 EUR. */
    val RATES: Map<String, BigDecimal> = linkedMapOf(
        "AUD" to BigDecimal("1.6222"),
        "CHF" to BigDecimal("0.9234"),
        "CZK" to BigDecimal("24.378"),
        "EUR" to BigDecimal.ONE,
        "GBP" to BigDecimal("0.83618"),
        "JPY" to BigDecimal("177.18"),
        "PLN" to BigDecimal("4.4038"),
        "USD" to BigDecimal("1.1127"),
    )

    val NAMES: Map<String, String> = mapOf(
        "AUD" to "Australian Dollar",
        "CHF" to "Swiss Franc",
        "CZK" to "Czech Koruna",
        "EUR" to "Euro",
        "GBP" to "British Pound",
        "JPY" to "Japanese Yen",
        "PLN" to "Polish Zloty",
        "USD" to "US Dollar",
    )

    /**
     * A valid table with all sample currencies, or only [codes] (EUR is always added). Every rate
     * has [rateDate] unless [rateDates] gives individual dates.
     */
    fun table(
        fetchedAtMillis: Long = 0L,
        rateDate: String = RATE_DATE,
        codes: Set<String> = RATES.keys,
        source: RateSourceKind = RateSourceKind.FRANKFURTER_BLENDED,
        rateDates: Map<String, String> = emptyMap(),
    ): RateTable {
        val rates = RATES.filterKeys { it in codes }
        val dates = rates.keys.associateWith { rateDates[it] ?: rateDate }
        return checkNotNull(RateTable.create(rates, dates, NAMES, source, fetchedAtMillis))
    }

    /**
     * Frankfurter `/v2/rates` (blended default feed) for 2026-11-12, trimmed: like the live feed it
     * has per-row dates (TTD from the previous day), crypto-assets with three-letter (ADA, BTC)
     * and four-letter (USDT) codes, a precious metal (XAU) and a row for EUR itself.
     */
    val FRANKFURTER_RATES_JSON = """
        [
          {"date":"2026-11-11","base":"EUR","quote":"ADA","rate":6.4139},
          {"date":"2026-11-12","base":"EUR","quote":"AUD","rate":1.6222},
          {"date":"2026-11-11","base":"EUR","quote":"BTC","rate":0.0000108},
          {"date":"2026-11-12","base":"EUR","quote":"CHF","rate":0.9234},
          {"date":"2026-11-12","base":"EUR","quote":"CZK","rate":24.378},
          {"date":"2026-11-12","base":"EUR","quote":"EUR","rate":1.0},
          {"date":"2026-11-12","base":"EUR","quote":"GBP","rate":0.83618},
          {"date":"2026-11-12","base":"EUR","quote":"JPY","rate":177.18},
          {"date":"2026-11-12","base":"EUR","quote":"PLN","rate":4.4038},
          {"date":"2026-11-11","base":"EUR","quote":"TTD","rate":7.5383},
          {"date":"2026-11-12","base":"EUR","quote":"USD","rate":1.1127},
          {"date":"2026-11-11","base":"EUR","quote":"USDT","rate":1.1131},
          {"date":"2026-11-12","base":"EUR","quote":"XAU","rate":0.00028}
        ]
    """.trimIndent()

    /** Fiat codes of [FRANKFURTER_RATES_JSON], including EUR. */
    val FRANKFURTER_FIAT = listOf("AUD", "CHF", "CZK", "EUR", "GBP", "JPY", "PLN", "TTD", "USD")

    /**
     * Frankfurter `/v2/currencies` for [FRANKFURTER_RATES_JSON] (live format). PLN is missing on
     * purpose (unknown to the provider list: platform rules apply).
     */
    val FRANKFURTER_CURRENCIES_JSON = """
        [
          {"iso_code":"ADA","iso_numeric":null,"name":"Cardano","symbol":"₳","type":"crypto","start_date":"2017-12-01","end_date":"2026-11-11"},
          {"iso_code":"AUD","iso_numeric":"036","name":"Australian Dollar","symbol":"${'$'}","type":"currency","start_date":"1983-12-12","end_date":"2026-11-12"},
          {"iso_code":"BTC","iso_numeric":null,"name":"Bitcoin","symbol":"₿","type":"crypto","start_date":"2010-07-18","end_date":"2026-11-11"},
          {"iso_code":"CHF","iso_numeric":"756","name":"Swiss Franc","symbol":"CHF","type":"currency","start_date":"1980-01-02","end_date":"2026-11-12"},
          {"iso_code":"CZK","iso_numeric":"203","name":"Czech Koruna","symbol":"Kč","type":"currency","start_date":"1993-01-04","end_date":"2026-11-12"},
          {"iso_code":"EUR","iso_numeric":"978","name":"Euro","symbol":"€","type":"currency","start_date":"1999-01-01","end_date":"2026-11-12"},
          {"iso_code":"GBP","iso_numeric":"826","name":"British Pound","symbol":"£","type":"currency","start_date":"1975-01-02","end_date":"2026-11-12"},
          {"iso_code":"JPY","iso_numeric":"392","name":"Japanese Yen","symbol":"¥","type":"currency","start_date":"1971-01-04","end_date":"2026-11-12"},
          {"iso_code":"TTD","iso_numeric":"780","name":"Trinidad and Tobago Dollar","symbol":"TT${'$'}","type":"currency","start_date":"1993-04-13","end_date":"2026-11-11"},
          {"iso_code":"USD","iso_numeric":"840","name":"United States Dollar","symbol":"${'$'}","type":"currency","start_date":"1948-06-21","end_date":"2026-11-12"},
          {"iso_code":"USDT","iso_numeric":null,"name":"Tether","symbol":"₮","type":"crypto","start_date":"2017-11-28","end_date":"2026-11-11"},
          {"iso_code":"XAU","iso_numeric":"959","name":"Gold (Troy Ounce)","symbol":"oz t","type":"metal","start_date":"1999-01-05","end_date":"2026-11-12"}
        ]
    """.trimIndent()

    /** The ECB's own daily file (single quotes, as published). */
    val ECB_DAILY_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gesmes:Envelope xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01" xmlns="http://www.ecb.int/vocabulary/2002-08-01/eurofxref">
        	<gesmes:subject>Reference rates</gesmes:subject>
        	<gesmes:Sender>
        		<gesmes:name>European Central Bank</gesmes:name>
        	</gesmes:Sender>
        	<Cube>
        		<Cube time='2026-11-11'>
        			<Cube currency='USD' rate='1.1127'/>
        			<Cube currency='JPY' rate='177.18'/>
        			<Cube currency='CZK' rate='24.378'/>
        			<Cube currency='GBP' rate='0.83618'/>
        			<Cube currency='PLN' rate='4.4038'/>
        			<Cube currency='CHF' rate='0.9234'/>
        			<Cube currency='AUD' rate='1.6222'/>
        		</Cube>
        	</Cube>
        </gesmes:Envelope>
    """.trimIndent()
}

/**
 * Untrimmed responses of the live Frankfurter API, saved on 2026-11-12 (test resources):
 * `GET https://api.frankfurter.dev/v2/rates` (200 rows: 161 fiat incl. EUR, 35 crypto, 4 metals;
 * 146 fiat rows dated 2026-11-12 and 15 dated 2026-11-11) and
 * `GET https://api.frankfurter.dev/v2/currencies` (all types; 161 fiat).
 */
object LivePayloads {
    const val NEWEST_DATE = "2026-11-12"
    const val FIAT_COUNT = 161
    const val FIAT_OF_NEWEST_DATE = 146

    val RATES_JSON: String by lazy { resource("/frankfurter/rates_2026-11-12.json") }
    val CURRENCIES_JSON: String by lazy { resource("/frankfurter/currencies_2026-11-12.json") }

    private fun resource(path: String): String =
        checkNotNull(LivePayloads::class.java.getResourceAsStream(path)) { "Missing test resource $path" }
            .use { String(it.readBytes(), Charsets.UTF_8) }
}
