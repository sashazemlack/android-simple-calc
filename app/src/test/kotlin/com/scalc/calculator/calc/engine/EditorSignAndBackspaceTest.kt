package com.scalc.calculator.calc.engine

import com.scalc.calculator.calc.engine.ExpressionEditor as Ed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** ExpressionEditor: sign toggling, backspace, clear/replace, and consistency with the engine. */
class EditorSignAndBackspaceTest {

    private fun at(expression: String, justEvaluated: Boolean = false) = EditorState(expression, justEvaluated)

    private fun toggled(expression: String) = Ed.toggleSign(at(expression)).expression

    private fun backspaced(expression: String) = Ed.backspace(at(expression)).expression

    // Toggle sign

    @Test fun toggleOnEmptyStartsNegativeNumber() = assertEquals("−", toggled(""))

    @Test fun toggleNegatesFirstNumber() {
        assertEquals("−5", toggled("5"))
        assertEquals("5", toggled("−5"))
    }

    @Test fun toggleFlipsBinaryPlusAndMinus() {
        assertEquals("2−5", toggled("2+5"))
        assertEquals("2+5", toggled("2−5"))
    }

    @Test fun toggleInsertsUnaryMinusAfterMultiplicativeOperators() {
        assertEquals("2×−5", toggled("2×5"))
        assertEquals("2÷−5", toggled("2÷5"))
        assertEquals("2^−3", toggled("2^3"))
    }

    @Test fun toggleRemovesUnaryMinus() {
        assertEquals("2×5", toggled("2×−5"))
        assertEquals("(5", toggled("(−5"))
    }

    @Test fun toggleInsideParenthesesAndFunctions() {
        assertEquals("(−5", toggled("(5"))
        assertEquals("sin(−30", toggled("sin(30"))
    }

    @Test fun toggleCoversWholeOperand() {
        assertEquals("2−(3+4)", toggled("2+(3+4)"))
        assertEquals("2−sin(30)", toggled("2+sin(30)"))
        assertEquals("−2π", toggled("2π"))
        assertEquals("−√9", toggled("√9"))
        assertEquals("−5!", toggled("5!"))
        assertEquals("−10%", toggled("10%"))
        assertEquals("−1.5E+20", toggled("1.5E+20"))
    }

    @Test fun toggleOfModuloOperandUsesParentheses() = assertEquals("10%(−5", toggled("10%5"))

    @Test fun toggleWithoutTrailingOperandTogglesUpcomingSign() {
        assertEquals("5−", toggled("5+"))
        assertEquals("5+", toggled("5−"))
        assertEquals("5×−", toggled("5×"))
        assertEquals("5×", toggled("5×−"))
        assertEquals("(−", toggled("("))
        assertEquals("", toggled("−"))
    }

    @Test fun toggleNegatesResultAndKeepsItEvaluated() {
        assertEquals(at("−42", justEvaluated = true), Ed.toggleSign(at("42", justEvaluated = true)))
        assertEquals(at("42", justEvaluated = true), Ed.toggleSign(at("−42", justEvaluated = true)))
        assertEquals(at("−1.5E+20", justEvaluated = true), Ed.toggleSign(at("1.5E+20", justEvaluated = true)))
    }

    @Test fun toggleOfZeroResultKeepsZero() =
        assertEquals(at("0", justEvaluated = true), Ed.toggleSign(at("0", justEvaluated = true)))

    @Test fun toggledExpressionsEvaluateToNegatedValue() {
        for (expression in listOf("2+5", "2×5", "3!2", "2π", "sin(30)", "(1+2)", "2^3")) {
            val original = valueOf(expression)
            val negated = valueOf(toggled(expression))
            val expected = when (expression) {
                // Toggling the exponent changes 2^3 into 2^−3.
                "2^3" -> valueOf("2^−3")
                // Toggling the last addend changes 2+5 into 2−5.
                "2+5" -> valueOf("2−5")
                else -> original.negate()
            }
            assertEquals(expression, 0, expected.compareTo(negated))
        }
    }

    // Backspace

    @Test fun backspaceRemovesOneCharacter() {
        assertEquals("12", backspaced("123"))
        assertEquals("5", backspaced("5+"))
        assertEquals("cos(3", backspaced("cos(30"))
    }

    @Test fun backspaceOnEmptyStaysEmpty() = assertEquals(EditorState(), Ed.backspace(EditorState()))

    @Test fun backspaceRemovesFunctionNameWithParenthesis() {
        assertEquals("", backspaced("sin("))
        assertEquals("2", backspaced("2asin("))
        assertEquals("2", backspaced("2sin("))
        assertEquals("", backspaced("acos("))
        assertEquals("1+", backspaced("1+atan("))
        assertEquals("", backspaced("log("))
        assertEquals("", backspaced("ln("))
    }

