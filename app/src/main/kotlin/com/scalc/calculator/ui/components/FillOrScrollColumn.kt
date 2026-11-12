package com.scalc.calculator.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.max

/**
 * Vertical layout of a wrap-content [top] section and a [bottom] section (usually a keypad) that
 * fills the remaining viewport height. When the viewport is too small (landscape, split screen,
 * large font scale) the bottom section keeps [minBottomHeight] and the whole column scrolls.
 *
 * Because the column is a real vertical scroll container, pull-to-refresh style nested-scroll
 * connections placed on an ancestor receive overscroll at the top position.
 */
@Composable
fun FillOrScrollColumn(
    minBottomHeight: Dp,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val viewport = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        Layout(
            contents = listOf(top, bottom),
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        ) { (topMeasurables, bottomMeasurables), c ->
            val width = c.maxWidth
            val topPlaceables = topMeasurables.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
            val topHeight = topPlaceables.sumOf { it.height }
            val bottomHeight = max(viewport - topHeight, minBottomHeight.roundToPx())
            val bottomPlaceables = bottomMeasurables.map { it.measure(Constraints.fixed(width, bottomHeight)) }
            layout(width, topHeight + bottomHeight) {
                var y = 0
                topPlaceables.forEach {
                    it.place(0, y)
                    y += it.height
                }
                bottomPlaceables.forEach { it.place(0, topHeight) }
            }
        }
    }
}
