package com.scalc.calculator.calc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scalc.calculator.R
import com.scalc.calculator.ui.components.FluentText
import com.scalc.calculator.ui.components.Glyph
import com.scalc.calculator.ui.components.GlyphIcon
import com.scalc.calculator.ui.components.HorizontalSeparator
import com.scalc.calculator.ui.components.VerticalSeparator
import com.scalc.calculator.ui.components.flatClickable
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme

/**
 * Flat command row between display and keypads: History (with an accent underline while the
 * panel is open) on the left, Paste and Copy on the right, separated by 1 dp rules.
 */
@Composable
internal fun ActionRow(
    historyOpen: Boolean,
    onToggleHistory: () -> Unit,
    onPaste: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(FluentTheme.colors.background)) {
        HorizontalSeparator()
        Row(Modifier.fillMaxWidth().height(ActionRowHeight), verticalAlignment = Alignment.CenterVertically) {
            ActionButton(
                glyph = Glyph.History,
                label = stringResource(R.string.calc_history),
                onClick = onToggleHistory,
                selected = historyOpen,
            )
            VerticalSeparator()
            Spacer(Modifier.weight(1f))
            VerticalSeparator()
            ActionButton(glyph = Glyph.Paste, description = stringResource(R.string.calc_paste), onClick = onPaste)
            VerticalSeparator()
            ActionButton(glyph = Glyph.Copy, description = stringResource(R.string.calc_copy), onClick = onCopy)
        }
        HorizontalSeparator()
    }
}

/**
 * The same three commands without the surrounding rules, side by side at full height: used in
 * the compact landscape layout, where they share one band with the display.
 */
@Composable
internal fun ActionButtons(
    historyOpen: Boolean,
    onToggleHistory: () -> Unit,
    onPaste: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        ActionButton(
            glyph = Glyph.History,
            label = stringResource(R.string.calc_history),
            onClick = onToggleHistory,
            selected = historyOpen,
        )
        VerticalSeparator()
        ActionButton(glyph = Glyph.Paste, description = stringResource(R.string.calc_paste), onClick = onPaste)
        VerticalSeparator()
        ActionButton(glyph = Glyph.Copy, description = stringResource(R.string.calc_copy), onClick = onCopy)
    }
}

/**
 * One command: a glyph with an optional visible [label] (otherwise [description] names it for
 * accessibility). [selected] non-null makes it a toggle that shows a 2 dp accent underline.
 */
@Composable
private fun ActionButton(
    glyph: Glyph,
    onClick: () -> Unit,
    label: String? = null,
    description: String? = null,
    selected: Boolean? = null,
) {
    val colors = FluentTheme.colors
    val isSelected = selected == true
    val tint = if (isSelected) colors.accentText else colors.textPrimary
    Box(
        Modifier
            .fillMaxHeight()
            .widthIn(min = FluentDimens.minTouch + 4.dp)
            .flatClickable(onClick = onClick)
            .semantics {
                if (description != null) contentDescription = description
                if (selected != null) this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(glyph = glyph, contentDescription = null, tint = tint, size = 20.dp)
            if (label != null) {
                Spacer(Modifier.width(8.dp))
                FluentText(label, style = FluentTheme.typography.body, color = tint, maxLines = 1)
            }
        }
        if (isSelected) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(FluentDimens.tabIndicator)
                        .background(colors.accent),
                )
            }
        }
    }
}
