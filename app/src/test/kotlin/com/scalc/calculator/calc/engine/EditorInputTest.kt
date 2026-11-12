package com.scalc.calculator.calc.engine

import com.scalc.calculator.calc.engine.ExpressionEditor as Ed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** ExpressionEditor: digits, decimal point, operators, functions, constants, parentheses, factorial. */
class EditorInputTest {

    private fun at(expression: String, justEvaluated: Boolean = false) = EditorState(expression, justEvaluated)

    private fun typeDigits(start: EditorState, digits: String) = digits.fold(start) { s, d -> Ed.digit(s, d) }

    // Digits

    @Test fun digitsAppend() = assertEquals("123", typeDigits(EditorState(), "123").expression)

    @Test fun leadingZeroIsReplaced() = assertEquals("5", Ed.digit(at("0"), '5').expression)

    @Test fun leadingZeroAfterOperatorIsReplaced() = assertEquals("1+5", Ed.digit(at("1+0"), '5').expression)

    @Test fun repeatedZeroStaysSingle() = assertEquals("0", Ed.digit(at("0"), '0').expression)

    @Test fun zerosAfterDecimalPointAreKept() = assertEquals("0.00", Ed.digit(at("0.0"), '0').expression)

    @Test fun numbersAreLimitedToFifteenDigits() {
        val state = typeDigits(EditorState(), "1234567890123456789")
        assertEquals("123456789012345", state.expression)
    }

    @Test fun digitLimitCountsFractionDigits() {
        val state = typeDigits(at("1.2345678901234"), "567")
        assertEquals("1.23456789012345", state.expression)
    }

    @Test fun digitLimitIsPerNumber() {
        val state = typeDigits(at("123456789012345+"), "12")
        assertEquals("123456789012345+12", state.expression)
    }

    @Test fun digitAfterClosingParenthesisIsAppended() = assertEquals("(2)3", Ed.digit(at("(2)"), '3').expression)

    @Test fun digitAfterConstantIsAppended() = assertEquals("π3", Ed.digit(at("π"), '3').expression)

    @Test fun digitAfterFactorialIsAppended() = assertEquals("3!2", Ed.digit(at("3!"), '2').expression)

    @Test fun digitAfterPercentIsAppended() = assertEquals("10%3", Ed.digit(at("10%"), '3').expression)

    @Test fun digitAfterResultStartsNewExpression() = assertEquals(at("7"), Ed.digit(at("42", justEvaluated = true), '7'))

    @Test fun digitExtendsExponent() = assertEquals("1.5E+20", Ed.digit(at("1.5E+2"), '0').expression)

    @Test fun nonDigitIsIgnored() = assertEquals(at("5"), Ed.digit(at("5"), 'x'))

    // Decimal point

    @Test fun decimalPointOnEmptyInsertsZero() = assertEquals("0.", Ed.decimalPoint(EditorState()).expression)

    @Test fun decimalPointAppendsToNumber() = assertEquals("5.", Ed.decimalPoint(at("5")).expression)

    @Test fun secondDecimalPointIsIgnored() {
        assertEquals("5.", Ed.decimalPoint(at("5.")).expression)
        assertEquals("1.5", Ed.decimalPoint(at("1.5")).expression)
    }

    @Test fun decimalPointInExponentIsIgnored() = assertEquals("1.5E+20", Ed.decimalPoint(at("1.5E+20")).expression)

    @Test fun decimalPointAfterOperatorOrParenthesisInsertsZero() {
        assertEquals("5+0.", Ed.decimalPoint(at("5+")).expression)
        assertEquals("(0.", Ed.decimalPoint(at("(")).expression)
    }

    @Test fun decimalPointAfterOperandEndMultiplies() {
        assertEquals("(2)×0.", Ed.decimalPoint(at("(2)")).expression)
        assertEquals("π×0.", Ed.decimalPoint(at("π")).expression)
        assertEquals("3!×0.", Ed.decimalPoint(at("3!")).expression)
    }

    @Test fun decimalPointAfterResultStartsNewNumber() =
        assertEquals(at("0."), Ed.decimalPoint(at("42", justEvaluated = true)))

