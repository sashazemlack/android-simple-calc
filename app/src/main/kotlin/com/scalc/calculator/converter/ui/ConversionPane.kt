package com.scalc.calculator.converter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
import com.scalc.calculator.ui.components.VerticalSeparator
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

/** Side by side, the fields pane is a little wider than the keypad pane (as on the Exchange tab). */
private const val FIELDS_PANE_WEIGHT = 1.2f

/** Width of a unit selector when its amount sits beside it (side-by-side layout). */
private val CompactSelectorWidth = 168.dp

/** Space between a compact selector and its amount. */
private val CompactSelectorGap = 8.dp

/** Space between a selector's caption and its bordered box (see SelectorField). */
private val SelectorLabelGap = 4.dp

/** Arrangement of the conversion screen for the space it gets. */
internal object ConverterLayout {
    /**
     * Landscape (wider than tall): fields on the left, keypad on the right, like the Exchange tab.
     * Portrait: fields above the keypad.
     */
    fun sideBySide(widthDp: Float, heightDp: Float): Boolean = widthDp > heightDp
}

/**
 * Conversion screen of one category: header with back button, source amount, swap control,
 * converted value, formula hint, and the numeric keypad filling the remaining height. In
 * landscape ([ConverterLayout.sideBySide]) the fields are on the left and the keypad on the right.
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

    BoxWithConstraints(modifier.fillMaxSize()) {
        val sideBySide = ConverterLayout.sideBySide(maxWidth.value, maxHeight.value)
        val fields: @Composable (compact: Boolean) -> Unit = { compact ->
            ConversionFields(
                conversion = conversion,
                onPickFrom = { pickerSide = UnitSide.From },
                onPickTo = { pickerSide = UnitSide.To },
                onSwap = onSwap,
                compact = compact,
            )
        }
        val keypad: @Composable () -> Unit = {
            NumericKeypad(
                onKey = onKey,
                bottomLeftKey = if (conversion.category.allowsNegative) NumericKey.ToggleSign else NumericKey.DoubleZero,
            )
        }
        Column(Modifier.fillMaxSize()) {
            PaneHeader(title = stringResource(conversion.category.titleRes), onBack = onBack)
            HorizontalSeparator()
            if (sideBySide) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier
                            .weight(FIELDS_PANE_WEIGHT)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        fields(true)
                    }
                    VerticalSeparator()
                    FillOrScrollColumn(
                        minBottomHeight = MinKeyRowHeight * 4,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        top = {},
                        bottom = keypad,
                    )
                }
            } else {
                FillOrScrollColumn(
                    minBottomHeight = MinKeyRowHeight * 4,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    top = { fields(false) },
                    bottom = keypad,
                )
            }
        }
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
    compact: Boolean = false,
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
        UnitAmount(
            label = stringResource(R.string.conv_from),
            unitName = fromName,
            unit = conversion.from,
            onPick = onPickFrom,
            amount = "$amountText ${conversion.from.symbol}",
            amountColor = colors.textPrimary,
            amountDescription = amountDescription,
            compact = compact,
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
        UnitAmount(
            label = stringResource(R.string.conv_to),
            unitName = toName,
            unit = conversion.to,
            onPick = onPickTo,
            amount = "${conversion.resultText} ${conversion.to.symbol}",
            amountColor = colors.accentText,
            amountDescription = resultDescription,
            compact = compact,
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

/**
 * A unit selector with its amount: underneath it (portrait) or to its right, centered on the
 * selector's bordered box ([compact], side-by-side layout).
 */
@Composable
private fun UnitAmount(
    label: String,
    unitName: String,
    unit: MeasureUnit,
    onPick: () -> Unit,
    amount: String,
    amountColor: Color,
    amountDescription: String,
    compact: Boolean,
) {
    val style = FluentTheme.typography.displayMedium
    val amountSemantics = Modifier.semantics(mergeDescendants = true) { contentDescription = amountDescription }
    if (compact) {
        // Shifts the amount below the selector's caption so both center on the bordered box.
        val labelHeight = with(LocalDensity.current) { FluentTheme.typography.caption.lineHeight.toDp() }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            UnitSelector(label, unitName, unit, onPick, Modifier.width(CompactSelectorWidth))
            Spacer(Modifier.width(CompactSelectorGap))
            AutoShrinkText(
                text = amount,
                style = style,
                color = amountColor,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = labelHeight + SelectorLabelGap)
                    .then(amountSemantics),
            )
        }
    } else {
        UnitSelector(label, unitName, unit, onPick, Modifier.fillMaxWidth())
        AutoShrinkText(
            text = amount,
            style = style,
            color = amountColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .then(amountSemantics),
        )
    }
}

@Composable
private fun UnitSelector(label: String, unitName: String, unit: MeasureUnit, onClick: () -> Unit, modifier: Modifier) {
    SelectorField(
        label = label,
        value = unitName,
        supporting = unit.symbol,
        onClick = onClick,
        modifier = modifier,
    )
}
