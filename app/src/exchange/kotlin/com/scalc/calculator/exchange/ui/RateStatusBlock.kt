package com.scalc.calculator.exchange.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scalc.calculator.R
import com.scalc.calculator.exchange.model.RateStatus
import com.scalc.calculator.exchange.model.StatusKind
import com.scalc.calculator.ui.components.FluentText
import com.scalc.calculator.ui.components.HorizontalSeparator
import com.scalc.calculator.ui.theme.FluentColors
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme

private val MarkerSlot = 10.dp
private val MarkerSize = 8.dp

/** Colors of one status kind; a null [marker] means "show the spinner instead of a square". */
@Immutable
private data class StatusAppearance(val text: Color, val detail: Color, val marker: Color?)

private fun appearanceOf(kind: StatusKind, colors: FluentColors): StatusAppearance = when (kind) {
    StatusKind.Fresh -> StatusAppearance(colors.textSecondary, colors.textSecondary, colors.success)
    StatusKind.Cached -> StatusAppearance(colors.textSecondary, colors.textSecondary, colors.borderStrong)
    StatusKind.Stale -> StatusAppearance(colors.warning, colors.textSecondary, colors.warning)
    StatusKind.Unavailable -> StatusAppearance(colors.error, colors.error, colors.error)
    StatusKind.Refreshing -> StatusAppearance(colors.textSecondary, colors.textSecondary, null)
}

/**
 * Compact, full-width "last updated" block placed directly above the keypad. It always states the
 * condition of the rate data: a colored square marks fresh (success), cached (neutral), stale
 * (warning) and unavailable (error) rates, and a spinning arc replaces it while refreshing.
 *
 * Changes are announced politely to screen readers, and [onRefresh] is offered as a custom
 * accessibility action because the pull-down gesture is hard to perform with a screen reader.
 */
@Composable
internal fun RateStatusBlock(status: RateStatus, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FluentTheme.colors
    val typography = FluentTheme.typography
    val appearance = appearanceOf(status.kind, colors)
    val refreshLabel = stringResource(R.string.exch_refresh_action)

    Column(modifier.fillMaxWidth()) {
        HorizontalSeparator()
        Row(
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    customActions = listOf(
                        CustomAccessibilityAction(refreshLabel) {
                            onRefresh()
                            true
                        },
                    )
                }
                .padding(horizontal = FluentDimens.gutter, vertical = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Centered on the first text line (caption line height 16 sp).
            Box(Modifier.padding(top = 3.dp).size(MarkerSlot), contentAlignment = Alignment.Center) {
                val marker = appearance.marker
                if (marker == null) {
                    SpinnerArc(size = MarkerSlot, strokeWidth = 1.5.dp)
                } else {
                    Box(Modifier.size(MarkerSize).background(marker))
                }
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                FluentText(
                    status.text,
                    style = typography.caption.copy(fontWeight = FontWeight.Medium),
                    color = appearance.text,
                )
                status.detail?.let { detail ->
                    FluentText(detail, style = typography.caption, color = appearance.detail)
                }
            }
        }
    }
}
