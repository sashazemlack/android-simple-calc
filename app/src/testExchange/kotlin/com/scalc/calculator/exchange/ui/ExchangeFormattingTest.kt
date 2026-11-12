package com.scalc.calculator.exchange.ui

import com.scalc.calculator.exchange.model.Currency
import com.scalc.calculator.exchange.model.CurrencyMath
import com.scalc.calculator.exchange.model.RateSourceKind
import com.scalc.calculator.exchange.model.RateTable
import com.scalc.calculator.exchange.model.SampleRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class ExchangeFormattingTest {

    private fun format(value: String) = ExchangeFormatting.convertedAmount(BigDecimal(value))

    @Test
    fun amountsOfOneAndMoreUseTwoDecimals() {
        assertEquals("1.11", format("1.1127"))
        assertEquals("13,909.31", format("13909.30635"))
        assertEquals("1,234,567.89", format("1234567.891"))
        assertEquals("2.50", format("2.50"))
        assertEquals("30,472.50", format("30472.5"))
        assertEquals("100.00", format("99.999"))
        assertEquals("5.00", format("5"))
    }

    @Test
    fun zeroHasTwoDecimals() {
        assertEquals("0.00", format("0"))
        assertEquals("0.00", format("0.000"))
    }

    @Test
    fun smallAmountsKeepFourSignificantDigits() {
        assertEquals("0.8987", format("0.898714820526647"))
        assertEquals("0.5", format("0.50"))
        assertEquals("0.04102", format("0.041020592337353"))
        assertEquals("0.005644", format("0.0056439779884863"))
        assertEquals("0.0000005644", format("0.00000056439779884863"))
        // Rounding up to one switches to the two-decimal form.
        assertEquals("1.00", format("0.999995"))
    }

    @Test
    fun tinyAmountsUseScientificNotation() {
        assertEquals("5.644E-8", format("0.000000056439779884863"))
        assertEquals("5E-12", format("0.000000000005"))
    }

    @Test
    fun pinnedCurrenciesComeFirstInFixedOrder() {
        val currencies = listOf("AUD", "BRL", "CHF", "CZK", "EUR", "GBP", "JPY", "PLN", "USD", "ZAR")
            .map { Currency(it, "$it name") }
            .shuffled(java.util.Random(7))

        assertEquals(
            listOf("EUR", "USD", "GBP", "CZK", "JPY", "CHF", "PLN", "AUD", "BRL", "ZAR"),
            CurrencyOrdering.sort(currencies).map { it.code },
        )
    }

    /**
     * The unit-rate line ("1 EUR = 20,223.32 IDR") and the converted value of the default amount
     * of one unit are shown together: they must never disagree.
     */
    @Test
    fun unitRateLineAgreesWithTheConvertedValueOfOneUnit() {
        assertEquals(ExchangeFormatting.STANDARD_FRACTION_DIGITS, CurrencyMath.MIN_FRACTION_DIGITS)
        val rates = SampleRates.RATES + mapOf(
            "IDR" to BigDecimal("20223.32"),
            "KRW" to BigDecimal("1456.06"),
            "HUF" to BigDecimal("384.95"),
            "ISK" to BigDecimal("153.7"),
        )
        val table = checkNotNull(
            RateTable.create(rates, SampleRates.RATE_DATE, SampleRates.NAMES, RateSourceKind.FRANKFURTER_ECB),
        )
        var checkedLarge = 0
        for (from in rates.keys) {
            for (to in rates.keys) {
                val rate = CurrencyMath.unitRate(from, to, table)!!
                if (rate < BigDecimal.ONE) continue
                val line = CurrencyMath.unitRateText(from, to, table)!!
                val shown = line.removePrefix("1 $from = ").removeSuffix(" $to")
                val converted = ExchangeFormatting.convertedAmount(CurrencyMath.convert(BigDecimal.ONE, from, to, table)!!)
                val shownFractionDigits = shown.substringAfter('.', "").length
                if (shownFractionDigits <= ExchangeFormatting.STANDARD_FRACTION_DIGITS) {
                    // Same value on both lines; the converted amount always shows two decimals
                    // ("153.70" next to the rate line's "153.7").
                    assertEquals("$from -> $to", 0, BigDecimal(shown.replace(",", "")).compareTo(BigDecimal(converted.replace(",", ""))))
                    assertEquals("$from -> $to", ExchangeFormatting.STANDARD_FRACTION_DIGITS, converted.substringAfter('.', "").length)
                } else {
                    // The rate line is more precise; rounded to cents it is the converted value.
                    val shownInCents = BigDecimal(shown.replace(",", ""))
                        .setScale(ExchangeFormatting.STANDARD_FRACTION_DIGITS, RoundingMode.HALF_UP)
                    assertEquals("$from -> $to", 0, shownInCents.compareTo(BigDecimal(converted.replace(",", ""))))
                }
                if (rate >= BigDecimal(10_000)) checkedLarge++
            }
        }
        // EUR, GBP, USD, CHF and AUD to IDR.
        assertTrue(checkedLarge >= 5)
    }

    @Test
    fun missingPinnedCurrenciesAreSkipped() {
        val currencies = listOf("ZAR", "USD", "AUD", "PLN").map { Currency(it, it) }
        assertEquals(listOf("USD", "PLN", "AUD", "ZAR"), CurrencyOrdering.sort(currencies).map { it.code })
    }
}
