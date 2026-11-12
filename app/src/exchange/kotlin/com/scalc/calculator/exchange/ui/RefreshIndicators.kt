package com.scalc.calculator.exchange.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scalc.calculator.R
import com.scalc.calculator.ui.components.Glyph
import com.scalc.calculator.ui.components.GlyphIcon
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme

private val IndicatorSize = 32.dp
private val IndicatorContentSize = 18.dp

/** One full turn of [SpinnerArc]. */
private const val SPIN_MILLIS = 1_000

/** Length of the moving accent segment of [SpinnerArc]. */
private const val SPIN_SWEEP_DEGREES = 100f

/**
 * Small flat pull-to-refresh indicator: a 32 dp bordered square that slides down from the top
 * edge with the pull. While pulling it shows the refresh glyph rotated by the pull progress
 * (accent once releasing would refresh); while the refresh runs it shows a spinning arc.
 * Position, alpha and rotation are applied in the draw phase, so dragging does not recompose.
 */
@Composable
internal fun PullRefreshIndicator(state: PullRefreshState, modifier: Modifier = Modifier) {
    val visible by remember(state) { derivedStateOf { state.distance > 0f || state.isRefreshing } }
    if (!visible) return

    val colors = FluentTheme.colors
    val armed by remember(state) { derivedStateOf { state.progress >= 1f } }
    val description = stringResource(R.string.exch_refreshing_indicator)
    val shape = RoundedCornerShape(FluentDimens.cornerSmall)
    Box(
        modifier
            .graphicsLayer {
                val sizePx = IndicatorSize.toPx()
                translationY = state.distance - sizePx
                alpha = (state.distance / sizePx).coerceIn(0f, 1f)
            }
            .size(IndicatorSize)
            .background(colors.surface, shape)
            .border(FluentDimens.hairline, colors.border, shape)
            .then(if (state.isRefreshing) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (state.isRefreshing) {
            SpinnerArc(size = IndicatorContentSize)
        } else {
            GlyphIcon(
                glyph = Glyph.Refresh,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = state.progress * 360f },
                tint = if (armed) colors.accentText else colors.textSecondary,
                size = IndicatorContentSize,
            )
        }
    }
}

/**
 * Continuously rotating accent arc on a faint track (about one turn per second). Used by the
 * pull-to-refresh indicator and by the status block while rates are being refreshed. The angle is
 * read in the draw phase only.
 */
@Composable
internal fun SpinnerArc(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    strokeWidth: Dp = 2.dp,
    color: Color = FluentTheme.colors.accent,
    trackColor: Color = FluentTheme.colors.separator,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(SPIN_MILLIS, easing = LinearEasing)),
        label = "spinnerRotation",
    )
    Canvas(modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
        drawArc(
            color,
            startAngle = rotation.value - 90f,
            sweepAngle = SPIN_SWEEP_DEGREES,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(stroke),
        )
    }
}
