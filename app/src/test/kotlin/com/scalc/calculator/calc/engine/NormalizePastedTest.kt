package com.scalc.calculator.calc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Clipboard text normalization. */
class NormalizePastedTest {

    private fun paste(text: String) = CalculatorEngine.normalizePasted(text)

    @Test fun plainExpressionIsKept() = assertEquals("125+25×2", paste("125+25×2"))

    @Test fun groupingCommasAreRemoved() = assertEquals("1234567.5", paste("  1,234,567.5 "))

    @Test fun singleGroupingCommaWithThreeDigits() = assertEquals("1234", paste("1,234"))

    @Test fun decimalCommaIsConverted() {
        assertEquals("3.5", paste("3,5"))
        assertEquals("2.25", paste("2,25"))
        assertEquals("1.2345", paste("1,2345"))
    }

    @Test fun threeDigitsAfterALeadingZeroAreDecimals() {
        assertEquals("0.500", paste("0,500"))
        assertEquals("0.250", paste("0,250"))
        assertEquals("5+0.125", paste("5+0,125"))
        assertNull(paste("0,500,000"))
    }

    @Test fun commaHeuristicIsOtherwiseUnchanged() {
        assertEquals("1234", paste("1,234"))
        assertEquals("12500", paste("12,500"))
        assertEquals("0.5", paste("0,5"))
        assertEquals("3.14159", paste("3,14159"))
        assertEquals("1234567.5", paste("1,234,567.5"))
    }

    @Test fun ambiguousCommasAreRejected() {
        assertNull(paste("1,23,456"))
        assertNull(paste("1,5,5"))
        assertNull(paste("1.5,25"))
    }

    @Test fun whitespaceIncludingNonBreakingSpacesIsRemoved() {
        val noBreakSpace = Char(0x00A0)
        val thinSpace = Char(0x2009)
        val zeroWidthSpace = Char(0x200B)
        assertEquals("1000", paste("1${noBreakSpace}000"))
        assertEquals("1000+2", paste("1${thinSpace}000 + 2"))
        assertEquals("12", paste("1${zeroWidthSpace}2"))
    }

    @Test fun asciiOperatorsAreMapped() {
        assertEquals("2×3", paste("2*3"))
        assertEquals("6÷2", paste("6/2"))
        assertEquals("5−3", paste("5-3"))
        assertEquals("2×3", paste("2∗3"))
    }

    @Test fun dashesBecomeMinus() {
        assertEquals("5−3", paste("5–3"))
        assertEquals("5−3", paste("5—3"))
        assertEquals("−5", paste("-5"))
    }

    @Test fun doubleStarIsPower() = assertEquals("2^3", paste("2**3"))

    @Test fun piWord() {
        assertEquals("2π", paste("2 pi"))
        assertEquals("π", paste("PI"))
    }

    @Test fun sqrtWord() = assertEquals("√(16)", paste("sqrt(16)"))

    @Test fun inverseFunctionSpellings() {
        assertEquals("asin(0.5)", paste("arcsin(0.5)"))
        assertEquals("acos(0.5)", paste("arccos(0.5)"))
        assertEquals("atan(1)", paste("arctan(1)"))
        assertEquals("asin(0.5)", paste("sin⁻¹(0.5)"))
        assertEquals("atan(1)", paste("atan(1)"))
    }

    @Test fun functionNamesAreCaseInsensitive() {
        assertEquals("sin(30)", paste("SIN(30)"))
        assertEquals("ln(e)", paste("LN(E)"))
    }

    @Test fun log10IsLog() = assertEquals("log(100)", paste("log10(100)"))

    @Test fun expIsEulerPower() = assertEquals("e^(1)", paste("exp(1)"))

    @Test fun modWordIsModulo() = assertEquals("10%3", paste("10 mod 3"))

    @Test fun superscriptSquares() = assertEquals("3^2", paste("3²"))

    @Test fun equalsSignsAreStripped() {
        assertEquals("5+5", paste("=5+5"))
        assertEquals("5+5", paste("5+5="))
    }

    @Test fun scientificNotationIsKept() {
        assertEquals("1.5E+20", paste("1.5E+20"))
        assertEquals("2E-9", paste("2E−9"))
    }

    @Test fun lowerCaseExponentIsScientificNotation() {
        assertEquals("1E-7", paste("1e-7"))
        assertEquals("2.5E3", paste("2.5e3"))
    }

    @Test fun eulerConstantIsNotAnExponent() {
        assertEquals("2e", paste("2e"))
        assertEquals("e−1", paste("e-1"))
        assertEquals("2e−1", paste("2e−1"))
    }

    @Test fun incompleteExpressionsAreAccepted() {
        assertEquals("5+", paste("5+"))
        assertEquals("sin(", paste("sin("))
    }

    @Test fun blankTextIsRejected() {
        assertNull(paste(""))
        assertNull(paste("   "))
        assertNull(paste("="))
    }

    @Test fun invalidTextIsRejected() {
        assertNull(paste("abc"))
        assertNull(paste("5+×"))
        assertNull(paste("5)"))
        assertNull(paste("hello world"))
    }

    @Test fun tooLongTextIsRejected() {
        assertNull(paste("1+".repeat(500) + "1"))
        assertNull(paste("1".repeat(20_000)))
    }

    @Test fun evaluationErrorsAreStillValidPastes() {
        assertEquals("1÷0", paste("1/0"))
        assertEquals("√(−4)", paste("sqrt(-4)"))
    }

    @Test fun pastedExpressionEvaluates() = assertExact("4", paste("2 * (1.5 + 0.5)")!!)

    @Test fun checkSyntaxParsesWithoutEvaluating() {
        assertNull(CalculatorEngine.checkSyntax("1÷0"))
        assertNull(CalculatorEngine.checkSyntax("25205!×25205!"))
        assertEquals(CalcError.Incomplete, CalculatorEngine.checkSyntax("5+"))
        assertEquals(CalcError.Incomplete, CalculatorEngine.checkSyntax("√("))
        assertEquals(CalcError.Syntax, CalculatorEngine.checkSyntax("5+×3"))
        assertEquals(CalcError.Syntax, CalculatorEngine.checkSyntax("(".repeat(600) + "1"))
        assertEquals(CalcError.Syntax, CalculatorEngine.checkSyntax("1".repeat(CalculatorEngine.MAX_EXPRESSION_LENGTH + 1)))
    }

    /** Paste runs on the main thread, so it must only parse: this text took ~0.8 s to evaluate before. */
    @Test fun normalizingALongExpressionIsFast() {
        val text = "sin(-1e-99999)".repeat(70)
        assertNotNull(paste(text))
        repeat(5) { paste(text) }
        val bestMillis = (1..5).minOf {
            val start = System.nanoTime()
            paste(text)
            (System.nanoTime() - start) / 1e6
        }
        assertTrue("normalizePasted took $bestMillis ms", bestMillis < 20.0)
    }
}
