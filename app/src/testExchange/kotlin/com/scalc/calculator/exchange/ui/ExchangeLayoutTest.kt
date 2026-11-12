package com.scalc.calculator.exchange.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Viewport sizes are the space below the tab strip (45 dp) and the system bars. The actual fit is
 * checked on a device; these tests pin which arrangement each common size gets.
 */
class ExchangeLayoutTest {

    @Test
    fun smallPortraitPhonesUseTheCompactLayout() {
        // 360 x 640 dp phone with status bar and 3-button navigation bar.
        assertEquals(ExchangeLayoutMode.Compact, ExchangeLayout.mode(360f, 523f))
        // Same phone with hardware keys (no navigation bar).
        assertEquals(ExchangeLayoutMode.Compact, ExchangeLayout.mode(360f, 571f))
        // A two-line stale status still has to fit: the regular layout needs about 615 dp.
        assertEquals(ExchangeLayoutMode.Compact, ExchangeLayout.mode(411f, 619.9f))
    }

    @Test
    fun tallPortraitPhonesKeepTheRegularLayout() {
        assertEquals(ExchangeLayoutMode.Regular, ExchangeLayout.mode(411f, 683f))
        assertEquals(ExchangeLayoutMode.Regular, ExchangeLayout.mode(411f, 774f))
        assertEquals(ExchangeLayoutMode.Regular, ExchangeLayout.mode(360f, ExchangeLayout.REGULAR_MIN_HEIGHT_DP))
    }

    @Test
    fun landscapeUsesTheSideBySideLayout() {
        assertEquals(ExchangeLayoutMode.SideBySide, ExchangeLayout.mode(640f, 291f))
        assertEquals(ExchangeLayoutMode.SideBySide, ExchangeLayout.mode(915f, 340f))
        // Large landscape tablets too: the keypad never pushes the amounts off-screen.
        assertEquals(ExchangeLayoutMode.SideBySide, ExchangeLayout.mode(1280f, 700f))
    }

    @Test
    fun squareViewportStaysStacked() {
        assertEquals(ExchangeLayoutMode.Compact, ExchangeLayout.mode(500f, 500f))
    }
}
