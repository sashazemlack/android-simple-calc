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
            source = RateSourceKind.FRANKFURTER_BLENDED,
        )!!
        assertEquals("EUR", table.base)
        assertEquals(listOf("CZK", "EUR", "USD"), table.currencies.map { it.code })
        assertEquals(BigDecimal.ONE, table.rateOf("EUR"))
        assertEquals("US Dollar", table.currencyOf("USD")?.name)
        // No name available: the code itself is shown.
        assertEquals("CZK", table.currencyOf("CZK")?.name)
        assertEquals(0L, table.fetchedAtMillis)
        assertEquals(mapOf("CZK" to "2026-11-10", "EUR" to "2026-11-10", "USD" to "2026-11-10"), table.rateDates)
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
        // Per-currency dates: a rate without a valid date is unusable.
        assertNull(RateTable.create(mapOf("USD" to BigDecimal.ONE), emptyMap(), emptyMap(), RateSourceKind.FRANKFURTER_BLENDED))
        assertNull(
            RateTable.create(mapOf("USD" to BigDecimal.ONE), mapOf("USD" to "yesterday"), emptyMap(), RateSourceKind.FRANKFURTER_BLENDED),
        )
    }

    @Test
    fun keepsPerCurrencyDatesAndDropsStaleRows() {
        val rates = mapOf(
            "USD" to BigDecimal("1.1144"),
            "TTD" to BigDecimal("7.4987"),
            "ARS" to BigDecimal("1590.2"),
            "BGN" to BigDecimal("1.9558"),
            "VES" to BigDecimal("1007.85"),
        )
        val dates = mapOf(
            "USD" to "2026-11-12",
            "TTD" to "2026-11-11",
            // Exactly seven days older than the newest row: still used.
            "ARS" to "2026-11-05",
            // Eight days older: stale.
            "VES" to "2026-11-04",
            "BGN" to "2025-12-31",
        )
        val table = RateTable.create(rates, dates, emptyMap(), RateSourceKind.FRANKFURTER_BLENDED)!!

        assertEquals(setOf("ARS", "EUR", "TTD", "USD"), table.rates.keys)
        assertEquals("2026-11-12", table.rateDate)
        assertEquals("2026-11-11", table.rateDateOf("TTD"))
        assertEquals("2026-11-05", table.rateDateOf("ARS"))
        // The base has the newest date.
        assertEquals("2026-11-12", table.rateDateOf("EUR"))
        assertNull(table.rateDateOf("VES"))
        assertEquals(table.rates.keys, table.rateDates.keys)
    }

    @Test
    fun stalenessIsMeasuredAgainstTheNewestValidRow() {
        // The newest row is invalid (negative rate), so it does not count as "newest".
        val table = RateTable.create(
            rates = mapOf("USD" to BigDecimal("-1"), "TTD" to BigDecimal("7.4987")),
            rateDates = mapOf("USD" to "2026-12-31", "TTD" to "2026-11-11"),
            names = emptyMap(),
            source = RateSourceKind.FRANKFURTER_BLENDED,
        )!!
        assertEquals(setOf("EUR", "TTD"), table.rates.keys)
        assertEquals("2026-11-11", table.rateDate)
    }

    @Test
    fun pairRateDateIsTheOlderOfBothDates() {
        val table = SampleRates.table(rateDate = "2026-11-12", rateDates = mapOf("PLN" to "2026-11-11", "JPY" to "2026-11-10"))
        assertEquals("2026-11-12", table.pairRateDate("EUR", "USD"))
        assertEquals("2026-11-11", table.pairRateDate("PLN", "USD"))
        assertEquals("2026-11-11", table.pairRateDate("EUR", "PLN"))
        assertEquals("2026-11-10", table.pairRateDate("PLN", "JPY"))
        assertEquals("2026-11-10", table.pairRateDate("JPY", "JPY"))
        assertNull(table.pairRateDate("EUR", "XYZ"))
    }

    @Test
    fun rateDateValidation() {
        assertTrue(RateTable.isValidRateDate("2026-11-11"))
        assertFalse(RateTable.isValidRateDate("2026-11-1"))
        assertFalse(RateTable.isValidRateDate(""))
        assertFalse(RateTable.isValidRateDate("2026-00-10"))
        assertFalse(RateTable.isValidRateDate("2026-11-32"))
    }

    @Test
    fun epochDays() {
        assertEquals(0L, RateTable.epochDay("1970-01-01"))
        assertEquals(-1L, RateTable.epochDay("1969-12-31"))
        assertEquals(10_957L, RateTable.epochDay("2000-01-01"))
        assertEquals(20_769L, RateTable.epochDay("2026-11-12"))
        assertEquals(1L, RateTable.epochDay("2024-03-01")!! - RateTable.epochDay("2024-02-29")!!)
        assertEquals(1L, RateTable.epochDay("2026-01-01")!! - RateTable.epochDay("2025-12-31")!!)
        assertNull(RateTable.epochDay("12.11.2026"))
        // Every day of a few years, compared with the platform calendar.
        val calendar = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2023, 0, 1)
        }
        repeat(4 * 366) {
            val date = String.format(
                java.util.Locale.ROOT,
                "%04d-%02d-%02d",
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH) + 1,
                calendar.get(java.util.Calendar.DAY_OF_MONTH),
            )
            assertEquals(date, Math.floorDiv(calendar.timeInMillis, 86_400_000L), RateTable.epochDay(date))
            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
    }
}
