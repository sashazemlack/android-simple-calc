package com.scalc.calculator.calc.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/** Basic arithmetic, precedence, parentheses, negative numbers and decimals. */
class ArithmeticTest {

    @Test fun addition() = assertExact("3", "1+2")

    @Test fun subtractionIsLeftAssociative() = assertExact("3", "10−4−3")

    @Test fun divisionIsLeftAssociative() = assertExact("5", "100÷10÷2")

    @Test fun multiplicationBeforeAddition() = assertExact("175", "125+25×2")

    @Test fun mixedPrecedence() = assertExact("11", "2+3×4−6÷2")

    @Test fun nonIntegerDivision() = assertExact("3.5", "7÷2")

    @Test fun oneThirdShowsFifteenDigits() = assertShows("0.333333333333333", "1÷3")

    @Test fun twoThirdsIsRoundedHalfUp() = assertShows("0.666666666666667", "2÷3")

    @Test fun oneThirdTimesThreeDisplaysOne() = assertShows("1", "1÷3×3")

    @Test fun asciiOperatorsAreAccepted() = assertExact("40", "6*7-2/1")

    @Test fun whitespaceIsIgnored() = assertExact("7", " 1 + 2 × 3 ")

    @Test fun largeProductUsesScientificNotation() =
        assertShows("1.21932631112635E+17", "123456789×987654321")

    @Test fun reachingFifteenIntegerDigitsSwitchesToExponent() = assertShows("1E+15", "999999999999999+1")

    @Test fun parenthesesOverridePrecedence() = assertExact("20", "(2+3)×4")

    @Test fun nestedParentheses() = assertExact("21", "((1+2)×(3+4))")

    @Test fun unclosedParenthesisIsAutoClosed() = assertExact("14", "2×(3+4")

    @Test fun severalUnclosedParenthesesAreAutoClosed() = assertExact("46", "2×(3+(4×5")

    @Test fun twoUnclosedGroups() = assertExact("15", "((2+3)×(4−1")

    @Test fun deepNestingOf200Parentheses() {
        val expression = "(".repeat(200) + "1+1" + ")".repeat(200)
        assertExact("2", expression)
    }

    @Test fun deepNestingWithoutClosingParentheses() = assertExact("3", "(".repeat(200) + "3")

    @Test fun pathologicalNestingIsRejectedWithoutCrashing() =
        assertError(CalcError.Syntax, "(".repeat(600) + "1" + ")".repeat(600))

    @Test fun leadingNegativeNumber() = assertShows("−2", "−5+3")

    @Test fun negativeAfterMultiplication() = assertExact("-15", "5×−3")

    @Test fun negativeAfterDivision() = assertExact("-2", "6÷−3")

    @Test fun negatedGroup() = assertExact("-5", "−(2+3)")

    @Test fun doubleNegation() = assertExact("5", "−−5")

    @Test fun subtractingANegative() = assertExact("5", "2−−3")

    @Test fun unaryPlus() = assertExact("5", "+5")

    @Test fun asciiHyphenAsUnaryMinus() = assertExact("-8", "-2*4")

    @Test fun decimalsAddExactly() {
        val value = valueOf("0.1+0.2")
        assertEquals(0, BigDecimal("0.3").compareTo(value))
        assertEquals("0.3", CalculatorEngine.format(value))
    }

    @Test fun decimalSubtraction() = assertExact("0.1", "1−0.9")

    @Test fun decimalMultiplication() = assertExact("0.01", "0.1×0.1")

    @Test fun repeatedDecimalsCancelExactly() = assertShows("0", "3×0.1−0.3")

    @Test fun leadingAndTrailingDecimalPoint() = assertExact("5.5", ".5+5.")

    @Test fun exponentLiteral() = assertExact("150", "1.5E+2")

    @Test fun negativeExponentLiteral() = assertExact("0.0025", "2.5E-3")

    @Test fun exponentLiteralWithUnicodeMinus() = assertExact("0.0025", "2.5E−3")

    @Test fun exponentLiteralWithoutSign() = assertExact("200", "2E2")

    @Test fun constantsArePrecise() {
        assertShows("3.14159265358979", "π")
        assertShows("2.71828182845905", "e")
        assertShows("0", "π−π")
    }

    @Test fun longChainEvaluates() {
        val expression = List(300) { "1" }.joinToString("+")
        assertExact("300", expression)
    }

    @Test fun roundingResidueOfASumIsZero() {
        assertExact("0", "1÷3×3−1")
        assertExact("0", "10÷3×3−10")
        assertExact("0", "1÷3+1÷3+1÷3−1")
        assertExact("0", "1÷9×9−1")
        assertExact("0", "√(1÷3)^2×3−1")
        assertExact("0", "1÷3×3×10^(30)−10^(30)")
    }

    @Test fun exactOperandsCancelAfterARoundedSum() {
        assertExact("0", "1+40!−40!")
        assertExact("0", "0+40!−40!")
        assertExact("0", "1000!−1000!+1000!−1000!")
    }

    @Test fun genuineSmallDifferencesAreKept() {
        assertExact("1", "1E+20+1−1E+20")
        assertExact("1", "10^30+1−10^30")
        assertShows("3.33333333333333E-16", "1÷3−0.333333333333333")
        assertShows("1E-15", "1.000000000000001−1")
    }

    /** Documented trade-off: a difference below 1E-32 of the operands is below the working precision. */
    @Test fun differenceBelowTheWorkingPrecisionIsZero() = assertExact("0", "10^32+1−10^32")
}
