package com.scalc.calculator.exchange.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class RateTableTest {

    @Test
    fun createNormalizesAndSortsCurrencies() {
        val table = RateTable.create(
            rates = mapOf("USD" to BigDecimal("1.1127"), "CZK" to BigDecimal("24.378"), "EUR" to BigDecimal("1.0")),
            rateDate = "2026-11-10",
            names = mapOf("USD" to " US Dollar ", "EUR" to "Euro"),
            source = RateSourceKind.FRANKFURTER_ECB,
        )!!
        assertEquals("EUR", table.base)
        assertEquals(listOf("CZK", "EUR", "USD"), table.currencies.map { it.code })
        assertEquals(BigDecimal.ONE, table.rateOf("EUR"))
        assertEquals("US Dollar", table.currencyOf("USD")?.name)
        // No name available: the code itself is shown.
        assertEquals("CZK", table.currencyOf("CZK")?.name)
        assertEquals(0L, table.fetchedAtMillis)
    }

    @Test
    fun createDropsInvalidRows() {
        val table = RateTable.create(
            rates = mapOf(
                "USD" to BigDecimal("1.1127"),
                "GBP" to BigDecimal.ZERO,
                "JPY" to BigDecimal("-1"),
                "usd" to BigDecimal("1.2"),
                "ABCD" to BigDecimal("2"),
            ),
            rateDate = "2026-11-10",
            names = emptyMap(),
            source = RateSourceKind.ECB_DIRECT,
        )!!
        assertEquals(setOf("EUR", "USD"), table.rates.keys)
        assertTrue(table.contains("USD"))
        assertFalse(table.contains("GBP"))
    }

    @Test
    fun createRejectsUnusableTables() {
        assertNull(RateTable.create(mapOf("EUR" to BigDecimal.ONE), "2026-11-10", emptyMap(), RateSourceKind.ECB_DIRECT))
        assertNull(RateTable.create(emptyMap(), "2026-11-10", emptyMap(), RateSourceKind.ECB_DIRECT))
        assertNull(RateTable.create(mapOf("USD" to BigDecimal.ONE), "10.11.2026", emptyMap(), RateSourceKind.ECB_DIRECT))
        assertNull(RateTable.create(mapOf("USD" to BigDecimal.ONE), "2026-13-01", emptyMap(), RateSourceKind.ECB_DIRECT))
    }

    @Test
    fun rateDateValidation() {
        assertTrue(RateTable.isValidRateDate("2026-11-11"))
        assertFalse(RateTable.isValidRateDate("2026-11-1"))
        assertFalse(RateTable.isValidRateDate(""))
        assertFalse(RateTable.isValidRateDate("2026-00-10"))
        assertFalse(RateTable.isValidRateDate("2026-11-32"))
    }
}
