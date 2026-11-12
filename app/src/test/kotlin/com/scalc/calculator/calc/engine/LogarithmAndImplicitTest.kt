package com.scalc.calculator.calc.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/** log, ln, 10^x, e^x, constants and implicit multiplication. */
class LogarithmAndImplicitTest {

    @Test fun logOfPowerOfTenIsExact() {
        assertExact("3", "log(1000)")
        assertExact("500", "log(1E+500)")
        assertExact("-3", "log(0.001)")
        assertExact("-500", "log(1E-500)")
        assertExact("0", "log(1)")
    }

    @Test fun logOfTwo() = assertShows("0.301029995663981", "log(2)")

    @Test fun logNearOneIsAccurate() {
        // log10(1 + 1e-7) = (1e-7 − 5e-15 + …) / ln 10 = 4.3429446018852…E-8
        val shown = calc("log(1.0000001)")
        assertTrue(shown, shown.startsWith("4.342944601885") && shown.endsWith("E-8"))
    }

    @Test fun naturalLogarithms() {
        assertExact("0", "ln(1)")
        assertShows("1", "ln(e)")
        assertShows("0.693147180559945", "ln(2)")
    }

    @Test fun naturalLogOfHugeNumber() = assertShows("2302.58509299405", "ln(1E+1000)")

    @Test fun naturalLogNearOneIsAccurate() = assertShows("0.000000999999500000333", "ln(1.000001)")

    @Test fun logarithmDomainErrors() {
        assertError(CalcError.Domain, "log(0)")
        assertError(CalcError.Domain, "log(−5)")
        assertError(CalcError.Domain, "ln(0)")
        assertError(CalcError.Domain, "ln(−1)")
    }

    @Test fun tenToThePowerOfLogRoundTrips() = assertShows("2", "10^(log(2))")

    @Test fun exponentialOfLnRoundTrips() = assertShows("5", "e^(ln(5))")

    @Test fun lnOfExponential() = assertShows("3", "ln(e^(3))")

    @Test fun logOfPowerOfTen() = assertExact("7", "log(10^(7))")

    @Test fun exponentialOfFraction() = assertShows("1.64872127070013", "e^(0.5)")

    @Test fun implicitMultiplicationNumberAndConstant() {
        assertShows("6.28318530717959", "2π")
        assertShows("6.28318530717959", "π2")
        assertShows("5.43656365691809", "2e")
    }

    @Test fun implicitMultiplicationOfConstants() {
        assertShows("9.86960440108936", "ππ")
        assertShows("8.53973422267357", "eπ")
    }

    @Test fun implicitMultiplicationWithParentheses() {
        assertExact("8", "2(3+1)")
        assertExact("8", "(1+1)(2+2)")
        assertExact("6", "(2)3")
        assertExact("24", "2(3)(4)")
    }

    @Test fun implicitMultiplicationAfterFactorial() = assertExact("12", "3!2")

    @Test fun implicitMultiplicationBindsLikeTimes() = assertShows("1.5707963267949", "1÷2π")

    @Test fun implicitOperandIncludesItsPower() = assertShows("19.7392088021787", "2π^2")

    @Test fun implicitMultiplicationWithNegativeLeft() = assertExact("-6", "−2(3)")

    @Test fun implicitMultiplicationWithFunctions() {
        assertExact("6", "2log(1000)")
        assertExact("4", "(2)√4")
    }

    @Test fun adjacentNumbersAreNotMultiplied() = assertError(CalcError.Syntax, "2 3")
}
