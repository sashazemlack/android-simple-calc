package com.scalc.calculator.common

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

/** Unicode minus sign used in on-screen expressions and values. */
const val MINUS_SIGN = '−'

/**
 * Locale-independent number formatting shared by the calculator, the unit converter and
 * (in the Exchange flavor) the currency converter. Decimal separator is always '.'.
 */
object NumberFormatting {

    /**
     * Formats [value] with at most [maxSignificant] significant digits (HALF_UP), stripping
     * trailing zeros. Uses plain notation when 10^[plainLowerExponent] <= |x| < 10^[plainUpperExponent],
     * otherwise scientific notation with an upper-case 'E' and explicit exponent sign, e.g. "1.5E+20",
     * "-2.5E-9". The exponent form is parseable by the calculator engine.
     */
    fun format(
        value: BigDecimal,
        maxSignificant: Int = 15,
        negativeSign: String = "-",
        plainUpperExponent: Int = 15,
        plainLowerExponent: Int = -7,
    ): String {
        if (value.signum() == 0) return "0"
        val rounded = value.round(MathContext(maxSignificant, RoundingMode.HALF_UP)).stripTrailingZeros()
        if (rounded.signum() == 0) return "0"
        val magnitude = rounded.abs()
        val exponent = magnitude.precision() - magnitude.scale() - 1
        val body = if (exponent >= plainUpperExponent || exponent < plainLowerExponent) {
            val digits = magnitude.unscaledValue().toString()
            val mantissa = if (digits.length == 1) digits else digits[0] + "." + digits.substring(1)
            mantissa + "E" + (if (exponent >= 0) "+" else "-") + abs(exponent)
        } else {
            magnitude.toPlainString()
        }
        return if (rounded.signum() < 0) negativeSign + body else body
    }

    /** Same as [format] but with a fixed maximum number of fraction digits (e.g. 2-4 for money). */
    fun formatFixedMax(value: BigDecimal, maxFractionDigits: Int, negativeSign: String = "-"): String {
        val scaled = value.setScale(maxFractionDigits, RoundingMode.HALF_UP).stripTrailingZeros()
        if (scaled.signum() == 0) return "0"
        val text = scaled.abs().toPlainString()
        return if (scaled.signum() < 0) negativeSign + text else text
    }

    /**
     * Visual digit grouping for display only: inserts [separator] every three digits in the
     * integer part of every number found in [text]. Fraction digits (after '.') and exponent
     * digits (after 'E', 'E+', 'E-') are left untouched. The underlying values never contain
     * separators, so copy/paste and evaluation are unaffected.
     */
    fun groupDigits(text: String, separator: Char = ','): String {
        if (text.length < 4) return text
        val out = StringBuilder(text.length + text.length / 3)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (!c.isAsciiDigit()) {
                out.append(c)
                i++
                continue
            }
            var j = i
            while (j < text.length && text[j].isAsciiDigit()) j++
            val run = text.substring(i, j)
            val prev = if (i > 0) text[i - 1] else ' '
            val prev2 = if (i > 1) text[i - 2] else ' '
            val isFraction = prev == '.'
            val isExponent = prev == 'E' || ((prev == '+' || prev == '-') && prev2 == 'E')
            if (isFraction || isExponent || run.length <= 3) {
                out.append(run)
            } else {
                val firstGroup = run.length % 3
                if (firstGroup > 0) out.append(run, 0, firstGroup)
                var k = firstGroup
                while (k < run.length) {
                    if (k > 0) out.append(separator)
                    out.append(run, k, k + 3)
                    k += 3
                }
            }
            i = j
        }
        return out.toString()
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'
}
