package com.scalc.calculator.converter.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.scalc.calculator.ui.components.FluentText

/**
 * Single-line text that shrinks its font (in 1 sp steps) until it fits the available width.
 * When it still does not fit at [minFontSize] it wraps onto further lines instead of being
 * clipped, so long converted values are always fully readable.
 */
@Composable
internal fun AutoShrinkText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 20.sp,
    textAlign: TextAlign = TextAlign.End,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        // The measurer is re-created when density or font scale change, so it is a key too.
        val fitted = remember(measurer, text, style, maxWidth, minFontSize) {
            fitStyle(measurer, listOf(text), style, maxWidth, minFontSize)
        }
        FluentText(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = fitted,
            color = color,
            textAlign = textAlign,
        )
    }
}

/**
 * Largest style (from [style]'s size down to [minFontSize], in 1 sp steps, line height scaled
 * proportionally) in which every one of [texts] fits [maxWidthPx] on a single line. Returns the
 * [minFontSize] style when even that is too wide.
 */
internal fun fitStyle(
    measurer: TextMeasurer,
    texts: Collection<String>,
    style: TextStyle,
    maxWidthPx: Int,
    minFontSize: TextUnit,
): TextStyle {
    if (!style.fontSize.isSp || !minFontSize.isSp || maxWidthPx == Constraints.Infinity) return style
    val maxSize = style.fontSize.value
    val minSize = minFontSize.value.coerceAtMost(maxSize)
    val lineRatio = if (style.lineHeight.isSp) {
        style.lineHeight.value / style.fontSize.value
    } else {
        null
    }

    fun styleAt(size: Float): TextStyle =
        style.copy(fontSize = size.sp, lineHeight = lineRatio?.let { (size * it).sp } ?: style.lineHeight)

    fun fits(candidate: TextStyle): Boolean = texts.all { text ->
        measurer.measure(text, style = candidate, softWrap = false, maxLines = 1).size.width <= maxWidthPx
    }

    var size = maxSize
    while (size > minSize) {
        val candidate = styleAt(size)
        if (fits(candidate)) return candidate
        size -= 1f
    }
    return styleAt(minSize)
}
