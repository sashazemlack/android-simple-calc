package com.scalc.calculator.converter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scalc.calculator.R
import com.scalc.calculator.common.NumericKey
import com.scalc.calculator.converter.engine.MeasureUnit
import com.scalc.calculator.converter.engine.UnitCatalog
import com.scalc.calculator.ui.components.FillOrScrollColumn
import com.scalc.calculator.ui.components.FluentText
import com.scalc.calculator.ui.components.Glyph
import com.scalc.calculator.ui.components.GlyphButton
import com.scalc.calculator.ui.components.HorizontalSeparator
import com.scalc.calculator.ui.components.NumericKeypad
import com.scalc.calculator.ui.components.PickerDialog
import com.scalc.calculator.ui.components.PickerItem
import com.scalc.calculator.ui.components.SelectorField
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme

/**
 * Height of one keypad row when the keypad has to be compressed (small phones, landscape, large
 * fonts). Equal to the minimum touch target, so the 4-row keypad needs at least 176 dp; together
 * with the fields above it (about 280 dp at font scale 1.0) the whole screen fits a 360x640 dp
 * phone with 3-button navigation. Taller screens give the keypad all the remaining height.
 */
private val MinKeyRowHeight = FluentDimens.minTouch

/** Which unit selector opened the picker dialog. */
private enum class UnitSide { From, To }

/**
 * Conversion screen of one category: header with back button, source amount, swap control,
 * converted value, formula hint, and the numeric keypad filling the remaining height.
 */
@Composable
internal fun ConversionPane(
    conversion: UnitConversion,
    onBack: () -> Unit,
    onKey: (NumericKey) -> Unit,
    onSelectFrom: (String) -> Unit,
    onSelectTo: (String) -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerSide by rememberSaveable { mutableStateOf<UnitSide?>(null) }

    Column(modifier.fillMaxSize()) {
        PaneHeader(title = stringResource(conversion.category.titleRes), onBack = onBack)
        HorizontalSeparator()
        FillOrScrollColumn(
            minBottomHeight = MinKeyRowHeight * 4,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            top = {
                ConversionFields(
                    conversion = conversion,
                    onPickFrom = { pickerSide = UnitSide.From },
                    onPickTo = { pickerSide = UnitSide.To },
                    onSwap = onSwap,
                )
            },
            bottom = {
                NumericKeypad(
                    onKey = onKey,
                    bottomLeftKey = if (conversion.category.allowsNegative) NumericKey.ToggleSign else NumericKey.DoubleZero,
                )
            },
        )
    }

    pickerSide?.let { side ->
        val selected = if (side == UnitSide.From) conversion.from else conversion.to
        val items = UnitCatalog.units(conversion.category).map { unit ->
            PickerItem(key = unit.key, title = stringResource(unit.nameRes), subtitle = unit.symbol)
        }
        PickerDialog(
            title = stringResource(if (side == UnitSide.From) R.string.conv_pick_from else R.string.conv_pick_to),
            items = items,
            selectedKey = selected.key,
            onSelect = { item ->
                if (side == UnitSide.From) onSelectFrom(item.key) else onSelectTo(item.key)
                pickerSide = null
            },
            onDismiss = { pickerSide = null },
        )
    }
}

@Composable
private fun PaneHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 2.dp, end = FluentDimens.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(glyph = Glyph.Back, contentDescription = stringResource(R.string.conv_back), onClick = onBack)
        FluentText(
            title,
            modifier = Modifier.weight(1f).padding(start = 6.dp).semantics { heading() },
            style = FluentTheme.typography.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConversionFields(
    conversion: UnitConversion,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onSwap: () -> Unit,
) {
    val colors = FluentTheme.colors
    val typography = FluentTheme.typography
    val fromName = stringResource(conversion.from.nameRes)
    val toName = stringResource(conversion.to.nameRes)
    val amountText = conversion.amount.display
    val amountDescription = stringResource(R.string.conv_amount_description, amountText, fromName)
    val resultDescription = stringResource(R.string.conv_result_description, conversion.resultText, toName)
    val swapDescription = stringResource(R.string.conv_swap_description)

    Column(Modifier.fillMaxWidth().padding(horizontal = FluentDimens.gutter, vertical = 4.dp)) {
        UnitSelector(stringResource(R.string.conv_from), fromName, conversion.from, onPickFrom)
        AutoShrinkText(
            text = "$amountText ${conversion.from.symbol}",
            style = typography.displayMedium,
            color = colors.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .semantics(mergeDescendants = true) { contentDescription = amountDescription },
        )
        // The 44 dp swap button already separates the two halves; no extra vertical padding.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HorizontalSeparator(Modifier.weight(1f))
            GlyphButton(
                glyph = Glyph.Swap,
                contentDescription = swapDescription,
                onClick = onSwap,
                label = stringResource(R.string.conv_swap),
                modifier = Modifier.semantics { contentDescription = swapDescription },
            )
            HorizontalSeparator(Modifier.weight(1f))
        }
        UnitSelector(stringResource(R.string.conv_to), toName, conversion.to, onPickTo)
        AutoShrinkText(
            text = "${conversion.resultText} ${conversion.to.symbol}",
            style = typography.displayMedium,
            color = colors.accentText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .semantics(mergeDescendants = true) { contentDescription = resultDescription },
        )
        FluentText(
            conversion.formula,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            style = typography.caption,
            color = colors.textSecondary,
        )
        if (conversion.isBelowAbsoluteZero) {
            FluentText(
                stringResource(R.string.conv_below_absolute_zero),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                style = typography.caption,
                color = colors.warning,
            )
        }
    }
}

@Composable
private fun UnitSelector(label: String, unitName: String, unit: MeasureUnit, onClick: () -> Unit) {
    SelectorField(
        label = label,
        value = unitName,
        supporting = unit.symbol,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}
