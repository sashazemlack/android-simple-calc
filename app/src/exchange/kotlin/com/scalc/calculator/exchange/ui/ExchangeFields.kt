package com.scalc.calculator.exchange.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scalc.calculator.R
import com.scalc.calculator.converter.ui.AutoShrinkText
import com.scalc.calculator.exchange.model.Currency
import com.scalc.calculator.ui.components.FluentText
import com.scalc.calculator.ui.components.Glyph
import com.scalc.calculator.ui.components.GlyphButton
import com.scalc.calculator.ui.components.HorizontalSeparator
import com.scalc.calculator.ui.components.SelectorField
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme

/** Opacity of a currency selector that cannot be used because no rates are available. */
private const val DISABLED_ALPHA = 0.5f

/** Width of a currency selector placed beside its amount in the compact layout. */
private val CompactSelectorWidth = 132.dp

/** Gap between a compact selector and its amount. */
private val CompactSelectorGap = 8.dp

/** Gap between the caption and the bordered box of a [SelectorField]. */
private val SelectorLabelGap = 4.dp

/**
 * Upper part of the Exchange screen: source currency and typed amount, swap control, destination
 * currency and converted value, followed by the unit rates and the data attribution.
 *
 * With [compact] each selector sits beside its amount instead of above it and both unit rates
 * share one line, which saves about 100 dp of height for short or landscape viewports.
 */
@Composable
internal fun ExchangeFields(
    state: ExchangeUiState,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = FluentTheme.colors
    val amountDescription = stringResource(R.string.exch_amount_description, state.amountText, state.from.name)
    val resultDescription = if (state.conversionAvailable) {
        stringResource(R.string.exch_result_description, state.convertedText, state.to.name)
    } else {
        stringResource(R.string.exch_result_unavailable)
    }
    val swapDescription = stringResource(R.string.exch_swap_description)

    Column(modifier.fillMaxWidth().padding(horizontal = FluentDimens.gutter, vertical = 8.dp)) {
        CurrencyAmount(
            label = stringResource(R.string.exch_from),
            currency = state.from,
            enabled = state.ratesAvailable,
            onPick = onPickFrom,
            amount = "${state.amountText} ${state.from.code}",
            amountColor = colors.textPrimary,
            amountDescription = amountDescription,
            compact = compact,
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HorizontalSeparator(Modifier.weight(1f))
            GlyphButton(
                glyph = Glyph.Swap,
                contentDescription = swapDescription,
                onClick = onSwap,
                label = stringResource(R.string.exch_swap),
                enabled = state.loaded,
                modifier = Modifier.semantics { contentDescription = swapDescription },
            )
            HorizontalSeparator(Modifier.weight(1f))
        }
        CurrencyAmount(
            label = stringResource(R.string.exch_to),
            currency = state.to,
            enabled = state.ratesAvailable,
            onPick = onPickTo,
            amount = "${state.convertedText} ${state.to.code}",
            amountColor = if (state.conversionAvailable) colors.accentText else colors.textSecondary,
            amountDescription = resultDescription,
            compact = compact,
        )
        RateInfo(state, compact, Modifier.padding(top = 6.dp))
    }
}

/**
 * A currency selector with its amount: underneath it (regular) or to its right, centered on the
 * selector's bordered box (compact).
 */
@Composable
private fun CurrencyAmount(
    label: String,
    currency: Currency,
    enabled: Boolean,
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
            CurrencySelector(label, currency, enabled, onPick, Modifier.width(CompactSelectorWidth))
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
        CurrencySelector(label, currency, enabled, onPick, Modifier.fillMaxWidth())
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

/** Currency selector showing the code with the currency name underneath. */
@Composable
private fun CurrencySelector(
    label: String,
    currency: Currency,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    SelectorField(
        label = label,
        value = currency.code,
        supporting = currency.name,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA)),
    )
}

/**
 * Unit rates in both directions and the data source; nothing while no rates are available. With
 * [compact] both unit rates share one (wrapping) line.
 */
@Composable
private fun RateInfo(state: ExchangeUiState, compact: Boolean, modifier: Modifier = Modifier) {
    val colors = FluentTheme.colors
    val caption = FluentTheme.typography.caption
    val unitRate = state.unitRateText
    val inverseRate = state.inverseRateText
    val rateLines = if (compact && unitRate != null && inverseRate != null) {
        listOf(stringResource(R.string.exch_rate_pair, unitRate, inverseRate))
    } else {
        listOfNotNull(unitRate, inverseRate)
    }
    val lines = rateLines + listOfNotNull(
        state.status.attribution?.let { stringResource(R.string.exch_attribution, it) },
    )
    if (lines.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        lines.forEach { line -> FluentText(line, style = caption, color = colors.textSecondary) }
    }
}