    @Test fun backspaceRemovesOtherInsertionsCharacterByCharacter() {
        assertEquals("√", backspaced("√("))
        assertEquals("10^", backspaced("10^("))
        assertEquals("e^", backspaced("e^("))
    }

    @Test fun backspaceRemovesDanglingExponentMarker() {
        assertEquals("1.5", backspaced("1.5E+2"))
        assertEquals("1.5E+2", backspaced("1.5E+20"))
        assertEquals("2", backspaced("2E5"))
        assertEquals("2", backspaced("2E-9"))
    }

    @Test fun backspaceEditsResultText() = assertEquals(at("4"), Ed.backspace(at("42", justEvaluated = true)))

    // Clear and replace

    @Test fun clearEmptiesTheExpression() = assertEquals(EditorState(), Ed.clear())

    @Test fun replaceWithSetsTextAndLeavesEvaluatedState() =
        assertEquals(at("1+2"), Ed.replaceWith("1+2"))

    // Consistency with the engine

    private fun press(state: EditorState, key: String): EditorState = when {
        key.length == 1 && key[0] in '0'..'9' -> Ed.digit(state, key[0])
        key == "." -> Ed.decimalPoint(state)
        key == "(" -> Ed.openParen(state)
        key == ")" -> Ed.closeParen(state)
        key == "!" -> Ed.factorial(state)
        key == "±" -> Ed.toggleSign(state)
        key == "⌫" -> Ed.backspace(state)
        key == "C" -> Ed.clear()
        key == "π" || key == "e" -> Ed.constant(state, key[0])
        key.length == 1 -> Ed.operator(state, key[0])
        else -> Ed.function(state, key)
    }

    private fun typeKeys(vararg keys: String) = keys.fold(EditorState()) { s, k -> press(s, k) }

    @Test fun typedPrecedenceExample() {
        val state = typeKeys("1", "2", "5", "+", "2", "5", "×", "2")
        assertEquals("125+25×2", state.expression)
        assertExact("175", state.expression)
    }

    @Test fun typedAutoClosedGroup() {
        val state = typeKeys("2", "×", "(", "3", "+", "4")
        assertEquals("2×(3+4", state.expression)
        assertExact("14", state.expression)
    }

    @Test fun typedScientificExpression() {
        val state = typeKeys("3", "sin", "3", "0", ")", "+", "sqrt", "1", "6", ")", "−", "4", "!")
        assertEquals("3sin(30)+√(16)−4!", state.expression)
        assertExact("-18.5", state.expression)
    }

    @Test fun continuingFromResult() {
        var state = typeKeys("1", "÷", "3")
        val value = valueOf(state.expression)
        state = EditorState(CalculatorEngine.format(value), justEvaluated = true)
        state = press(press(state, "×"), "3")
        assertEquals("0.333333333333333×3", state.expression)
        assertEquals("1", calc(state.expression, carry = Carry(CalculatorEngine.format(value), value)))
    }

    @Test fun newNumberAfterResultStartsOver() {
        val state = press(EditorState("42", justEvaluated = true), "7")
        assertEquals("7", state.expression)
        assertFalse(state.justEvaluated)
    }

    /**
     * Random key sequences must never produce a malformed expression: every state the editor
     * reaches is either a valid expression, an incomplete one, or a valid one with an
     * evaluation error.
     */
    @Test fun editorNeverProducesSyntaxErrors() {
        val keys = listOf(
            "0", "1", "2", "5", "9", ".", "+", "−", "×", "÷", "^", "%", "(", ")", "!", "±", "⌫",
            "π", "e", "sin", "asin", "log", "ln", "sqrt", "pow10", "exp", "=", "C",
        )
        val random = Random(20261111)
        repeat(1500) { sequence ->
            var state = EditorState()
            repeat(40) {
                val key = keys[random.nextInt(if (random.nextInt(50) == 0) keys.size else keys.size - 1)]
                state = if (key == "=") {
                    when (val result = CalculatorEngine.evaluate(state.expression, AngleMode.DEG)) {
                        is EvalResult.Value -> EditorState(CalculatorEngine.format(result.value), justEvaluated = true)
                        is EvalResult.Error -> state
                    }
                } else {
                    press(state, key)
                }
                val result = CalculatorEngine.evaluate(state.expression, AngleMode.RAD)
                assertNotEquals(
                    "sequence $sequence produced '${state.expression}'",
                    EvalResult.Error(CalcError.Syntax),
                    result,
                )
                assertTrue(state.expression.length <= Ed.MAX_LENGTH)
            }
        }
    }
}
