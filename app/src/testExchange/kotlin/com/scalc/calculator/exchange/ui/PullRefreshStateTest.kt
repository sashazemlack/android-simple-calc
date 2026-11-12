package com.scalc.calculator.exchange.ui

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gesture logic of the custom pull-to-refresh, driven through its nested-scroll connection. */
@OptIn(ExperimentalCoroutinesApi::class)
class PullRefreshStateTest {

    private val threshold = 64f
    private val max = 110f
    private var atTop = true
    private var refreshes = 0

    /** 60 Hz frame clock on virtual time, so settle animations run under the test scheduler. */
    private class TestFrameClock : MonotonicFrameClock {
        private var frameTimeNanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(FRAME_MILLIS)
            frameTimeNanos += FRAME_MILLIS * 1_000_000L
            return onFrame(frameTimeNanos)
        }
    }

    private fun TestScope.newState() = PullRefreshState(
        scope = CoroutineScope(backgroundScope.coroutineContext + TestFrameClock()),
        thresholdPx = threshold,
        maxDistancePx = max,
        canStartPull = { atTop },
        onRefresh = { refreshes++ },
    )

    private fun PullRefreshState.drag(dy: Float, source: NestedScrollSource = NestedScrollSource.UserInput): Float {
        val connection = nestedScrollConnection
        val pre = connection.onPreScroll(Offset(0f, dy), source)
        // The scroll container is at its top and cannot scroll further down; upward it would consume.
        val left = dy - pre.y
        val post = connection.onPostScroll(Offset.Zero, Offset(0f, left), source)
        return pre.y + post.y
    }

    private suspend fun PullRefreshState.release(): Velocity = nestedScrollConnection.onPreFling(Velocity.Zero)

    private fun TestScope.settle() {
        advanceTimeBy(PullRefreshDefaults.SETTLE_MILLIS + 10L * FRAME_MILLIS)
        runCurrent()
    }

    @Test
    fun pullingDownAtTheTopAccumulatesWithResistanceUpToTheMaximum() = runTest {
        val state = newState()
        assertEquals(100f, state.drag(100f), 0f)
        assertEquals(50f, state.distance, 0f)

        state.drag(200f)
        assertEquals(max, state.distance, 0f)
    }

    @Test
    fun noPullUnlessScrolledToTheTop() = runTest {
        atTop = false
        val state = newState()
        assertEquals(0f, state.drag(100f), 0f)
        assertEquals(0f, state.distance, 0f)
    }

    @Test
    fun onlyDirectUserInputPulls() = runTest {
        val state = newState()
        state.drag(100f, NestedScrollSource.SideEffect)
        assertEquals(0f, state.distance, 0f)
    }

    @Test
    fun movingUpShrinksThePullBeforeTheContentScrolls() = runTest {
        val state = newState()
        state.drag(100f)
        val consumed = state.nestedScrollConnection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        assertEquals(-40f, consumed.y, 0f)
        assertEquals(30f, state.distance, 0f)

        // More than the remaining pull: only the part needed to close it is consumed.
        val rest = state.nestedScrollConnection.onPreScroll(Offset(0f, -200f), NestedScrollSource.UserInput)
        assertEquals(-60f, rest.y, 0f)
        assertEquals(0f, state.distance, 0f)
    }

    @Test
    fun shortPullSettlesBackWithoutRefreshing() = runTest {
        val state = newState()
        state.drag(100f)
        assertEquals(Velocity.Zero, state.release())
        settle()

        assertEquals(0, refreshes)
        assertFalse(state.isRefreshing)
        assertEquals(0f, state.distance, 0f)
    }

    @Test
    fun pullBeyondTheThresholdRefreshesOnceAndParksUntilEnded() = runTest {
        val state = newState()
        state.drag(200f)
        assertTrue(state.progress >= 1f)
        state.release()
        settle()

        assertEquals(1, refreshes)
        assertTrue(state.isRefreshing)
        assertEquals(threshold, state.distance, 0f)

        // While refreshing, further gestures neither move the indicator nor trigger again.
        state.drag(100f)
        state.drag(-100f)
        state.release()
        assertEquals(threshold, state.distance, 0f)
        assertEquals(1, refreshes)

        state.endRefresh()
        settle()
        assertFalse(state.isRefreshing)
        assertEquals(0f, state.distance, 0f)
    }

    @Test
    fun releaseWithoutPullIsIgnored() = runTest {
        val state = newState()
        val velocity = Velocity(0f, 500f)
        assertEquals(Velocity.Zero, state.nestedScrollConnection.onPreFling(velocity))
        assertEquals(0, refreshes)
    }

    private companion object {
        const val FRAME_MILLIS = 16L
    }
}