    // Operators

    @Test fun onlyMinusStartsAnEmptyExpression() {
        assertEquals("−", Ed.operator(EditorState(), '−').expression)
        assertEquals("", Ed.operator(EditorState(), '+').expression)
        assertEquals("", Ed.operator(EditorState(), '×').expression)
        assertEquals("", Ed.operator(EditorState(), '%').expression)
        assertEquals("", Ed.operator(EditorState(), '^').expression)
    }

    @Test fun operatorAppendsAfterNumber() = assertEquals("5+", Ed.operator(at("5"), '+').expression)

    @Test fun trailingOperatorIsReplaced() {
        assertEquals("5×", Ed.operator(at("5+"), '×').expression)
        assertEquals("5+", Ed.operator(at("5−"), '+').expression)
        assertEquals("5−", Ed.operator(at("5+"), '−').expression)
        assertEquals("5÷", Ed.operator(at("5^"), '÷').expression)
    }

    @Test fun minusAfterMultiplicativeOperatorIsUnary() {
        assertEquals("5×−", Ed.operator(at("5×"), '−').expression)
        assertEquals("5÷−", Ed.operator(at("5÷"), '−').expression)
        assertEquals("5^−", Ed.operator(at("5^"), '−').expression)
    }

    @Test fun operatorAfterUnaryMinusReplacesBoth() = assertEquals("5+", Ed.operator(at("5×−"), '+').expression)

    @Test fun secondUnaryMinusIsIgnored() = assertEquals("5×−", Ed.operator(at("5×−"), '−').expression)

    @Test fun afterOpeningParenthesisOnlyMinusIsAccepted() {
        assertEquals("(", Ed.operator(at("("), '×').expression)
        assertEquals("(−", Ed.operator(at("("), '−').expression)
        assertEquals("(−", Ed.operator(at("(−"), '+').expression)
        assertEquals("sin(−", Ed.operator(at("sin("), '−').expression)
    }

    @Test fun leadingMinusCannotBecomeBinaryOperator() = assertEquals("−", Ed.operator(at("−"), '+').expression)

    @Test fun afterSquareRootOnlyMinusIsAccepted() {
        assertEquals("√", Ed.operator(at("√"), '×').expression)
        assertEquals("√−", Ed.operator(at("√"), '−').expression)
        assertEquals("2×√−", Ed.operator(at("2×√−"), '×').expression)
    }

    @Test fun operatorsFollowPostfixOperators() {
        assertEquals("5%−", Ed.operator(at("5%"), '−').expression)
        assertEquals("5%+", Ed.operator(at("5%"), '+').expression)
        assertEquals("5!×", Ed.operator(at("5!"), '×').expression)
    }

    @Test fun percentReplacesTrailingOperator() = assertEquals("5%", Ed.operator(at("5+"), '%').expression)

    @Test fun asciiOperatorsAreCanonicalized() {
        assertEquals("5×", Ed.operator(at("5"), '*').expression)
        assertEquals("5÷", Ed.operator(at("5"), '/').expression)
        assertEquals("5−", Ed.operator(at("5"), '-').expression)
    }

    @Test fun unknownOperatorIsIgnored() = assertEquals(at("5"), Ed.operator(at("5"), 'x'))

    @Test fun operatorContinuesFromResult() = assertEquals(at("42+"), Ed.operator(at("42", justEvaluated = true), '+'))

    @Test fun percentContinuesFromResult() = assertEquals(at("42%"), Ed.operator(at("42", justEvaluated = true), '%'))

    @Test fun powerOfNegativeResultIsParenthesised() =
        assertEquals(at("(−5)^"), Ed.operator(at("−5", justEvaluated = true), '^'))

    @Test fun inputStopsAtMaximumLength() {
        val full = at("1+".repeat(99) + "11")
        assertEquals(200, full.expression.length)
        assertEquals(full, Ed.operator(full, '+'))
        val filled = Ed.digit(at("1+".repeat(99) + "1"), '1')
        assertEquals(full, filled)
        assertEquals(full, Ed.digit(filled, '1'))
        assertEquals(full, Ed.function(full, "sin"))
        assertEquals(full, Ed.factorial(full))
    }

