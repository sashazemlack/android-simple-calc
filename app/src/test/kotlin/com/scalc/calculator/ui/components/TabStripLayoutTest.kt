package com.scalc.calculator.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabStripLayoutTest {

    /** Label widths at 15 sp Medium: "Calculator", "Exchange Rate", "Unit Converter". */
    private val exchangeLabels = listOf(68.dp, 96.6.dp, 93.dp)

    /** Width left for the tabs on a 360 dp phone next to the 40 dp Settings button. */
    private val narrow = 320.dp

    private fun assertDp(expected: Float, actual: Dp) = assertEquals(expected, actual.value, 0.01f)

    @Test
    fun paddingShrinksSoThatAllThreeExchangeTabsFitOn360Dp() {
        val padding = tabHorizontalPadding(narrow, exchangeLabels)
        assertDp((320f - 4f - 257.6f) / 6f, padding) // ≈ 9.73 dp instead of the fixed 12 dp
        val total = 4.dp + exchangeLabels.fold(0.dp) { a, w -> a + w } + padding * (2 * exchangeLabels.size)
        assertTrue("tabs need $total of $narrow", total <= narrow + 0.01.dp)
    }

    @Test
    fun paddingStaysAtTwelveDpWhenThereIsRoom() {
        assertDp(12f, tabHorizontalPadding(411.dp, exchangeLabels))
        assertDp(12f, tabHorizontalPadding(narrow, listOf(68.dp, 93.dp))) // Offline flavor
        assertDp(12f, tabHorizontalPadding(narrow, emptyList()))
    }

    @Test
    fun paddingStopsAtTheMinimumAndTheStripScrollsInstead() {
        val scaled = exchangeLabels.map { it * 1.3f } // font scale 1.3
        assertDp(6f, tabHorizontalPadding(narrow, scaled))
    }

    @Test
    fun labelBoundsFollowTheInsetAndPadding() {
        val bounds = tabLabelBounds(listOf(10.dp, 20.dp, 30.dp), padding = 5.dp, startInset = 4.dp)
        assertEquals(listOf(9.dp to 10.dp, 29.dp to 20.dp, 59.dp to 30.dp), bounds)
        assertEquals(emptyList<Pair<Dp, Dp>>(), tabLabelBounds(emptyList(), 12.dp))
    }

    @Test
    fun indicatorSnapsOnFirstPlacementAndAnimatesTabSwitches() {
        assertTrue(shouldSnapIndicator(0.dp, 90.dp))
        assertFalse(shouldSnapIndicator(90.dp, 120.dp))
        assertFalse(shouldSnapIndicator(0.dp, 0.dp))
        // Layout-only change (e.g. window resize) with the same selection: jump, do not slide.
        assertTrue(shouldSnapIndicator(90.dp, 120.dp, selectionChanged = false))
        assertFalse(shouldSnapIndicator(0.dp, 0.dp, selectionChanged = false))
    }

    @Test
    fun selectedTabIsScrolledFullyIntoView() {
        assertNull(tabScrollTarget(tabStart = 10, tabEnd = 100, scroll = 0, viewport = 300))
        assertEquals(50, tabScrollTarget(tabStart = 250, tabEnd = 350, scroll = 0, viewport = 300))
        assertEquals(20, tabScrollTarget(tabStart = 20, tabEnd = 120, scroll = 80, viewport = 300))
        // Wider than the viewport: show its start.
        assertEquals(400, tabScrollTarget(tabStart = 400, tabEnd = 800, scroll = 0, viewport = 300))
    }
}
