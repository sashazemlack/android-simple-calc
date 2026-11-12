package com.scalc.calculator.calc.engine

import com.scalc.calculator.calc.engine.AngleMode.DEG
import com.scalc.calculator.calc.engine.AngleMode.RAD
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** sin, cos, tan and their inverses in degree and radian mode. */
class TrigonometryTest {

    @Test fun sineOfThirtyDegreesIsExactlyHalf() = assertExact("0.5", "sin(30)", DEG)

    @Test fun cosineOfSixtyDegreesIsExactlyHalf() = assertExact("0.5", "cos(60)", DEG)

    @Test fun tangentOfFortyFiveDegreesIsExactlyOne() = assertExact("1", "tan(45)", DEG)

    @Test fun exactValuesAtQuarterTurns() {
        assertExact("0", "sin(0)", DEG)
        assertExact("1", "sin(90)", DEG)
        assertExact("0", "sin(180)", DEG)
        assertExact("-1", "sin(270)", DEG)
        assertExact("1", "cos(0)", DEG)
        assertExact("0", "cos(90)", DEG)
        assertExact("-1", "cos(180)", DEG)
        assertExact("0", "cos(270)", DEG)
    }

    @Test fun tangentAtMultiplesOfHalfTurnIsZero() {
        assertExact("0", "tan(0)", DEG)
        assertExact("0", "tan(180)", DEG)
        assertExact("0", "tan(−360)", DEG)
    }

    @Test fun tangentAtOddQuarterTurnsIsUndefined() {
        assertError(CalcError.Domain, "tan(90)", DEG)
        assertError(CalcError.Domain, "tan(270)", DEG)
        assertError(CalcError.Domain, "tan(−90)", DEG)
        assertError(CalcError.Domain, "tan(450)", DEG)
    }

    @Test fun negativeAnglesAreReduced() {
        assertExact("-0.5", "sin(−30)", DEG)
        assertExact("-1", "sin(−90)", DEG)
    }

    @Test fun anglesBeyondFullTurnAreReduced() {
        assertExact("0.5", "sin(390)", DEG)
        assertExact("1", "cos(720)", DEG)
    }

    @Test fun hugeIntegerAngleIsReducedExactly() =
        // 10^20 ≡ 280 (mod 360); sin(280°) = −sin(80°).
        assertShows("−0.984807753012208", "sin(1E+20)", DEG)

    @Test fun nonSpecialDegreeAngles() {
        assertShows("0.707106781186548", "sin(45)", DEG)
        assertShows("0.707106781186548", "cos(45)", DEG)
        assertShows("1.73205080756888", "tan(60)", DEG)
        assertShows("0.5", "sin(30.0)", DEG)
    }

    @Test fun implicitMultiplicationWithSine() = assertExact("1.5", "3sin(30)", DEG)

    @Test fun radianSineOfPiSnapsToZero() = assertExact("0", "sin(π)", RAD)

    @Test fun radianCosineOfPi() = assertShows("−1", "cos(π)", RAD)

    @Test fun radianQuarterTurn() {
        assertShows("1", "sin(π÷2)", RAD)
        assertExact("0", "cos(π÷2)", RAD)
    }

    @Test fun radianTangentAtQuarterTurnIsUndefined() = assertError(CalcError.Domain, "tan(π÷2)", RAD)

    @Test fun radianTangentOfPiSnapsToZero() = assertExact("0", "tan(π)", RAD)

    @Test fun radianSnappingScalesWithAngle() = assertExact("0", "sin(100π)", RAD)

    @Test fun radianSineOfSixthTurn() = assertShows("0.5", "sin(π÷6)", RAD)

    @Test fun radianValues() {
        assertShows("0.841470984807897", "sin(1)", RAD)
        assertShows("0.54030230586814", "cos(1)", RAD)
        assertShows("1.5574077246549", "tan(1)", RAD)
    }

    @Test fun smallRadianAnglesAreNotSnapped() = assertNotEquals("0", calc("sin(0.0001)", RAD))

    @Test fun angleModeChangesTheResult() {
        assertExact("1", "sin(90)", DEG)
        assertShows("0.893996663600558", "sin(90)", RAD)
    }

    @Test fun inverseSineInDegrees() {
        assertExact("90", "asin(1)", DEG)
        assertExact("30", "asin(0.5)", DEG)
        assertExact("-90", "asin(−1)", DEG)
        assertExact("0", "asin(0)", DEG)
    }

    @Test fun inverseCosineInDegrees() {
        assertExact("90", "acos(0)", DEG)
        assertExact("0", "acos(1)", DEG)
        assertExact("180", "acos(−1)", DEG)
        assertExact("60", "acos(0.5)", DEG)
    }

    @Test fun inverseTangentInDegrees() {
        assertExact("45", "atan(1)", DEG)
        assertExact("-45", "atan(−1)", DEG)
        assertShows("90", "atan(1E+400)", DEG)
    }

