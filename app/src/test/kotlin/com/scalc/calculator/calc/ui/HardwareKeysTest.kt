package com.scalc.calculator.calc.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HardwareKeysTest {

    @Test
    fun keyboardCharactersMapToCalculatorKeys() {
        ('0'..'9').forEach { assertEquals(CalcKey.Digit(it), HardwareKeys.forCharacter(it)) }
        assertEquals(CalcKey.Decimal, HardwareKeys.forCharacter('.'))
        assertEquals(CalcKey.Decimal, HardwareKeys.forCharacter(','))
        assertEquals(CalcKey.Operator('+'), HardwareKeys.forCharacter('+'))
        assertEquals(CalcKey.Operator('−'), HardwareKeys.forCharacter('-'))
        assertEquals(CalcKey.Operator('×'), HardwareKeys.forCharacter('*'))
        assertEquals(CalcKey.Operator('÷'), HardwareKeys.forCharacter('/'))
        assertEquals(CalcKey.Operator('^'), HardwareKeys.forCharacter('^'))
        assertEquals(CalcKey.Percent, HardwareKeys.forCharacter('%'))
        assertEquals(CalcKey.Factorial, HardwareKeys.forCharacter('!'))
        assertEquals(CalcKey.OpenParenthesis, HardwareKeys.forCharacter('('))
        assertEquals(CalcKey.CloseParenthesis, HardwareKeys.forCharacter(')'))
        assertEquals(CalcKey.Equals, HardwareKeys.forCharacter('='))
    }

    @Test
    fun otherCharactersAreNotConsumed() {
        assertNull(HardwareKeys.forCharacter('a'))
        assertNull(HardwareKeys.forCharacter('x'))
        assertNull(HardwareKeys.forCharacter(' '))
        assertNull(HardwareKeys.forCharacter('\n'))
    }
}
