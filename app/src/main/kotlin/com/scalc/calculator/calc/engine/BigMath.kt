package com.scalc.calculator.calc.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * BigDecimal arithmetic helpers for the calculator (minSdk 24 safe: no BigDecimal.sqrt).
 *
 * Exact operations use [MC] (34 significant digits, HALF_EVEN). Transcendental functions are
 * computed in double precision on a mantissa/exponent decomposition, so arguments and results
 * far outside the double range still work. Magnitudes of 1E+100000 and above are [CalcError.Overflow];
 * magnitudes below 1E-100000 are flushed to zero.
 */
internal object BigMath {
    val MC: MathContext = MathContext(34, RoundingMode.HALF_EVEN)

    /** Values with a decimal exponent at or above this overflow. */
    const val MAX_DECIMAL_EXPONENT = 100_000L

    /**
     * Largest |n| for which x^n is computed with BigDecimal.pow at 34 digits (the limit of
     * BigDecimal.pow(int, MathContext)); the overflow estimate runs first, so any such power is
     * cheap (about 30 multiplications).
     */
    const val MAX_INTEGER_POWER = 999_999_999

    /** Largest n with n! < 1E+100000 (log10(25205!) = 99995.68); 25206! overflows. */
    const val MAX_FACTORIAL = 25_205

    /** n! up to this n is cheap enough (< 0.1 ms) to recompute on every keystroke. */
    private const val UNCACHED_FACTORIAL_LIMIT = 1000

    /** Number of large factorials kept by [factorialCache] (25205! is about 41 KB). */
    private const val FACTORIAL_CACHE_SIZE = 32

    val PI = BigDecimal("3.14159265358979323846264338327950288419716939937510")
    val E = BigDecimal("2.71828182845904523536028747135266249775724709369995")
    private val LN10 = BigDecimal("2.30258509299404568401799145468436420760110148862877")
    private val LN10_DOUBLE = Math.log(10.0)
    private val TWO = BigDecimal(2)
    private val NEAR_ONE_LOW = BigDecimal("0.75")
    private val NEAR_ONE_HIGH = BigDecimal("1.25")
    private val MAX_FACTORIAL_DECIMAL = BigDecimal(MAX_FACTORIAL)
    private val MAX_DECIMAL_EXPONENT_DECIMAL = BigDecimal(MAX_DECIMAL_EXPONENT)

    /** Integer exponents with at most this many digits may take the BigDecimal.pow route. */
    private const val MAX_INTEGER_POWER_DIGITS = 9

    /** |y| below this keeps e^y inside the double range, so Math.exp applies directly. */
    private const val EXP_DOUBLE_LIMIT = 700.0

    /** Integer check for n! tolerates representation noise beyond 15 significant digits. */
    private val FACTORIAL_ROUNDING = MathContext(15, RoundingMode.HALF_EVEN)

