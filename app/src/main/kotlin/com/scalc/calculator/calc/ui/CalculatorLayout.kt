package com.scalc.calculator.calc.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme
import kotlin.math.ceil

/** Height of the History / Paste / Copy row without its separators. */
internal val ActionRowHeight = 44.dp

/** Height of the scientific keypad expander strip. */
internal val HandleHeight = 22.dp

private val KeyRowMin = 34.dp
private val KeyRowComfort = 44.dp
private val ScientificRowMax = 44.dp

/** Pixel sizes for [CalculatorLayoutSpec], derived from the theme typography and the density. */
@Composable
internal fun rememberCalculatorDimensions(): CalculatorDimensions {
    val density = LocalDensity.current
    val typography = FluentTheme.typography
    return remember(density, typography) {
        with(density) {
            val caption = typography.caption.lineHeight.toPx()
            val expression = typography.displayLarge.lineHeight.toPx()
            val expressionMin = DisplayMetrics.expressionLineHeight(typography, DisplayMetrics.MinExpressionFontSize).toPx()
            val result = typography.displaySmall.lineHeight.toPx()
            val natural = (DisplayMetrics.Padding * 2 + DisplayMetrics.Breathing).toPx() + caption + expression + result
            val compact = (DisplayMetrics.CompactPadding * 2).toPx() + expressionMin + result
            CalculatorDimensions(
                actionRow = (ActionRowHeight + FluentDimens.hairline * 2).roundToPx(),
                handle = HandleHeight.roundToPx(),
                gap = FluentDimens.keyGap.roundToPx(),
                rowMin = KeyRowMin.roundToPx(),
                rowComfort = KeyRowComfort.roundToPx(),
                sciRowMax = ScientificRowMax.roundToPx(),
                displayNatural = ceil(natural).toInt(),
                displayMin = ceil(compact).toInt(),
            )
        }
    }
}

/**
 * Stacks display, action row and keypad area (keypads or history panel) and distributes the
 * height with [CalculatorLayoutSpec]. The scientific expansion fraction is read during
 * measurement only, so a running expand/collapse animation re-lays out without recomposing.
 * When even the minimum sizes exceed the viewport (landscape phones with the scientific keypad
 * open, large font scales) the whole screen scrolls instead of overlapping.
 */
@Composable
internal fun CalculatorLayout(
    dimensions: CalculatorDimensions,
    scientificFraction: () -> Float,
    display: @Composable () -> Unit,
    actionRow: @Composable () -> Unit,
    keypadArea: @Composable (CalculatorLayoutSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val viewport = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        val spec = remember(viewport, dimensions) { CalculatorLayoutSpec(viewport, dimensions) }
        val keypad: @Composable () -> Unit = { keypadArea(spec) }
        Layout(
            contents = listOf(display, actionRow, keypad),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) { (displayParts, actionParts, keypadParts), constraints ->
            val width = constraints.maxWidth
            val split = spec.split(scientificFraction())
            val displays = displayParts.map { it.measure(Constraints.fixed(width, split.display)) }
            val actions = actionParts.map { it.measure(Constraints.fixed(width, dimensions.actionRow)) }
            val keypads = keypadParts.map { it.measure(Constraints.fixed(width, split.keypadArea)) }
            layout(width, split.display + dimensions.actionRow + split.keypadArea) {
                displays.forEach { it.place(0, 0) }
                actions.forEach { it.place(0, split.display) }
                keypads.forEach { it.place(0, split.display + dimensions.actionRow) }
            }
        }
    }
}
