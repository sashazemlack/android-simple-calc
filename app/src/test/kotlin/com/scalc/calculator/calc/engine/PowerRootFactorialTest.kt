package com.scalc.calculator.calc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Exponents, square roots and factorials. */
class PowerRootFactorialTest {

    @Test fun integerPower() = assertExact("1024", "2^10")

    @Test fun powerIsRightAssociative() = assertExact("512", "2^3^2")

    @Test fun negativeExponent() = assertExact("0.5", "2^−1")

    @Test fun negativeExponentGivesExactFraction() = assertExact("0.125", "2^−3")

    @Test fun unaryMinusBindsLooserThanPower() = assertExact("-4", "−2^2")

    @Test fun parenthesisedNegativeBaseEvenPower() = assertExact("4", "(−2)^2")

    @Test fun parenthesisedNegativeBaseOddPower() = assertExact("-8", "(−2)^3")

    @Test fun decimalBaseIsExact() = assertExact("1.21", "1.1^2")

    @Test fun squareRootViaHalfPower() = assertExact("2", "4^0.5")

    @Test fun irrationalPower() = assertShows("1.4142135623731", "2^0.5")

    @Test fun nestedFractionalPowerIsRightAssociative() = assertShows("1.18920711500272", "2^0.5^2")

    @Test fun cubeRootViaFractionalExponent() = assertShows("3", "27^(1÷3)")

    @Test fun zeroToTheZero() = assertExact("1", "0^0")

    @Test fun anythingToTheZero() = assertExact("1", "5^0")

    @Test fun zeroToPositivePower() = assertExact("0", "0^5")

    @Test fun zeroToNegativePowerIsDivisionByZero() = assertError(CalcError.DivisionByZero, "0^−1")

    @Test fun negativeBaseWithFractionalExponentIsDomainError() = assertError(CalcError.Domain, "(−8)^(1÷3)")

    @Test fun powerOfTenIsExact() {
        assertExact("1000", "10^(3)")
        assertShows("0.00001", "10^(−5)")
    }

    @Test fun largeIntegerPowerShowsFifteenDigits() = assertShows("1.26765060022823E+30", "2^100")

    @Test fun largestExactPowerOfTen() = assertShows("1E+99999", "10^99999")

    @Test fun powerBeyondLimitOverflows() = assertError(CalcError.Overflow, "10^100000")

    @Test fun hugeIntegerExponentShowsFifteenCorrectDigits() {
        assertShows("9.99002093014385E+30102", "2^100000")
        assertShows("3.03125638434508E+58903", "3^123456")
        assertShows("4.45758327949227E+84510", "7^100001")
        assertShows("1.00099890379869E-30103", "2^(−100000)")
    }

    @Test fun hugeIntegerExponentIsFast() {
        repeat(3) { CalculatorEngine.evaluate("1.0000001^999999999", AngleMode.DEG) }
        val bestMillis = (1..5).minOf {
            val start = System.nanoTime()
            CalculatorEngine.evaluate("1.0000001^999999999", AngleMode.DEG)
            (System.nanoTime() - start) / 1e6
        }
        assertTrue("took $bestMillis ms", bestMillis < 5.0)
    }

    @Test fun towerOverflows() = assertError(CalcError.Overflow, "9^9^9")

    @Test fun hugeNegativeExponentUnderflowsToZero() = assertShows("0", "10^(−100001)")

    @Test fun minusOneToHugeOddPower() = assertExact("-1", "(−1)^1000001")

    @Test fun eulerPower() = assertShows("7.38905609893065", "e^(2)")

    @Test fun eulerPowerWithNonIntegerExponentUsesExp() {
        assertShows("1.64872127070013", "e^(0.5)")
        assertShows("8.54813428729806E+21", "e^(50.5)")
        assertShows("1.6721859620675E+304", "e^(700.5)")
        assertShows("5.98019611863979E-305", "e^(−700.5)")
        assertError(CalcError.Overflow, "e^(300000.5)")
        assertShows("0", "e^(−300000.5)")
    }

    @Test fun squareRoot() = assertExact("3", "√9")

    @Test fun squareRootOfGroup() = assertExact("4", "√(9+7)")

