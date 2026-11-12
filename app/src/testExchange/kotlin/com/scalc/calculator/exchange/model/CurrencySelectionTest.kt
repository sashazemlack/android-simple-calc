package com.scalc.calculator.exchange.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencySelectionTest {

    private val table = SampleRates.table()

    @Test
    fun normalizeAcceptsKnownCodesCaseInsensitively() {
        assertEquals("CZK", CurrencySelection.normalize(" czk ", table))
        assertNull(CurrencySelection.normalize("BGN", table))
        assertNull(CurrencySelection.normalize("US", table))
        // Without rates any well-formed code is accepted.
        assertEquals("BGN", CurrencySelection.normalize("bgn", null))
        assertNull(CurrencySelection.normalize("12A", null))
    }

    @Test
    fun sanitizeKeepsAvailableCodes() {
        assertEquals("GBP" to "JPY", CurrencySelection.sanitize("GBP", "JPY", table))
        // Same currency on both sides is a legitimate user choice.
        assertEquals("CHF" to "CHF", CurrencySelection.sanitize("CHF", "CHF", table))
    }

    @Test
    fun sanitizeFallsBackToEurAndUsd() {
        assertEquals("EUR" to "USD", CurrencySelection.sanitize("BGN", "HRK", table))
        assertEquals("EUR" to "GBP", CurrencySelection.sanitize("BGN", "GBP", table))
        // EUR is taken by the destination, so the source falls back to USD.
        assertEquals("USD" to "EUR", CurrencySelection.sanitize("BGN", "EUR", table))
    }

    @Test
    fun sanitizeFallsBackToFirstCodesWhenUsdIsMissing() {
        val noUsd = SampleRates.table(codes = setOf("CZK", "GBP", "PLN"))
        assertEquals("EUR" to "CZK", CurrencySelection.sanitize("USD", "USD", noUsd))
        assertEquals("PLN" to "EUR", CurrencySelection.sanitize("PLN", "USD", noUsd))
    }

    @Test
    fun sanitizeWithoutTableOnlyRepairsMalformedCodes() {
        assertEquals("GBP" to "USD", CurrencySelection.sanitize("gbp", "", null))
        assertEquals("EUR" to "USD", CurrencySelection.sanitize("", "x", null))
    }
}
