package com.scalc.calculator.exchange.model

import com.scalc.calculator.common.NumberFormatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class CurrencyMathTest {

    private val table = SampleRates.table()

    private fun assertDecimal(expected: String, actual: BigDecimal?) {
        requireNotNull(actual)
        assertTrue("expected $expected but was $actual", BigDecimal(expected).compareTo(actual) == 0)
    }

    @Test
    fun eurToUsdUsesTheReferenceRate() {
        assertDecimal("111.27", CurrencyMath.convert(BigDecimal("100"), "EUR", "USD", table))
    }

    @Test
    fun usdToEurDividesByTheReferenceRate() {
        assertDecimal("100", CurrencyMath.convert(BigDecimal("111.27"), "USD", "EUR", table))
    }

    @Test
    fun crossRateGoesThroughEur() {
        val result = CurrencyMath.convert(BigDecimal("100"), "USD", "CZK", table)
        // 100 × 24.378 / 1.1127, rounded once to 34 significant digits.
        val expected = BigDecimal("2437.8").divide(BigDecimal("1.1127"), MathContext(34, RoundingMode.HALF_EVEN))
        assertEquals(expected, result)
        assertEquals(2190.887, result!!.toDouble(), 0.001)
        assertEquals("2190.89", NumberFormatting.formatFixedMax(result, 2))
    }

    @Test
    fun jpyConversionKeepsFullPrecision() {
        val oneYenInEuro = CurrencyMath.convert(BigDecimal.ONE, "JPY", "EUR", table)!!
        assertEquals(34, oneYenInEuro.precision())
        // Converting back must reproduce 1 within the 34-digit working precision.
        val back = oneYenInEuro.multiply(BigDecimal("177.18"))
        assertTrue(back.subtract(BigDecimal.ONE).abs() < BigDecimal("1E-30"))

        val million = CurrencyMath.convert(BigDecimal("1000000"), "JPY", "USD", table)!!
        assertEquals("6280.05", NumberFormatting.formatFixedMax(million, 2))
        val roundTrip = CurrencyMath.convert(million, "USD", "JPY", table)!!
        assertTrue(roundTrip.subtract(BigDecimal("1000000")).abs() < BigDecimal("1E-25"))
    }

    @Test
    fun sameCurrencyReturnsTheAmountUnchanged() {
        val amount = BigDecimal("123.45")
        assertSame(amount, CurrencyMath.convert(amount, "GBP", "GBP", table))
    }

    @Test
    fun missingCurrencyGivesNull() {
        assertNull(CurrencyMath.convert(BigDecimal.TEN, "XYZ", "USD", table))
        assertNull(CurrencyMath.convert(BigDecimal.TEN, "USD", "XYZ", table))
        assertNull(CurrencyMath.convert(BigDecimal.TEN, "XYZ", "XYZ", table))
        assertNull(CurrencyMath.unitRate("EUR", "BGN", table))
        assertNull(CurrencyMath.unitRateText("EUR", "BGN", table))
    }

    @Test
    fun zeroAndNegativeAmounts() {
        assertEquals(0, CurrencyMath.convert(BigDecimal.ZERO, "USD", "CZK", table)!!.signum())
        assertDecimal("-111.27", CurrencyMath.convert(BigDecimal("-100"), "EUR", "USD", table))
    }

    @Test
    fun unitRateHelpers() {
        assertDecimal("1.1127", CurrencyMath.unitRate("EUR", "USD", table))
        assertEquals("1 EUR = 1.1127 USD", CurrencyMath.unitRateText("EUR", "USD", table))
        assertEquals("1 EUR = 177.18 JPY", CurrencyMath.unitRateText("EUR", "JPY", table))
        assertEquals("1 JPY = 0.00564398 EUR", CurrencyMath.unitRateText("JPY", "EUR", table))
        assertEquals("1 USD = 21.9089 CZK", CurrencyMath.unitRateText("USD", "CZK", table))
        assertEquals("1 EUR = 1 EUR", CurrencyMath.unitRateText("EUR", "EUR", table))
        assertEquals("1 GBP = 1.33069 USD", CurrencyMath.unitRateText("GBP", "USD", table))
        assertEquals("1 USD = 0.898715 EUR", CurrencyMath.unitRateText("USD", "EUR", table))
    }

    /** ECB quotes IDR with two decimals (7 significant digits); the unit-rate line must keep them. */
    private val largeRateTable = checkNotNull(
        RateTable.create(
            rates = SampleRates.RATES + mapOf(
                "IDR" to BigDecimal("20223.32"),
                "KRW" to BigDecimal("1456.06"),
                "HUF" to BigDecimal("384.95"),
            ),
            rateDate = SampleRates.RATE_DATE,
            names = SampleRates.NAMES,
            source = RateSourceKind.FRANKFURTER_ECB,
        ),
    )

    @Test
    fun unitRatesOfTenThousandAndMoreKeepTwoDecimals() {
        assertEquals("1 EUR = 20,223.32 IDR", CurrencyMath.unitRateText("EUR", "IDR", largeRateTable))
        assertEquals("1 GBP = 24,185.37 IDR", CurrencyMath.unitRateText("GBP", "IDR", largeRateTable))
        // 20223.32 / 1.1127 = 18174.9977...: rounded HALF_UP at the second decimal, zeros stripped.
        assertEquals("1 USD = 18,175 IDR", CurrencyMath.unitRateText("USD", "IDR", largeRateTable))
        // 1456.06 / 0.83618 = 1741.3236...: 6 significant digits already give 2 decimals.
        assertEquals("1 GBP = 1,741.32 KRW", CurrencyMath.unitRateText("GBP", "KRW", largeRateTable))
        // Rates below 10,000 already had 2+ decimals with 6 significant digits: unchanged.
        assertEquals("1 EUR = 1,456.06 KRW", CurrencyMath.unitRateText("EUR", "KRW", largeRateTable))
        assertEquals("1 EUR = 384.95 HUF", CurrencyMath.unitRateText("EUR", "HUF", largeRateTable))
        // Rates below 1 keep 6 significant digits.
        assertEquals("1 IDR = 0.0000494479 EUR", CurrencyMath.unitRateText("IDR", "EUR", largeRateTable))
        assertEquals("1 KRW = 0.000686785 EUR", CurrencyMath.unitRateText("KRW", "EUR", largeRateTable))
    }

    @Test
    fun unitRateKeepsAtLeastTheRequestedSignificantDigits() {
        assertEquals("1 EUR = 20,223.32 IDR", CurrencyMath.unitRateText("EUR", "IDR", largeRateTable, maxSignificant = 3))
        assertEquals("1 EUR = 1.11 USD", CurrencyMath.unitRateText("EUR", "USD", largeRateTable, maxSignificant = 3))
        assertEquals("1 GBP = 24,185.3668 IDR", CurrencyMath.unitRateText("GBP", "IDR", largeRateTable, maxSignificant = 9))
    }
}
