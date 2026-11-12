package com.scalc.calculator.calc.engine

import org.junit.Test

/** Postfix percent, additive percent and the modulo reading of '%'. */
class PercentModuloTest {

    @Test fun percentAlone() = assertExact("0.1", "10%")

    @Test fun percentOfGroup() = assertExact("0.1", "(5+5)%")

    @Test fun addPercentOfRunningTotal() = assertExact("220", "200+10%")

    @Test fun subtractPercentOfRunningTotal() = assertExact("180", "200−10%")

    @Test fun addPercentOfParenthesisedValue() = assertExact("55", "50+(10)%")

    @Test fun addPercentOfGroupExpression() = assertExact("110", "100+(5+5)%")

    @Test fun percentAppliesToWholeRunningSum() = assertExact("165", "100+50+10%")

    @Test fun signedPercentTerm() = assertExact("180", "200+−10%")

    @Test fun multiplyByPercent() = assertExact("20", "200×10%")

    @Test fun divideByPercent() = assertExact("2000", "200÷10%")

    @Test fun percentInsideLargerTermIsPlainPercent() = assertExact("200.2", "200+10%×2")

    @Test fun percentInsideParenthesesIsNotAdditive() = assertExact("200.1", "200+(10%)")

    @Test fun percentOnLeftOperand() = assertExact("5.1", "10%+5")

    @Test fun negatedPercent() = assertExact("-0.1", "−10%")

    @Test fun repeatedPercent() = assertExact("0.005", "50%%")

    @Test fun percentFollowedByMinusIsSubtraction() = assertExact("-2.9", "10%−3")

    @Test fun percentThenFactorialIsInvalid() = assertError(CalcError.InvalidFactorial, "10%!")

    @Test fun moduloOfIntegers() = assertExact("1", "10%3")

    @Test fun moduloKeepsSignOfDividend() = assertExact("-1", "−7%3")

    @Test fun moduloOfDecimal() = assertExact("1.5", "7.5%2")

    @Test fun moduloWithNegativeDivisor() = assertExact("1", "10%(−3)")

    @Test fun moduloByZero() = assertError(CalcError.DivisionByZero, "5%0")

    @Test fun moduloHasMultiplicativePrecedence() = assertExact("4", "2+10%3×2")

    @Test fun moduloIsLeftAssociative() = assertExact("2", "17%5%3")

    @Test fun moduloWithZeroResult() = assertExact("0", "10%2.5")

    @Test fun moduloOfHugeDividendIsExact() = assertExact("2", "1E+20%7")

    // 10 ≡ 3 (mod 7), 3^6 ≡ 1 and 99999 ≡ 3 (mod 6), so 10^99999 ≡ 27 ≡ 6.
    @Test fun moduloOfHugeExponentIsFast() = assertExact("6", "1E+99999%7")

    @Test fun moduloBySmallerDividendReturnsDividend() = assertExact("3", "3%10")

    @Test fun moduloByConstant() = assertShows("0.57522203923062", "10%π")

    @Test fun moduloAfterPercentOperand() = assertExact("0.1", "10%%3")
}