    /**
     * Least-recently-used exact n! for n > [UNCACHED_FACTORIAL_LIMIT]. The live preview evaluates
     * the whole expression on every keystroke, so "25205!+…" must not redo the 10-20 ms product
     * each time. Evaluations run on background threads, hence the lock.
     */
    private val factorialCache = object : LinkedHashMap<Int, BigDecimal>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, BigDecimal>): Boolean =
            size > FACTORIAL_CACHE_SIZE
    }

    /** Decimal exponent of the leading digit: x = m · 10^e with 1 <= |m| < 10. [x] must be non-zero. */
    fun exponentOf(x: BigDecimal): Long = x.precision().toLong() - x.scale().toLong() - 1L

    /** Enforces the overflow limit and flushes underflowing values to zero. */
    fun checked(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return x
        val exponent = exponentOf(x)
        return when {
            exponent >= MAX_DECIMAL_EXPONENT -> throw CalcException(CalcError.Overflow)
            exponent < -MAX_DECIMAL_EXPONENT -> BigDecimal.ZERO
            else -> x
        }
    }

    /** Converts a double result with `BigDecimal(Double.toString(d))` semantics. */
    fun fromDouble(d: Double): BigDecimal {
        if (d.isNaN()) throw CalcException(CalcError.Domain)
        if (d.isInfinite()) throw CalcException(CalcError.Overflow)
        return BigDecimal.valueOf(d)
    }

    /** Parses a number token; exponents beyond the BigDecimal range overflow or underflow. */
    fun parseLiteral(text: String): BigDecimal {
        val normalized = text.replace('−', '-')
        return try {
            BigDecimal(normalized)
        } catch (e: NumberFormatException) {
            val exponentAt = normalized.indexOf('E')
            if (exponentAt < 0) throw CalcException(CalcError.Syntax)
            val mantissa = BigDecimal(normalized.substring(0, exponentAt))
            when {
                mantissa.signum() == 0 -> BigDecimal.ZERO
                normalized.getOrNull(exponentAt + 1) == '-' -> BigDecimal.ZERO
                else -> throw CalcException(CalcError.Overflow)
            }
        }
    }

    /**
     * Truncated remainder (sign of the dividend): 10%3 = 1, −7%3 = −1, 7.5%2 = 1.5.
     *
     * Computed exactly on the unscaled integers with modular exponentiation, so huge quotients
     * (1E+99999 % 7) stay fast.
     */
    fun remainder(a: BigDecimal, b: BigDecimal): BigDecimal {
        if (b.signum() == 0) throw CalcException(CalcError.DivisionByZero)
        if (a.signum() == 0) return BigDecimal.ZERO
        if (a.abs() < b.abs()) return a
        // a = A / 10^scale and b = B / 10^scale with integers A, B; |a| >= |b| keeps B small.
        val scale = max(a.scale(), b.scale())
        val modulus = b.unscaledValue().abs().multiply(BigInteger.TEN.pow(scale - b.scale()))
        val shift = BigInteger.valueOf(scale.toLong() - a.scale().toLong())
        val magnitude = a.unscaledValue().abs().mod(modulus)
            .multiply(BigInteger.TEN.modPow(shift, modulus))
            .mod(modulus)
        val result = BigDecimal(magnitude, scale)
        return (if (a.signum() < 0) result.negate() else result).round(MC)
    }

    /**
     * Square root by Newton iteration at 42 digits, seeded from Math.sqrt on the mantissa and
     * rounded to 34 digits, so exact squares give exact results (√2.25 = 1.5).
     */
    fun sqrt(x: BigDecimal): BigDecimal {
        when (x.signum()) {
            -1 -> throw CalcException(CalcError.Domain)
            0 -> return BigDecimal.ZERO
        }
        // x = m · 10^(2k) with 1 <= m < 100, so the double seed never over- or underflows.
        val k = Math.floorDiv(exponentOf(x), 2L).toInt()
        val m = x.scaleByPowerOfTen(-2 * k)
        val work = MathContext(MC.precision + 8, RoundingMode.HALF_EVEN)
        var y = BigDecimal.valueOf(Math.sqrt(m.toDouble())).scaleByPowerOfTen(k)
        for (i in 0 until 10) {
            val next = y.add(x.divide(y, work), work).divide(TWO, work)
            if (next.compareTo(y) == 0) break
            y = next
        }
        return y.round(MC).stripTrailingZeros()
    }

    /**
     * x^y. Integer exponents with |y| <= [MAX_INTEGER_POWER] are computed with BigDecimal.pow at
     * 34 digits after an overflow/underflow estimate (0^0 = 1, 0^negative is a division by zero),
     * so 2^100000 and 3^123456 show 15 correct digits. e^y for non-integer y uses Math.exp (or
     * an exact base-10 split beyond the double range). Other exponents use logarithms in double
     * precision; a negative base with a non-integer exponent is a domain error.
     */
    fun pow(base: BigDecimal, exponent: BigDecimal): BigDecimal {
        if (exponent.signum() == 0) return BigDecimal.ONE
        if (base.signum() == 0) {
            if (exponent.signum() < 0) throw CalcException(CalcError.DivisionByZero)
            return BigDecimal.ZERO
        }
        val y = exponent.stripTrailingZeros()
        if (y.scale() <= 0) {
            val integerDigits = y.precision().toLong() - y.scale().toLong()
            if (integerDigits <= MAX_INTEGER_POWER_DIGITS) {
                val n = y.intValueExact()
                if (abs(n) <= MAX_INTEGER_POWER) return integerPow(base, n)
            }
            // A negative scale means a multiple of ten, hence even.
            val odd = y.scale() == 0 && y.unscaledValue().testBit(0)
            return powViaLogs(base.abs(), y, negate = base.signum() < 0 && odd)
        }
        if (base.signum() < 0) throw CalcException(CalcError.Domain)
        if (base.compareTo(E) == 0) return expOf(y)
        return powViaLogs(base, y, negate = false)
    }

    /**
     * e^[y] for a non-integer y. Math.exp is accurate to 1 ulp inside the double range; beyond
     * it, y / ln 10 is split at 34 digits into an integer exponent and a fraction for Math.pow.
     */
    private fun expOf(y: BigDecimal): BigDecimal {
        val yd = y.toDouble()
        if (abs(yd) < EXP_DOUBLE_LIMIT) return fromDouble(Math.exp(yd))
        val log10 = y.divide(LN10, MC)
        if (log10 >= MAX_DECIMAL_EXPONENT_DECIMAL) throw CalcException(CalcError.Overflow)
        if (log10 < MAX_DECIMAL_EXPONENT_DECIMAL.negate()) return BigDecimal.ZERO
        val k = log10.setScale(0, RoundingMode.FLOOR)
        return fromDouble(Math.pow(10.0, log10.subtract(k).toDouble())).scaleByPowerOfTen(k.intValueExact())
    }

    private fun integerPow(base: BigDecimal, n: Int): BigDecimal {
        val estimate = n * log10Abs(base)
        if (estimate > MAX_DECIMAL_EXPONENT + 1) throw CalcException(CalcError.Overflow)
        if (estimate < -MAX_DECIMAL_EXPONENT - 1) return BigDecimal.ZERO
        return base.pow(n, MC)
    }

    /** |result| = a^y for a > 0, computed as exp(y · ln a) with the best double route available. */
    private fun powViaLogs(a: BigDecimal, y: BigDecimal, negate: Boolean): BigDecimal {
        if (a.compareTo(BigDecimal.ONE) == 0) return if (negate) BigDecimal.ONE.negate() else BigDecimal.ONE
        val yd = y.toDouble()
        val lnA = lnDouble(a)
        val lnResult = yd * lnA
        if (lnResult.isNaN()) throw CalcException(CalcError.Overflow)
        val log10Result = lnResult / LN10_DOUBLE
        if (log10Result >= MAX_DECIMAL_EXPONENT) throw CalcException(CalcError.Overflow)
        if (log10Result < -MAX_DECIMAL_EXPONENT) return BigDecimal.ZERO
        val magnitude = if (abs(lnResult) < 700.0) {
            val ad = a.toDouble()
            // Math.pow is accurate to 1 ulp when a is exactly representable, or when |ln a| >= 1 so
            // that the rounding of a to double matters less than the rounding of y·ln a. Otherwise
            // (a near 1, e.g. 1.0000001) exp(y·log1p(a−1)) is the more accurate route.
            val inRange = ad > 1e-300 && ad < 1e300
            val usePow = inRange && (abs(lnA) >= 1.0 || BigDecimal(ad).compareTo(a) == 0)
            fromDouble(if (usePow) Math.pow(ad, yd) else Math.exp(lnResult))
        } else {
            fromLog10(log10Result)
        }
        return if (negate) magnitude.negate() else magnitude
    }

    /** 10^[log10] for values outside the double range: mantissa from the fraction, exponent from the integer part. */
    private fun fromLog10(log10: Double): BigDecimal {
        val k = floor(log10)
        val mantissa = Math.pow(10.0, log10 - k)
        return fromDouble(mantissa).scaleByPowerOfTen(k.toInt())
    }

    private fun log10Abs(x: BigDecimal): Double {
        val e = exponentOf(x)
        return e + Math.log10(x.abs().scaleByPowerOfTen(-e.toInt()).toDouble())
    }

    /**
     * n! for integers 0..[MAX_FACTORIAL], exact; larger n overflow. Results above 1000! are
     * memoized ([factorialCache]) because they take milliseconds to build.
     */
    fun factorial(x: BigDecimal): BigDecimal {
        if (x.signum() < 0) throw CalcException(CalcError.InvalidFactorial)
        val n = x.round(FACTORIAL_ROUNDING).stripTrailingZeros()
        if (n.scale() > 0) throw CalcException(CalcError.InvalidFactorial)
        if (n > MAX_FACTORIAL_DECIMAL) throw CalcException(CalcError.Overflow)
        val count = n.intValueExact()
        if (count <= UNCACHED_FACTORIAL_LIMIT) return BigDecimal(productOf(2, count))
        synchronized(factorialCache) { factorialCache[count] }?.let { return it }
        val value = BigDecimal(productOf(2, count))
        // Computing the precision now caches it inside the immutable BigDecimal for later reads.
        value.precision()
        synchronized(factorialCache) { factorialCache[count] = value }
        return value
    }

    /** Product of from..to by binary splitting (balanced multiplications are much faster). */
    private fun productOf(from: Int, to: Int): BigInteger {
        if (to < from) return BigInteger.ONE
        if (to - from < 16) {
            var product = BigInteger.valueOf(from.toLong())
            for (i in from + 1..to) product = product.multiply(BigInteger.valueOf(i.toLong()))
            return product
        }
        val mid = (from + to) ushr 1
        return productOf(from, mid).multiply(productOf(mid + 1, to))
    }

    /** Natural logarithm; x <= 0 is a domain error. */
    fun ln(x: BigDecimal): BigDecimal {
        if (x.signum() <= 0) throw CalcException(CalcError.Domain)
        if (isNearOne(x)) return fromDouble(Math.log1p(x.subtract(BigDecimal.ONE).toDouble()))
        val e = exponentOf(x)
        val mantissa = x.scaleByPowerOfTen(-e.toInt()).toDouble()
        return BigDecimal.valueOf(e).multiply(LN10, MC).add(fromDouble(Math.log(mantissa)), MC)
    }

    /** Base-10 logarithm; exact for powers of ten (log(1000) = 3, log(1E+500) = 500). */
    fun log10(x: BigDecimal): BigDecimal {
        if (x.signum() <= 0) throw CalcException(CalcError.Domain)
        val e = exponentOf(x)
        val mantissa = x.scaleByPowerOfTen(-e.toInt())
        if (mantissa.compareTo(BigDecimal.ONE) == 0) return BigDecimal.valueOf(e)
        if (isNearOne(x)) return fromDouble(Math.log1p(x.subtract(BigDecimal.ONE).toDouble()) / LN10_DOUBLE)
        return BigDecimal.valueOf(e).add(fromDouble(Math.log10(mantissa.toDouble())), MC)
    }

    /** ln(x) as a double for any positive x, accurate near 1 thanks to log1p. */
    fun lnDouble(x: BigDecimal): Double {
        if (isNearOne(x)) return Math.log1p(x.subtract(BigDecimal.ONE).toDouble())
        val e = exponentOf(x)
        return e * LN10_DOUBLE + Math.log(x.scaleByPowerOfTen(-e.toInt()).toDouble())
    }

    private fun isNearOne(x: BigDecimal) = x >= NEAR_ONE_LOW && x <= NEAR_ONE_HIGH
}
