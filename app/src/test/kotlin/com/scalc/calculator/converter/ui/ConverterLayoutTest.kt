package com.scalc.calculator.converter.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConverterLayoutTest {

    @Test
    fun landscapePutsTheKeypadBesideTheFields() {
        assertTrue(ConverterLayout.sideBySide(765f, 306f))
        assertTrue("tablet landscape", ConverterLayout.sideBySide(1280f, 752f))
    }

    @Test
    fun portraitStacksFieldsAboveTheKeypad() {
        assertFalse(ConverterLayout.sideBySide(392f, 700f))
        assertFalse(ConverterLayout.sideBySide(360f, 560f))
    }
}
