package com.scalc.calculator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scalc.calculator.R
import com.scalc.calculator.common.MINUS_SIGN
import com.scalc.calculator.common.NumericKey
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme

/**
 * Shared amount keypad used by the unit converter and the currency converter:
 *
 * ```
 * | 7 | 8 | 9 |    |
 * | 4 | 5 | 6 | C  |
 * | 1 | 2 | 3 |    |
 * | ± | 0 | . | ⌫  |
 * ```
 * The right column holds two double-height keys (Clear and Backspace).
 * [bottomLeftKey] is [NumericKey.ToggleSign] (default) or [NumericKey.DoubleZero].
 */
@Composable
fun NumericKeypad(
    onKey: (NumericKey) -> Unit,
    modifier: Modifier = Modifier,
    bottomLeftKey: NumericKey = NumericKey.ToggleSign,
) {
    val gap = FluentDimens.keyGap
    Row(
        modifier
            .fillMaxSize()
            .background(FluentTheme.colors.keypadGap)
            .padding(gap),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(gap)) {
            val rows = listOf(
                listOf(NumericKey.D7, NumericKey.D8, NumericKey.D9),
                listOf(NumericKey.D4, NumericKey.D5, NumericKey.D6),
                listOf(NumericKey.D1, NumericKey.D2, NumericKey.D3),
                listOf(bottomLeftKey, NumericKey.D0, NumericKey.Decimal),
            )
            rows.forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { key ->
                        val (label, description, kind) = when (key) {
                            NumericKey.ToggleSign -> Triple("+/$MINUS_SIGN", stringResource(R.string.keypad_toggle_sign), KeyKind.Function)
                            NumericKey.DoubleZero -> Triple("00", stringResource(R.string.keypad_double_zero), KeyKind.Number)
                            NumericKey.Decimal -> Triple(".", stringResource(R.string.keypad_decimal), KeyKind.Number)
                            else -> Triple(key.digit.toString(), null, KeyKind.Number)
                        }
                        TextKey(
                            text = label,
                            onClick = { onKey(key) },
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            kind = kind,
                            contentDescription = description,
                        )
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
            TextKey(
                text = "C",
                onClick = { onKey(NumericKey.Clear) },
                modifier = Modifier.weight(1f).fillMaxSize(),
                kind = KeyKind.Operator,
                contentDescription = stringResource(R.string.keypad_clear),
            )
            GlyphKey(
                glyph = Glyph.Backspace,
                contentDescription = stringResource(R.string.keypad_backspace),
                onClick = { onKey(NumericKey.Backspace) },
                onLongClick = { onKey(NumericKey.Clear) },
                modifier = Modifier.weight(1f).fillMaxSize(),
                kind = KeyKind.Operator,
            )
        }
    }
}
