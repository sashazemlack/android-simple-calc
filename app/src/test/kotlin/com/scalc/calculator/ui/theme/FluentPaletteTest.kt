package com.scalc.calculator.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class FluentPaletteTest {

    private val palettes = listOf("light" to LightFluentColors, "dark" to DarkFluentColors)

    /** WCAG contrast ratio. */
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertContrast(name: String, a: Color, b: Color, minimum: Double) {
        val ratio = contrast(a, b)
        assertTrue("$name: %.3f:1 < %.2f:1".format(ratio, minimum), ratio >= minimum)
    }

    @Test
    fun keysStandOutFromTheKeypadGap() {
        // Keys are separated only by the gap color (no borders): every key fill needs a visible edge.
        for ((name, c) in palettes) {
            assertContrast("$name keyNumber/keypadGap", c.keyNumber, c.keypadGap, 1.12)
            assertContrast("$name keyFunction/keypadGap", c.keyFunction, c.keypadGap, 1.12)
            assertContrast("$name keyOperator/keypadGap", c.keyOperator, c.keypadGap, 1.12)
        }
    }

    @Test
    fun accentTextMeetsWcagAaOnItsBackgrounds() {
        for ((name, c) in palettes) {
            assertContrast("$name accentText/accentSubtle", c.accentText, c.accentSubtle, 4.5)
            assertContrast("$name accentText/background", c.accentText, c.background, 4.5)
            assertContrast("$name accentText/surface", c.accentText, c.surface, 4.5)
        }
    }
}