    @Test fun squareRootAppliesToPower() = assertExact("4", "√4^2")

    @Test fun squareRootOfExactSquareIsExact() {
        val value = valueOf("√2.25")
        assertEquals(0, BigDecimal("1.5").compareTo(value))
        assertEquals("1.5", value.toPlainString())
    }

    @Test fun squareRootOfTwo() = assertShows("1.4142135623731", "√2")

    @Test fun squareRootSquaredDisplaysExactly() = assertShows("2", "√2×√2")

    @Test fun squareRootWithImplicitMultiplication() = assertExact("6", "2√9")

    @Test fun nestedSquareRoots() = assertExact("2", "√√16")

    @Test fun negatedSquareRoot() = assertExact("-2", "−√4")

    @Test fun squareRootOfZero() = assertExact("0", "√0")

    @Test fun squareRootOfHugeNumber() = assertShows("1E+50", "√(1E+100)")

    @Test fun squareRootOfTinyNumber() = assertShows("1E-50", "√(1E-100)")

    @Test fun squareRootOfNegativeIsDomainError() = assertError(CalcError.Domain, "√−4")

    @Test fun squareRootOfNegativeDecimalIsDomainError() = assertError(CalcError.Domain, "√(−0.01)")

    @Test fun squareRootWithAutoClose() = assertShows("1.4142135623731", "√(2")

    @Test fun parenthesisedRadicandEndsTheRoot() {
        assertExact("2", "√(4)!")
        assertExact("0.04", "√(16)%")
        assertError(CalcError.Domain, "√(−4)^2")
        assertExact("208", "200+√(16)%")
        assertExact("8", "√(16)×2")
    }

    @Test fun unparenthesisedRadicandBindsAtUnaryLevel() {
        assertShows("4.89897948556636", "√4!")
        assertExact("4", "√4^2")
        assertError(CalcError.Domain, "√−4")
    }

    @Test fun openSquareRootIsIncomplete() = assertError(CalcError.Incomplete, "√(")

    @Test fun factorial() = assertExact("120", "5!")

    @Test fun factorialOfZeroAndOne() {
        assertExact("1", "0!")
        assertExact("1", "1!")
    }

    @Test fun doubleFactorialAppliesTwice() = assertExact("720", "3!!")

    @Test fun factorialOfGroup() = assertExact("6", "(2+1)!")

    @Test fun factorialBindsTighterThanUnaryMinus() = assertExact("-6", "−3!")

    @Test fun factorialQuotient() = assertExact("12", "4!÷2!")

    @Test fun factorialOfExactRoot() = assertExact("6", "(√9)!")

    @Test fun largeFactorialIsFormatted() = assertShows("2.43290200817664E+18", "20!")

    @Test fun thousandFactorial() = assertShows("4.02387260077094E+2567", "1000!")

    @Test fun factorialsUpToTheDisplayRange() {
        assertShows("4.02789647337171E+2570", "1001!")
        assertShows("4.14935960343785E+9130", "3000!")
        assertShows("2.84625968091705E+35659", "10000!")
        assertShows("4.7833985485965E+99995", "25205!")
    }

    @Test fun factorialBeyondLimitOverflows() = assertError(CalcError.Overflow, "25206!")

    @Test fun repeatedLargeFactorialIsCached() {
        CalculatorEngine.evaluate("25205!", AngleMode.DEG)
        val bestMillis = (1..5).minOf {
            val start = System.nanoTime()
            CalculatorEngine.evaluate("25205!", AngleMode.DEG)
            (System.nanoTime() - start) / 1e6
        }
        assertTrue("25205! took $bestMillis ms", bestMillis < 5.0)
    }

    @Test fun factorialOfNegativeIsInvalid() = assertError(CalcError.InvalidFactorial, "(−3)!")

    @Test fun factorialOfFractionIsInvalid() = assertError(CalcError.InvalidFactorial, "2.5!")

    @Test fun factorialToleratesDoubleNoise() = assertExact("120", "(10^(log(5)))!")

    @Test fun hugePowerOfFactorialOverflows() = assertError(CalcError.Overflow, "1000!^100")
}
