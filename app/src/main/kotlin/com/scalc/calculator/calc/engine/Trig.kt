package com.scalc.calculator.calc.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * Trigonometric and inverse trigonometric functions.
 *
 * Forward functions reduce the argument to a quadrant q and a remainder r with |r| <= 45° (π/4)
 * before anything is converted to double: DEG mode exactly with BigDecimal, RAD mode at 160
 * digits against a 200-decimal π. Only r goes through Math.sin/cos/tan, so results keep their
 * 15 displayed digits next to every multiple of 90° (π/2) and for large arguments such as
 * sin(1E+15) in radians.
 *
 * - DEG returns exact values at the well-known angles (multiples of 90°, plus the ±0.5 and ±1
 *   cases at 30°/45°/60° multiples); tan at odd multiples of 90° is a domain error.
 * - RAD treats an argument that is a multiple of π/2 to within its 34-digit working precision
 *   (sin(π), cos(π÷2), sin(100π)) as exact: sin/cos are 0 or ±1 and tan at odd multiples is a
 *   domain error. Arguments of magnitude 1E+100 and above are a domain error.
 * - Arguments below 1E-8 in magnitude use the first-order result (sin x = tan x = asin x =
 *   atan x = x, cos x = 1), exact to far more than 15 digits; tiny values are never flushed to
 *   zero and never go through exact arithmetic on 100,000-digit numbers.
 * - asin/acos close to ±1 are computed from the exact 1 − |x|, so typed arguments such as
 *   0.99999999999999 keep all their information.
 */
internal object Trig {
    private val FULL_TURN = BigDecimal(360)
    private val FULL_TURN_INTEGER = BigInteger.valueOf(360)
    private val RIGHT_ANGLE = BigDecimal(90)
    private val HALF_TURN = BigDecimal(180)
    private val ONE = BigDecimal.ONE
    private val ZERO = BigDecimal.ZERO
    private val TWO = BigDecimal(2)
    private val FOUR_INTEGER = BigInteger.valueOf(4)
    private val MINUS_ONE = BigDecimal.ONE.negate()
    private val HALF = BigDecimal("0.5")
    private val MINUS_HALF = BigDecimal("-0.5")

    /** π to 200 decimals; its first 50 decimals are [BigMath.PI]. */
    private val PI_HIGH = BigDecimal(
        "3.14159265358979323846264338327950288419716939937510" +
            "58209749445923078164062862089986280348253421170679" +
            "82148086513282306647093844609550582231725359408128" +
            "48111745028410270193852110555964462294895493038196",
    )
    private val HALF_PI_HIGH: BigDecimal = PI_HIGH.divide(TWO)

    /** Working precision of the radian reduction: 100 integer digits of the quotient plus guard digits. */
    private val REDUCTION_MC = MathContext(160, RoundingMode.HALF_EVEN)

    /** Radian arguments at or above this magnitude are a domain error. */
    private val MAX_REDUCIBLE = BigDecimal("1E+100")

    /** A remainder below 1E-32 of the argument means the argument is a multiple of π/2. */
    private const val EXACT_MULTIPLE_DIGITS = 32

    /** Below this magnitude the first-order Taylor term is the correctly rounded result. */
    private val TINY = BigDecimal("1E-8")

    /** asin/acos arguments may exceed ±1 by 1E-15 (rounding noise); they are clamped. */
    private val ONE_PLUS_TOLERANCE = BigDecimal("1.000000000000001")

    private val SIN_EXACT = mapOf(
        0 to ZERO, 30 to HALF, 90 to ONE, 150 to HALF,
        180 to ZERO, 210 to MINUS_HALF, 270 to MINUS_ONE, 330 to MINUS_HALF,
    )
    private val COS_EXACT = mapOf(
        0 to ONE, 60 to HALF, 90 to ZERO, 120 to MINUS_HALF,
        180 to MINUS_ONE, 240 to MINUS_HALF, 270 to ZERO, 300 to HALF,
    )
    private val TAN_EXACT = mapOf(
        0 to ZERO, 45 to ONE, 135 to MINUS_ONE, 180 to ZERO, 225 to ONE, 315 to MINUS_ONE,
    )