    // Functions and constants

    @Test fun functionsInsertNameAndParenthesis() {
        val expected = mapOf(
            "sin" to "sin(", "cos" to "cos(", "tan" to "tan(", "asin" to "asin(", "acos" to "acos(",
            "atan" to "atan(", "log" to "log(", "ln" to "ln(", "sqrt" to "√(", "pow10" to "10^(", "exp" to "e^(",
        )
        for ((name, text) in expected) assertEquals(name, text, Ed.function(EditorState(), name).expression)
    }

    @Test fun functionAfterNumberIsImplicitMultiplication() = assertEquals("2sin(", Ed.function(at("2"), "sin").expression)

    @Test fun powerOfTenAfterNumberMultipliesExplicitly() = assertEquals("2×10^(", Ed.function(at("2"), "pow10").expression)

    @Test fun functionAfterResultStartsNewExpression() = assertEquals(at("sin("), Ed.function(at("42", justEvaluated = true), "sin"))

    @Test fun unknownFunctionIsIgnored() = assertEquals(at("2"), Ed.function(at("2"), "cot"))

    @Test fun constantsAppend() {
        assertEquals("π", Ed.constant(EditorState(), 'π').expression)
        assertEquals("2π", Ed.constant(at("2"), 'π').expression)
        assertEquals("(1)e", Ed.constant(at("(1)"), 'e').expression)
    }

    @Test fun constantAfterResultStartsNewExpression() = assertEquals(at("e"), Ed.constant(at("42", justEvaluated = true), 'e'))

    @Test fun unknownConstantIsIgnored() = assertEquals(at("2"), Ed.constant(at("2"), 'x'))

    // Parentheses

    @Test fun openParenthesisAppends() {
        assertEquals("(", Ed.openParen(EditorState()).expression)
        assertEquals("2(", Ed.openParen(at("2")).expression)
    }

    @Test fun openParenthesisAfterResultStartsNewExpression() = assertEquals(at("("), Ed.openParen(at("42", justEvaluated = true)))

    @Test fun closeParenthesisClosesOpenGroup() {
        assertEquals("(2)", Ed.closeParen(at("(2")).expression)
        assertEquals("((2))", Ed.closeParen(at("((2)")).expression)
        assertEquals("sin(30)", Ed.closeParen(at("sin(30")).expression)
        assertEquals("(5%)", Ed.closeParen(at("(5%")).expression)
    }

    @Test fun closeParenthesisWithoutOpenGroupIsIgnored() {
        assertEquals("2", Ed.closeParen(at("2")).expression)
        assertEquals("(2)", Ed.closeParen(at("(2)")).expression)
    }

    @Test fun closeParenthesisOfIncompleteGroupIsIgnored() {
        assertEquals("(2+", Ed.closeParen(at("(2+")).expression)
        assertEquals("(", Ed.closeParen(at("(")).expression)
        assertEquals("sin(", Ed.closeParen(at("sin(")).expression)
    }

    // Factorial

    @Test fun factorialAppends() {
        assertEquals("5!", Ed.factorial(at("5")).expression)
        assertEquals("5!!", Ed.factorial(at("5!")).expression)
        assertEquals("(3)!", Ed.factorial(at("(3)")).expression)
    }

    @Test fun factorialWithoutOperandIsIgnored() {
        assertEquals("", Ed.factorial(EditorState()).expression)
        assertEquals("5+", Ed.factorial(at("5+")).expression)
        assertEquals("(", Ed.factorial(at("(")).expression)
    }

    @Test fun factorialContinuesFromResult() {
        val state = Ed.factorial(at("5", justEvaluated = true))
        assertEquals("5!", state.expression)
        assertFalse(state.justEvaluated)
    }

    @Test fun factorialOfNegativeResultIsParenthesised() =
        assertEquals("(−5)!", Ed.factorial(at("−5", justEvaluated = true)).expression)
}
