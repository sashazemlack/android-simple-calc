package com.scalc.calculator.converter.engine

import org.junit.Assert.assertTrue
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Shared helpers for the converter engine tests. */
internal object ConversionAssertions {

    fun unit(category: UnitCategory, key: String): MeasureUnit =
        requireNotNull(UnitCatalog.find(category, key)) { "Unknown unit $key in $category" }

    fun convert(value: String, category: UnitCategory, fromKey: String, toKey: String): BigDecimal =
        UnitConverter.convert(BigDecimal(value), unit(category, fromKey), unit(category, toKey))

    /** The result must equal [expected] exactly (numerically, ignoring trailing zeros). */
    fun assertExact(expected: String, actual: BigDecimal) {
        assertTrue(
            "expected exactly $expected but was ${actual.toPlainString()}",
            BigDecimal(expected).compareTo(actual) == 0,
        )
    }

    /**
     * [actual] rounded (HALF_UP) to as many significant digits as [expected] has must equal it,
     * e.g. "2.4710538146717" checks the first 14 significant digits.
     */
    fun assertRounded(expected: String, actual: BigDecimal) {
        val e = BigDecimal(expected)
        val rounded = actual.round(MathContext(e.precision(), RoundingMode.HALF_UP))
        assertTrue(
            "expected $expected (${e.precision()} significant digits) but was ${actual.toPlainString()}",
            e.compareTo(rounded) == 0,
        )
    }

    /** |expected − actual| <= tolerance. */
    fun assertWithin(expected: BigDecimal, actual: BigDecimal, tolerance: BigDecimal, message: String = "") {
        val diff = (expected - actual).abs()
        assertTrue(
            "$message expected ${expected.toPlainString()} but was ${actual.toPlainString()} (diff ${diff.toPlainString()})",
            diff <= tolerance,
        )
    }
}
