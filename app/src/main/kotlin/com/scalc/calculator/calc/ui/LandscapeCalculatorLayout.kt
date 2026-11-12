package com.scalc.calculator.calc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.scalc.calculator.ui.components.HorizontalSeparator
import com.scalc.calculator.ui.components.VerticalSeparator
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme

/** The display band never grows beyond this; any further height goes to the keys. */
private val LandscapeBandMax = 120.dp

/**
 * Compact landscape calculator (phones turned sideways): one band with the History / Paste / Copy
 * buttons on the left and the display on the right, and below it the keypad area, where the
 * scientific keys stay visible beside the standard keypad (or the history panel replaces both).
 * Merging the action row into the display band leaves the five key rows enough height.
 */
@Composable
internal fun LandscapeCalculatorLayout(
    dimensions: CalculatorDimensions,
    display: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    keypadArea: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val viewport = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        val spec = remember(viewport, dimensions, density) {
            with(density) {
                LandscapeLayoutSpec(viewport, dimensions, FluentDimens.hairline.roundToPx(), LandscapeBandMax.roundToPx())
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { spec.band.toDp() })
                    .background(FluentTheme.colors.background),
            ) {
                actions()
                VerticalSeparator()
                Box(Modifier.weight(1f).fillMaxHeight()) { display() }
            }
            HorizontalSeparator()
            Box(Modifier.fillMaxWidth().height(with(density) { spec.keypad.toDp() })) { keypadArea() }
        }
    }
}

/** Scientific keys (3 x 5) on the left, the standard keypad (4 x 5) on the right, rows aligned. */
@Composable
internal fun LandscapeKeypads(state: CalculatorUiState, onKey: (CalcKey) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize()) {
        ScientificKeypad(
            inverse = state.inverse,
            angleMode = state.angleMode,
            onKey = onKey,
            landscape = true,
            modifier = Modifier.weight(CalculatorLandscape.SCIENTIFIC_COLUMNS.toFloat()).fillMaxHeight(),
        )
        StandardKeypad(
            onKey = onKey,
            modifier = Modifier.weight(CalculatorLandscape.STANDARD_COLUMNS.toFloat()).fillMaxHeight(),
        )
    }
}
