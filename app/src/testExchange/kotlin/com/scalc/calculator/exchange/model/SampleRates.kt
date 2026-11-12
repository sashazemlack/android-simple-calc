package com.scalc.calculator.exchange.model

import java.math.BigDecimal

/** Realistic ECB reference-rate fixtures shared by the Exchange unit tests. */
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

    /** A valid table with all sample currencies, or only [codes] (EUR is always added). */
    fun table(
        fetchedAtMillis: Long = 0L,
        rateDate: String = RATE_DATE,
        codes: Set<String> = RATES.keys,
        source: RateSourceKind = RateSourceKind.FRANKFURTER_ECB,
    ): RateTable = checkNotNull(
        RateTable.create(RATES.filterKeys { it in codes }, rateDate, NAMES, source, fetchedAtMillis),
    )

    /** Frankfurter `/v2/rates?providers=ECB` response for 2026-11-11 (subset of currencies). */
    val FRANKFURTER_RATES_JSON = """
        [
          {"date":"2026-11-11","base":"EUR","quote":"AUD","rate":1.6222},
          {"date":"2026-11-11","base":"EUR","quote":"CHF","rate":0.9234},
          {"date":"2026-11-11","base":"EUR","quote":"CZK","rate":24.378},
          {"date":"2026-11-11","base":"EUR","quote":"EUR","rate":1.0},
          {"date":"2026-11-11","base":"EUR","quote":"GBP","rate":0.83618},
          {"date":"2026-11-11","base":"EUR","quote":"JPY","rate":177.18},
          {"date":"2026-11-11","base":"EUR","quote":"PLN","rate":4.4038},
          {"date":"2026-11-11","base":"EUR","quote":"USD","rate":1.1127}
        ]
    """.trimIndent()

    /** Frankfurter `/v2/currencies?providers=ECB` response including historical currencies. */
    val FRANKFURTER_CURRENCIES_JSON = """
        [
          {"iso_code":"AUD","iso_numeric":"036","name":"Australian Dollar","symbol":"${'$'}","type":"currency","start_date":"1999-01-04","end_date":"2026-11-11"},
          {"iso_code":"BGN","iso_numeric":"975","name":"Bulgarian Lev","symbol":"лв","type":"currency","start_date":"2000-07-19","end_date":"2025-12-31"},
          {"iso_code":"CHF","iso_numeric":"756","name":"Swiss Franc","symbol":"CHF","type":"currency","start_date":"1999-01-04","end_date":"2026-11-11"},
          {"iso_code":"CZK","iso_numeric":"203","name":"Czech Koruna","symbol":"Kč","type":"currency","start_date":"1999-01-04","end_date":"2026-11-11"},
          {"iso_code":"EUR","iso_numeric":"978","name":"Euro","symbol":"€","type":"currency","start_date":"1999-01-04","end_date":"2026-11-11"},
          {"iso_code":"GBP","iso_numeric":"826","name":"British Pound","symbol":"£","type":"currency","start_date":"1999-01-04","end_date":"2026-11-11"},
          {"iso_code":"JPY","iso_numeric":"392","name":"Japanese Yen","symbol":"¥","type":"currency","start_date":"1999-01-04","end_date":"2026-11-11"},
          {"iso_code":"USD","iso_numeric":"840","name":"United States Dollar","symbol":"${'$'}","type":"currency","start_date":"1999-01-04","end_date":"2026-11-11"}
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
