package com.scalc.calculator.calc.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.scalc.calculator.R
import com.scalc.calculator.common.MINUS_SIGN
import com.scalc.calculator.ui.components.Glyph
import com.scalc.calculator.ui.components.GlyphKey
import com.scalc.calculator.ui.components.KeyKind
import com.scalc.calculator.ui.components.TextKey
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme

/**
 * One keypad key. [label] is drawn unless [glyph] is set; [description] names symbol keys for
 * accessibility (digits are read from their label). [longPress] is an optional second action.
 */
internal data class KeySpec(
    val key: CalcKey,
    val label: String,
    val kind: KeyKind,
    @param:StringRes val description: Int? = null,
    val glyph: Glyph? = null,
    val longPress: CalcKey? = null,
)

private fun digitKey(d: Char) = KeySpec(CalcKey.Digit(d), d.toString(), KeyKind.Number)

/** The standard 4 x 5 layout; the right column holds the operators and '='. */
private val StandardRows: List<List<KeySpec>> = listOf(
    listOf(
        KeySpec(CalcKey.Clear, "C", KeyKind.Function, R.string.calc_key_clear),
        KeySpec(CalcKey.Percent, "%", KeyKind.Function, R.string.calc_key_percent),
        KeySpec(
            CalcKey.Backspace, "⌫", KeyKind.Function, R.string.calc_key_backspace,
            glyph = Glyph.Backspace, longPress = CalcKey.Clear,
        ),
        KeySpec(CalcKey.Operator('÷'), "÷", KeyKind.Operator, R.string.calc_key_divide),
    ),
    listOf(digitKey('7'), digitKey('8'), digitKey('9'), KeySpec(CalcKey.Operator('×'), "×", KeyKind.Operator, R.string.calc_key_multiply)),
    listOf(digitKey('4'), digitKey('5'), digitKey('6'), KeySpec(CalcKey.Operator(MINUS_SIGN), "$MINUS_SIGN", KeyKind.Operator, R.string.calc_key_minus)),
    listOf(digitKey('1'), digitKey('2'), digitKey('3'), KeySpec(CalcKey.Operator('+'), "+", KeyKind.Operator, R.string.calc_key_plus)),
    listOf(
        KeySpec(CalcKey.ToggleSign, "+/$MINUS_SIGN", KeyKind.Function, R.string.calc_key_toggle_sign),
        digitKey('0'),
        KeySpec(CalcKey.Decimal, ".", KeyKind.Number, R.string.calc_key_decimal),
        KeySpec(CalcKey.Equals, "=", KeyKind.Accent, R.string.calc_key_equals),
    ),
)

/** Standard keypad (4 columns x 5 rows) filling its bounds; Backspace long-press clears. */
@Composable
internal fun StandardKeypad(onKey: (CalcKey) -> Unit, modifier: Modifier = Modifier) {
    val gap = FluentDimens.keyGap
    Column(
        modifier.background(FluentTheme.colors.keypadGap).padding(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        StandardRows.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { spec -> KeyCell(spec, onKey, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

/** Renders [spec] as a text or glyph key. */
@Composable
internal fun KeyCell(
    spec: KeySpec,
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = FluentTheme.typography.key,
    selected: Boolean = false,
) {
    val description = spec.description?.let { stringResource(it) }
    val onLongClick: (() -> Unit)? = spec.longPress?.let { second -> { onKey(second) } }
    if (spec.glyph != null) {
        GlyphKey(
            glyph = spec.glyph,
            contentDescription = description ?: spec.label,
            onClick = { onKey(spec.key) },
            modifier = modifier,
            kind = spec.kind,
            onLongClick = onLongClick,
        )
    } else {
        TextKey(
            text = spec.label,
            onClick = { onKey(spec.key) },
            modifier = modifier,
            kind = spec.kind,
            selected = selected,
            style = style,
            contentDescription = description,
            onLongClick = onLongClick,
        )
    }
}
