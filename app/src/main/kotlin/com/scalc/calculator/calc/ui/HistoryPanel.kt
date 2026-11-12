package com.scalc.calculator.calc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scalc.calculator.R
import com.scalc.calculator.calc.history.HistoryEntry
import com.scalc.calculator.common.NumberFormatting
import com.scalc.calculator.ui.components.FlatButton
import com.scalc.calculator.ui.components.FlatButtonStyle
import com.scalc.calculator.ui.components.FluentText
import com.scalc.calculator.ui.components.Glyph
import com.scalc.calculator.ui.components.GlyphButton
import com.scalc.calculator.ui.components.GlyphIcon
import com.scalc.calculator.ui.components.HorizontalSeparator
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TIMESTAMP_PATTERN = "d MMM yyyy, HH:mm"

/**
 * History panel shown in place of the keypads: header with title, entry count, Clear and a
 * button back to the keypad; the calculations newest first. Tapping an entry restores it.
 * Expressions and results are laid out left to right in every locale (see [CalculatorDisplay]).
 */
@Composable
internal fun HistoryPanel(
    entries: List<HistoryEntry>,
    onRestore: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(FluentTheme.colors.surface)) {
        HistoryHeader(count = entries.size, onClear = onClear, onClose = onClose)
        HorizontalSeparator()
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(FluentDimens.gutter), contentAlignment = Alignment.Center) {
                FluentText(
                    text = stringResource(R.string.calc_history_empty),
                    style = FluentTheme.typography.body,
                    color = FluentTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            val formatter = remember { SimpleDateFormat(TIMESTAMP_PATTERN, Locale.getDefault()) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries) { entry ->
                    HistoryRow(entry, timestamp = formatter.format(Date(entry.timestamp)), onClick = { onRestore(entry) })
                    HorizontalSeparator()
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(count: Int, onClear: () -> Unit, onClose: () -> Unit) {
    val colors = FluentTheme.colors
    val typography = FluentTheme.typography
    val clearDescription = stringResource(R.string.calc_history_clear_description)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = FluentDimens.gutter, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FluentText(
            text = stringResource(R.string.calc_history_title),
            modifier = Modifier.semantics { heading() },
            style = typography.subtitle,
            maxLines = 1,
        )
        FluentText(
            text = pluralStringResource(R.plurals.calc_history_count, count, count),
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            style = typography.caption,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val enabled = count > 0
        val tint = if (enabled) colors.textPrimary else colors.textDisabled
        FlatButton(
            onClick = onClear,
            enabled = enabled,
            style = FlatButtonStyle.Subtle,
            contentPadding = 10.dp,
            modifier = Modifier.semantics { contentDescription = clearDescription },
        ) {
            GlyphIcon(Glyph.Delete, contentDescription = null, tint = tint, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            FluentText(stringResource(R.string.calc_history_clear), style = typography.body, color = tint, maxLines = 1)
        }
        GlyphButton(
            glyph = Glyph.ChevronDown,
            contentDescription = stringResource(R.string.calc_history_back),
            onClick = onClose,
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, timestamp: String, onClick: () -> Unit) {
    val colors = FluentTheme.colors
    val typography = FluentTheme.typography
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.calc_history_restore), role = Role.Button, onClick = onClick)
            .padding(horizontal = FluentDimens.gutter, vertical = 10.dp),
        horizontalAlignment = Alignment.End,
    ) {
        FluentText(
            text = NumberFormatting.groupDigits(entry.expression),
            style = typography.body.copy(textDirection = TextDirection.Ltr),
            color = colors.textSecondary,
            textAlign = TextAlign.End,
        )
        FluentText(
            text = "= " + NumberFormatting.groupDigits(entry.result),
            modifier = Modifier.padding(top = 2.dp),
            style = typography.title.copy(textDirection = TextDirection.Ltr),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FluentText(
            text = timestamp,
            modifier = Modifier.padding(top = 4.dp),
            style = typography.caption,
            color = colors.textSecondary,
            maxLines = 1,
        )
    }
}
