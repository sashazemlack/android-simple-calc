package com.scalc.calculator.calc.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** Syntax vs. Incomplete classification and evaluation errors. */
class ErrorClassificationTest {

    private fun assertAll(error: CalcError, vararg expressions: String) {
        for (expression in expressions) assertError(error, expression)
    }

    @Test fun leadingBinaryOperatorIsSyntax() = assertAll(CalcError.Syntax, "×5", "÷5", "^2", "%5", "!")

    @Test fun repeatedDecimalPointIsSyntax() = assertAll(CalcError.Syntax, "5..2", "1.2.3", "5..")

    @Test fun lonePointIsSyntax() = assertAll(CalcError.Syntax, ".", "5+.")

    @Test fun unmatchedClosingParenthesisIsSyntax() = assertAll(CalcError.Syntax, "5)", "(5))", ")", ")(", "2+)")

    @Test fun unknownCharactersAreSyntax() = assertAll(CalcError.Syntax, "abc", "5#3", "5,5", "2x3", "E5", "pi")

    @Test fun functionWithoutParenthesisIsSyntax() = assertAll(CalcError.Syntax, "sin5", "log 5", "sin+1")

    @Test fun operatorFollowedByOperatorIsSyntax() = assertAll(CalcError.Syntax, "5+×3", "5×÷3", "5^×2")

    @Test fun malformedExponentIsSyntax() = assertAll(CalcError.Syntax, "2E×3", "2E+×3")

    @Test fun overlongExpressionIsSyntax() {
        val expression = "1+".repeat(500) + "1"
        assertEquals(1001, expression.length)
        assertError(CalcError.Syntax, expression)
    }

    @Test fun expressionOfMaximumLengthEvaluates() {
        val expression = "1+".repeat(499) + "1" + "0"
        assertEquals(1000, expression.length)
        assertExact("509", expression)
    }

    @Test fun emptyExpressionIsIncomplete() = assertAll(CalcError.Incomplete, "", "   ")

    @Test fun trailingOperatorIsIncomplete() = assertAll(CalcError.Incomplete, "5+", "5−", "5×", "5÷", "2^", "10%3×")

    @Test fun trailingUnaryMinusIsIncomplete() = assertAll(CalcError.Incomplete, "5×−", "−", "(−", "2^−")

    @Test fun openFunctionIsIncomplete() = assertAll(CalcError.Incomplete, "sin(", "cos(", "log(", "ln(", "asin(", "sin")

    @Test fun openParenthesisIsIncomplete() = assertAll(CalcError.Incomplete, "(", "(((", "2×(", "5×(2+")

    @Test fun emptyParenthesesAreIncomplete() = assertAll(CalcError.Incomplete, "()", "sin()", "2×()")

    @Test fun squareRootWithoutOperandIsIncomplete() = assertAll(CalcError.Incomplete, "√", "2+√", "√(")

    @Test fun unfinishedExponentIsIncomplete() = assertAll(CalcError.Incomplete, "1.5E", "1.5E+", "1.5E−")

    @Test fun syntaxErrorBeforeIncompleteEndWins() = assertError(CalcError.Syntax, "5)+")

    @Test fun incompleteWinsOverEvaluationErrors() = assertError(CalcError.Incomplete, "1÷0+")

    @Test fun divisionByZero() = assertAll(CalcError.DivisionByZero, "1÷0", "0÷0", "5÷(3−3)", "1÷(2−2)×3", "2/0")

    @Test fun divisionByZeroInsideFunction() = assertError(CalcError.DivisionByZero, "sin(1÷0)")

    @Test fun domainErrors() = assertAll(
        CalcError.Domain,
        "√−4", "log(0)", "ln(−1)", "tan(90)", "asin(2)", "acos(−1.5)", "(−8)^0.5",
    )

    @Test fun invalidFactorials() = assertAll(CalcError.InvalidFactorial, "(−1)!", "0.5!", "(1÷3)!")

    @Test fun overflowingLiteral() = assertAll(CalcError.Overflow, "1E+100000", "1E+99999×10", "9.99E+99999×2")

    @Test fun overflowingPowers() = assertAll(CalcError.Overflow, "(1E+50000)^3", "10^100000", "10^(10^6)")

    @Test fun literalBeyondBigDecimalRangeOverflows() = assertError(CalcError.Overflow, "1E+99999999999")

    @Test fun tinyLiteralUnderflowsToZero() = assertShows("0", "1E-99999999999")

    @Test fun underflowIsFlushedToZero() = assertShows("0", "1E-99999÷1E+10")
}