    /** sin and cos at quadrant q (argument q·π/2), indexed by q mod 4. */
    private val SIN_QUADRANT = listOf(ZERO, ONE, ZERO, MINUS_ONE)
    private val COS_QUADRANT = listOf(ONE, ZERO, MINUS_ONE, ZERO)

    private val ASIN_EXACT = listOf(
        ONE to BigDecimal(90), HALF to BigDecimal(30), ZERO to ZERO,
        MINUS_HALF to BigDecimal(-30), MINUS_ONE to BigDecimal(-90),
    )
    private val ACOS_EXACT = listOf(
        ONE to ZERO, HALF to BigDecimal(60), ZERO to BigDecimal(90),
        MINUS_HALF to BigDecimal(120), MINUS_ONE to BigDecimal(180),
    )
    private val ATAN_EXACT = listOf(ONE to BigDecimal(45), ZERO to ZERO, MINUS_ONE to BigDecimal(-45))

    /** sin, cos or tan of [x] interpreted in [mode]. */
    fun forward(function: MathFunction, x: BigDecimal, mode: AngleMode): BigDecimal {
        if (x.signum() == 0) return if (function == MathFunction.Cos) ONE else ZERO
        if (x.abs() < TINY) {
            return when {
                function == MathFunction.Cos -> ONE
                mode == AngleMode.DEG -> x.multiply(BigMath.PI).divide(HALF_TURN, BigMath.MC)
                else -> x
            }
        }
        return if (mode == AngleMode.DEG) forwardDegrees(function, x) else forwardRadians(function, x)
    }

    /** asin, acos or atan of [x]; the result is in degrees in DEG mode. */
    fun inverse(function: MathFunction, x: BigDecimal, mode: AngleMode): BigDecimal {
        if (function != MathFunction.Atan && x.abs() > ONE_PLUS_TOLERANCE) {
            throw CalcException(CalcError.Domain)
        }
        if (mode == AngleMode.DEG) {
            val table = when (function) {
                MathFunction.Asin -> ASIN_EXACT
                MathFunction.Acos -> ACOS_EXACT
                else -> ATAN_EXACT
            }
            table.firstOrNull { it.first.compareTo(x) == 0 }?.let { return it.second }
        }
        if (function != MathFunction.Acos && x.signum() != 0 && x.abs() < TINY) {
            return if (mode == AngleMode.DEG) x.multiply(HALF_TURN).divide(BigMath.PI, BigMath.MC) else x
        }
        val radians = when {
            function == MathFunction.Atan -> Math.atan(x.toDouble())
            x.abs() > HALF -> inverseNearUnit(function, x)
            function == MathFunction.Asin -> Math.asin(x.toDouble())
            else -> Math.acos(x.toDouble())
        }
        return BigMath.fromDouble(if (mode == AngleMode.DEG) Math.toDegrees(radians) else radians)
    }

    /**
     * asin or acos (radians) of [x] with 0.5 < |x| <= 1 + tolerance. Uses
     * acos|x| = 2·asin(√((1 − |x|) / 2)) with the exact 1 − |x| (clamped at 0), which avoids
     * rounding x to double where the functions are steepest.
     */
    private fun inverseNearUnit(function: MathFunction, x: BigDecimal): Double {
        val complement = ONE.subtract(x.abs()).max(ZERO)
        val acosOfMagnitude = 2.0 * Math.asin(Math.sqrt(complement.divide(TWO).toDouble()))
        val positive = x.signum() > 0
        return if (function == MathFunction.Asin) {
            val asinOfMagnitude = Math.PI / 2 - acosOfMagnitude
            if (positive) asinOfMagnitude else -asinOfMagnitude
        } else {
            if (positive) acosOfMagnitude else Math.PI - acosOfMagnitude
        }
    }

