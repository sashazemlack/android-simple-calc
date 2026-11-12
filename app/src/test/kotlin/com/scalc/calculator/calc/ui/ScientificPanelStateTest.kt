package com.scalc.calculator.calc.ui

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expander drag and settle behaviour of [ScientificPanelState]. */
class ScientificPanelStateTest {

    /** 60 fps frames delivered immediately, so 180 ms animations finish within the test. */
    private class ImmediateFrameClock : MonotonicFrameClock {
        private var frameTimeNanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            frameTimeNanos += 16_666_667L
            return onFrame(frameTimeNanos)
        }
    }

    @Test
    fun releasedDragSnapsToTheNearerStateOrFollowsAFling() {
        val panel = ScientificPanelState(expanded = false)
        assertEquals(0f, panel.fraction)
        panel.dragBy(50f, fullHeightPx = 100f)
        assertEquals("ignored before the drag starts", 0f, panel.fraction)

        panel.startDrag()
        assertTrue(panel.isDragging)
        panel.dragBy(60f, fullHeightPx = 120f)
        assertEquals("follows the finger", 0.5f, panel.fraction, 0.0001f)
        assertTrue("50 % expands", panel.releaseTarget(velocityPxPerSecond = 0f, velocityThresholdPx = 400f))
        panel.dragBy(-12f, fullHeightPx = 120f)
        assertFalse("below 50 % collapses", panel.releaseTarget(velocityPxPerSecond = 0f, velocityThresholdPx = 400f))
        assertTrue("downward fling expands", panel.releaseTarget(velocityPxPerSecond = 900f, velocityThresholdPx = 400f))
        panel.dragBy(1000f, fullHeightPx = 120f)
        assertEquals("clamped", 1f, panel.fraction, 0.0001f)
        assertFalse("upward fling collapses", panel.releaseTarget(velocityPxPerSecond = -900f, velocityThresholdPx = 400f))
    }

    @Test
    fun settleAnimatesToTheTargetAndEndsTheDrag() = runTest(ImmediateFrameClock()) {
        val panel = ScientificPanelState(expanded = false)
        panel.startDrag()
        panel.dragBy(80f, fullHeightPx = 100f)
        panel.settle(expanded = true)
        assertFalse(panel.isDragging)
        assertEquals(1f, panel.fraction, 0.0001f)

        panel.startDrag()
        panel.animateTo(expanded = false)
        assertEquals("ViewModel changes wait while the user drags", 1f, panel.fraction, 0.0001f)
        panel.dragBy(-100f, fullHeightPx = 100f)
        panel.settle(expanded = false)
        assertEquals(0f, panel.fraction, 0.0001f)

        panel.animateTo(expanded = true)
        assertEquals(1f, panel.fraction, 0.0001f)
    }

    @Test
    fun cancelledDragReturnsToTheSettledState() {
        val panel = ScientificPanelState(expanded = true)
        panel.startDrag()
        panel.dragBy(-50f, fullHeightPx = 100f)
        assertEquals(0.5f, panel.fraction, 0.0001f)
        panel.cancelDrag()
        assertFalse(panel.isDragging)
        assertEquals(1f, panel.fraction, 0.0001f)
    }
}