    @Test fun inverseFunctionsInRadians() {
        assertShows("1.5707963267949", "asin(1)", RAD)
        assertShows("0.785398163397448", "atan(1)", RAD)
        assertShows("3.14159265358979", "acos(−1)", RAD)
    }

    @Test fun inverseSineRoundTrip() = assertShows("0.3", "sin(asin(0.3))", DEG)

    @Test fun inverseArgumentsOutsideUnitIntervalAreDomainErrors() {
        assertError(CalcError.Domain, "asin(2)", DEG)
        assertError(CalcError.Domain, "acos(−1.5)", RAD)
    }

    @Test fun inverseArgumentWithinToleranceIsClamped() = assertShows("90", "asin(1.0000000000000001)", DEG)

    // ------------------------------------------------------------------ RAD argument reduction

    @Test fun largeRadianArgumentsAreReducedAccurately() {
        assertShows("37754853.3617729", "tan(37362253)", RAD)
        assertShows("5.29733218888625E-8", "sin(74724506)", RAD)
        assertShows("−1.47728468179659E-8", "sin(80143857)", RAD)
        assertShows("0.858272793170236", "sin(1E+15)", RAD)
        assertShows("−0.51319373778697", "cos(1E+15)", RAD)
        assertShows("1.86137070965842", "tan(2E+15)", RAD)
        assertShows("0.833816528149183", "sin(4E+15)", RAD)
        assertShows("−0.852200849767189", "sin(1E+22)", RAD)
        assertShows("−0.645251285265781", "sin(10^(20))", RAD)
    }

    @Test fun radianArgumentsNextToMultiplesOfHalfPiKeepTheirDigits() {
        assertShows("3.23846264338328E-15", "sin(3.14159265358979)", RAD)
        assertShows("−3.38076867830836E-15", "cos(1.5707963267949)", RAD)
        assertShows("−295790719553274", "tan(1.5707963267949)", RAD)
    }

    @Test fun radianMultiplesOfHalfPiStayExact() {
        assertExact("0", "sin(1000000π)", RAD)
        assertExact("1", "cos(1000000π)", RAD)
        assertExact("-1", "sin(3π÷2)", RAD)
        assertError(CalcError.Domain, "tan(3π÷2)", RAD)
        assertExact("0", "tan(−π)", RAD)
    }

    @Test fun astronomicallyLargeRadianArgumentIsADomainError() = assertError(CalcError.Domain, "sin(1E+100)", RAD)

    // ------------------------------------------------------------------ DEG quadrant reduction

    @Test fun degreeArgumentsNextToQuarterTurnsKeepTheirDigits() {
        assertShows("−1.74532925199433E-8", "sin(180.000001)", DEG)
        assertShows("−1.74532925199433E-8", "cos(90.000001)", DEG)
        assertShows("1.74532925199433E-9", "cos(270.0000001)", DEG)
        assertShows("57295779.5130823", "tan(89.999999)", DEG)
        assertShows("−57295779.5130823", "tan(90.000001)", DEG)
        assertShows("1.74532925199433E-10", "sin(179.99999999)", DEG)
    }

    // ------------------------------------------------------------------ tiny arguments

    @Test fun tinyArgumentsAreNotFlushedToZero() {
        assertShows("1.74532925199433E-402", "sin(1E-400)", DEG)
        assertShows("1.74532925199433E-402", "tan(1E-400)", DEG)
        assertShows("5.72957795130823E-399", "asin(1E-400)", DEG)
        assertShows("1E-400", "sin(1E-400)", RAD)
        assertShows("−1E-400", "sin(−1E-400)", RAD)
        assertShows("1E-400", "tan(1E-400)", RAD)
        assertShows("1E-400", "asin(1E-400)", RAD)
        assertShows("1E-400", "atan(1E-400)", RAD)
        assertShows("1", "cos(1E-400)", RAD)
        assertShows("1", "cos(1E-400)", DEG)
        assertShows("90", "acos(1E-400)", DEG)
    }

    @Test fun argumentsBelowTheRangeUnderflowToZero() = assertShows("0", "sin(−1E-99999)", DEG)

    // ------------------------------------------------------------------ asin/acos next to ±1

    @Test fun inverseFunctionsNextToPlusMinusOneUseTheExactComplement() {
        assertShows("0.00000014142135623731", "acos(0.99999999999999)", RAD)
        assertShows("0.00000810284684541396", "acos(0.99999999999999)", DEG)
        assertShows("0.0000141421356238488", "acos(0.9999999999)", RAD)
        assertShows("1.57079618537354", "asin(0.99999999999999)", RAD)
        assertShows("−1.57079618537354", "asin(−0.99999999999999)", RAD)
        assertShows("3.14159251216844", "acos(−0.99999999999999)", RAD)
        assertShows("4.47213595499958E-9", "acos(0.99999999999999999)", RAD)
    }
}