    private fun forwardDegrees(function: MathFunction, x: BigDecimal): BigDecimal {
        val angle = reduceDegrees(x)
        exactDegrees(function, angle)?.let { return it }
        // angle = 90·q + d exactly, with |d| <= 45, so only the small remainder becomes a double.
        val quadrant = angle.divide(RIGHT_ANGLE, 0, RoundingMode.HALF_EVEN)
        val remainder = angle.subtract(RIGHT_ANGLE.multiply(quadrant))
        return byQuadrant(function, quadrant.toInt() and 3, Math.toRadians(remainder.toDouble()))
    }

    /** Angle modulo 360 in [0, 360), exact; integer angles use modular exponentiation (fast for 1E+99999). */
    private fun reduceDegrees(x: BigDecimal): BigDecimal {
        if (x.scale() <= 0) {
            val power = BigInteger.TEN.modPow(BigInteger.valueOf(-x.scale().toLong()), FULL_TURN_INTEGER)
            return BigDecimal(x.unscaledValue().mod(FULL_TURN_INTEGER).multiply(power).mod(FULL_TURN_INTEGER))
        }
        val r = x.remainder(FULL_TURN)
        return if (r.signum() < 0) r.add(FULL_TURN) else r
    }

    private fun exactDegrees(function: MathFunction, angle: BigDecimal): BigDecimal? {
        val stripped = angle.stripTrailingZeros()
        if (stripped.scale() > 0) return null
        val degrees = stripped.intValueExact()
        return when (function) {
            MathFunction.Sin -> SIN_EXACT[degrees]
            MathFunction.Cos -> COS_EXACT[degrees]
            else -> {
                if (degrees % 180 == 90) throw CalcException(CalcError.Domain)
                TAN_EXACT[degrees]
            }
        }
    }

    /**
     * Radians: x = k·π/2 + r with |r| <= π/4, computed at 160 digits against [PI_HIGH] so huge
     * arguments (up to 1E+100) and arguments next to a multiple of π/2 keep their accuracy.
     */
    private fun forwardRadians(function: MathFunction, x: BigDecimal): BigDecimal {
        if (x.abs() >= MAX_REDUCIBLE) throw CalcException(CalcError.Domain)
        val k = x.divide(HALF_PI_HIGH, REDUCTION_MC).setScale(0, RoundingMode.HALF_EVEN)
        val r = x.subtract(k.multiply(HALF_PI_HIGH), REDUCTION_MC)
        val quadrant = k.toBigInteger().mod(FOUR_INTEGER).toInt()
        if (k.signum() != 0 && r.abs().scaleByPowerOfTen(EXACT_MULTIPLE_DIGITS) <= x.abs()) {
            // A multiple of π/2 within the working precision: sin(π), cos(π÷2), tan(100π).
            return when (function) {
                MathFunction.Sin -> SIN_QUADRANT[quadrant]
                MathFunction.Cos -> COS_QUADRANT[quadrant]
                else -> if (quadrant % 2 == 0) ZERO else throw CalcException(CalcError.Domain)
            }
        }
        return byQuadrant(function, quadrant, r.toDouble())
    }

    /** sin, cos or tan of q·90° + [r] (r in radians, |r| <= π/4) for quadrant q in 0..3. */
    private fun byQuadrant(function: MathFunction, quadrant: Int, r: Double): BigDecimal {
        val value = when (function) {
            MathFunction.Sin -> when (quadrant) {
                0 -> Math.sin(r)
                1 -> Math.cos(r)
                2 -> -Math.sin(r)
                else -> -Math.cos(r)
            }
            MathFunction.Cos -> when (quadrant) {
                0 -> Math.cos(r)
                1 -> -Math.sin(r)
                2 -> -Math.cos(r)
                else -> Math.sin(r)
            }
            else -> {
                val tan = Math.tan(r)
                when {
                    quadrant % 2 == 0 -> tan
                    tan == 0.0 -> throw CalcException(CalcError.Domain)
                    else -> -1.0 / tan
                }
            }
        }
        return BigMath.fromDouble(value)
    }
}
