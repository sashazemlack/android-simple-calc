package com.scalc.calculator.calc.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Space distribution at density 1 (1 px = 1 dp). */
class CalculatorLayoutSpecTest {

    private val dims = CalculatorDimensions(
        actionRow = 46,
        handle = 22,
        gap = 2,
        rowMin = 34,
        rowComfort = 44,
        sciRowMax = 44,
        displayNatural = 132,
        displayMin = 65,
    )

    private fun standardRow(spec: CalculatorLayoutSpec, fraction: Float): Float {
        val split = spec.split(fraction)
        val rows = split.keypadArea - dims.handle - spec.scientificHeight(fraction) - 6 * dims.gap
        return rows / 5f
    }

    @Test
    fun tallPhoneKeepsTheNaturalDisplayAndFillsTheRestWithKeys() {
        val spec = CalculatorLayoutSpec(viewportHeight = 680, dims = dims)
        assertEquals(3 * 44 + 6, spec.scientificFull)
        for (fraction in listOf(0f, 0.5f, 1f)) {
            val split = spec.split(fraction)
            assertEquals(132, split.display)
            assertEquals(680, spec.contentHeight(fraction))
        }
        assertTrue(standardRow(spec, 0f) > 90f)
        assertTrue(standardRow(spec, 1f) > 60f)
    }

    @Test
    fun displayShrinksBeforeTheKeys() {
        val spec = CalculatorLayoutSpec(viewportHeight = 523, dims = dims)
        val collapsed = spec.split(0f)
        val expanded = spec.split(1f)
        assertEquals(132, collapsed.display)
        assertTrue("display gives way first", expanded.display < collapsed.display)
        assertTrue(expanded.display >= dims.displayMin)
        assertEquals(44f, standardRow(spec, 1f), 0.01f)
        assertEquals(523, spec.contentHeight(1f))
    }

    @Test
    fun landscapeShrinksRowsToTheMinimumAndScrollsOnlyWhenNecessary() {
        val spec = CalculatorLayoutSpec(viewportHeight = 323, dims = dims)
        assertEquals("scientific rows shrink to the minimum", 3 * 34 + 6, spec.scientificFull)

        val collapsed = spec.split(0f)
        assertEquals(dims.displayMin, collapsed.display)
        assertEquals(323, spec.contentHeight(0f))
        val row = standardRow(spec, 0f)
        assertTrue("row $row", row >= 34f && row < 44f)

        val expanded = spec.split(1f)
        assertEquals(dims.displayMin, expanded.display)
        assertEquals(34f, standardRow(spec, 1f), 0.01f)
        assertTrue("content scrolls instead of overlapping", spec.contentHeight(1f) > 323)
    }

    @Test
    fun zeroViewportFallsBackToMinimumSizes() {
        val spec = CalculatorLayoutSpec(viewportHeight = 0, dims = dims)
        val split = spec.split(0f)
        assertEquals(dims.displayMin, split.display)
        assertEquals(dims.handle + 5 * 34 + 6 * 2, split.keypadArea)
    }

    @Test
    fun scientificHeightFollowsTheFraction() {
        val spec = CalculatorLayoutSpec(viewportHeight = 680, dims = dims)
        assertEquals(0, spec.scientificHeight(0f))
        assertEquals(69, spec.scientificHeight(0.5f))
        assertEquals(138, spec.scientificHeight(1f))
        assertEquals(138, spec.scientificHeight(1.5f))
        assertEquals(0, spec.scientificHeight(-1f))
    }
}
