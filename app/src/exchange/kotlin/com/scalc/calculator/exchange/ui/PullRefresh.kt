package com.scalc.calculator.exchange.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Geometry and timing of the pull-to-refresh gesture. */
internal object PullRefreshDefaults {
    /** Pull distance (after resistance) that triggers a refresh on release. */
    val Threshold: Dp = 64.dp

    /** Maximum pull distance. */
    val MaxDistance: Dp = 110.dp

    /** Fraction of the finger movement that becomes pull distance. */
    const val RESISTANCE = 0.5f

    /** Duration of the settle animation after release or when a refresh ends. */
    const val SETTLE_MILLIS = 180
}

/**
 * State of the custom pull-to-refresh gesture (no Material component involved).
 *
 * Install [nestedScrollConnection] on an ancestor of a vertical scroll container:
 * - Downward drags the container cannot consume (it is at its top, [canStartPull]) become pull
 *   [distance], damped by [PullRefreshDefaults.RESISTANCE] and capped at [maxDistancePx].
 * - Upward drags while pulled shrink the pull before the container scrolls.
 * - On release, a pull of at least [thresholdPx] calls [onRefresh] and parks the indicator at the
 *   threshold ([isRefreshing]) until [endRefresh]; a shorter pull settles back to zero.
 *
 * Only direct user input is considered, so flings reaching the top never start a pull, and taps
 * are unaffected because they never produce scroll deltas.
 */
@Stable
internal class PullRefreshState(
    private val scope: CoroutineScope,
    val thresholdPx: Float,
    private val maxDistancePx: Float,
    private val canStartPull: () -> Boolean,
    private val onRefresh: () -> Unit,
) {
    /** Current pull distance in px (0 = hidden). */
    var distance by mutableFloatStateOf(0f)
        private set

    /** A refresh triggered by this gesture is running and the indicator is parked. */
    var isRefreshing by mutableStateOf(false)
        private set

    /** Pull distance relative to the threshold: 1 means "release to refresh". */
    val progress: Float get() = if (thresholdPx > 0f) distance / thresholdPx else 0f

    private var settleJob: Job? = null

    /** Hides the parked indicator once the refresh has finished (successfully or not). */
    fun endRefresh() {
        if (!isRefreshing) return
        isRefreshing = false
        settleTo(0f)
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || isRefreshing) return Offset.Zero
            if (available.y >= 0f || distance <= 0f) return Offset.Zero
            // Finger moves up while pulled: shrink the pull first, at the same damped ratio.
            val pullDelta = maxOf(available.y * PullRefreshDefaults.RESISTANCE, -distance)
            dragTo(distance + pullDelta)
            return Offset(0f, pullDelta / PullRefreshDefaults.RESISTANCE)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || isRefreshing) return Offset.Zero
            if (available.y <= 0f || !canStartPull()) return Offset.Zero
            dragTo(distance + available.y * PullRefreshDefaults.RESISTANCE)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (isRefreshing || distance <= 0f) return Velocity.Zero
            if (distance >= thresholdPx) {
                isRefreshing = true
                settleTo(thresholdPx)
                onRefresh()
            } else {
                settleTo(0f)
            }
            // The release belonged to the pull: do not let the content fling.
            return available
        }
    }

    private fun dragTo(value: Float) {
        settleJob?.cancel()
        distance = value.coerceIn(0f, maxDistancePx)
    }

    private fun settleTo(target: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(
                initialValue = distance,
                targetValue = target,
                animationSpec = tween(PullRefreshDefaults.SETTLE_MILLIS),
            ) { value, _ -> distance = value }
        }
    }
}

/**
 * Remembers a [PullRefreshState]. The latest [canStartPull] and [onRefresh] are always used, so
 * callers may pass fresh lambdas on every recomposition.
 */
@Composable
internal fun rememberPullRefreshState(
    canStartPull: () -> Boolean,
    onRefresh: () -> Unit,
    threshold: Dp = PullRefreshDefaults.Threshold,
    maxDistance: Dp = PullRefreshDefaults.MaxDistance,
): PullRefreshState {
    val scope = rememberCoroutineScope()
    val currentCanStartPull by rememberUpdatedState(canStartPull)
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val density = LocalDensity.current
    val thresholdPx = with(density) { threshold.toPx() }
    val maxDistancePx = with(density) { maxDistance.toPx() }
    return remember(scope, thresholdPx, maxDistancePx) {
        PullRefreshState(
            scope = scope,
            thresholdPx = thresholdPx,
            maxDistancePx = maxDistancePx,
            canStartPull = { currentCanStartPull() },
            onRefresh = { currentOnRefresh() },
        )
    }
}
