package com.scalc.calculator.calc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scalc.calculator.R
import com.scalc.calculator.calc.engine.AngleMode
import com.scalc.calculator.common.NumberFormatting
import com.scalc.calculator.ui.components.FluentText
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme
import com.scalc.calculator.ui.theme.FluentTypography

/** Sizes of the display; shared with [rememberCalculatorDimensions] so the layout reserves enough room. */
internal object DisplayMetrics {
    val Padding = 6.dp
    val CompactPadding = 4.dp

    /** Extra room above the expression when the screen is tall enough. */
    val Breathing = 20.dp
    val MinExpressionFontSize = 24.sp
    const val EXPRESSION_FONT_STEP = 2f

    /** The live result shrinks from displaySmall down to this size before it is elided. */
    val MinResultFontSize = 14.sp
    const val RESULT_FONT_STEP = 2f

    /** Line height of the expression drawn at [fontSize] (same ratio as displayLarge). */
    fun expressionLineHeight(typography: FluentTypography, fontSize: TextUnit): TextUnit {
        val base = typography.displayLarge
        return (fontSize.value * base.lineHeight.value / base.fontSize.value).sp
    }

    /** Smallest display height that still shows the mode caption on its own line. */
    fun fullHeight(typography: FluentTypography, density: Density): Dp = with(density) {
        Padding * 2 +
            typography.caption.lineHeight.toDp() +
            expressionLineHeight(typography, MinExpressionFontSize).toDp() +
            typography.displaySmall.lineHeight.toDp()
    }
}

/**
 * Calculator display: angle-mode caption (top left), the expression right-aligned in a large light
 * font that shrinks for long input and then scrolls horizontally with its end kept visible, and the
 * live result (or an error in the error color) underneath. On short screens the caption moves to
 * the left end of the result line so nothing overlaps.
 *
 * Expressions and results are always laid out left to right ([TextDirection.Ltr]): they contain
 * no strong directional characters, so in an RTL locale the bidi algorithm would otherwise
 * reorder "125+25×2" visually.
 */
@Composable
internal fun CalculatorDisplay(
    expression: String,
    preview: String?,
    errorMessage: String?,
    angleMode: AngleMode,
    inverse: Boolean,
    modifier: Modifier = Modifier,
) {
    val typography = FluentTheme.typography
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize().background(FluentTheme.colors.background)) {
        val compact = maxHeight < DisplayMetrics.fullHeight(typography, density)
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = FluentDimens.gutter,
                    vertical = if (compact) DisplayMetrics.CompactPadding else DisplayMetrics.Padding,
                ),
        ) {
            if (!compact) ModeCaption(angleMode, inverse)
            ExpressionLine(expression, Modifier.weight(1f).fillMaxWidth())
            ResultLine(
                preview = preview,
                errorMessage = errorMessage,
                leading = if (compact) ({ ModeCaption(angleMode, inverse) }) else null,
            )
        }
    }
}

@Composable
private fun ModeCaption(angleMode: AngleMode, inverse: Boolean) {
    val colors = FluentTheme.colors
    val caption = FluentTheme.typography.caption
    Row(verticalAlignment = Alignment.CenterVertically) {
        FluentText(
            text = stringResource(if (angleMode == AngleMode.DEG) R.string.calc_mode_deg else R.string.calc_mode_rad),
            style = caption,
            color = colors.textSecondary,
            maxLines = 1,
        )
        if (inverse) {
            FluentText(
                text = stringResource(R.string.calc_mode_inverse),
                modifier = Modifier.padding(start = 8.dp),
                style = caption.copy(fontWeight = FontWeight.Bold),
                color = colors.accentText,
                maxLines = 1,
            )
        }
    }
}

/**
 * The expression with grouped digits (display only). The font steps down from displayLarge to
 * [DisplayMetrics.MinExpressionFontSize] until the text fits the width and height; longer text
 * scrolls horizontally. Reverse scrolling anchors the end, and every edit scrolls back to it.
 */
@Composable
private fun ExpressionLine(expression: String, modifier: Modifier) {
    val shown = if (expression.isEmpty()) "0" else NumberFormatting.groupDigits(expression)
    val description = stringResource(R.string.calc_expression_description, shown)
    val typography = FluentTheme.typography
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.BottomEnd) {
        val maxWidthPx = constraints.maxWidth
        val maxHeightPx = constraints.maxHeight
        val style = remember(shown, measurer, typography, density, maxWidthPx, maxHeightPx) {
            fitExpressionStyle(measurer, shown, typography, density, maxWidthPx, maxHeightPx)
        }
        val scrollState = rememberScrollState()
        LaunchedEffect(shown) { scrollState.scrollTo(0) }
        FluentText(
            text = shown,
            modifier = Modifier
                .horizontalScroll(scrollState, reverseScrolling = true)
                .semantics { contentDescription = description },
            style = style,
            color = FluentTheme.colors.textPrimary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Largest expression style (in [DisplayMetrics.EXPRESSION_FONT_STEP] steps) whose single line fits. */
private fun fitExpressionStyle(
    measurer: TextMeasurer,
    text: String,
    typography: FluentTypography,
    density: Density,
    maxWidthPx: Int,
    maxHeightPx: Int,
): TextStyle {
    val base = typography.displayLarge.copy(textDirection = TextDirection.Ltr)
    fun styleAt(size: Float) = base.copy(
        fontSize = size.sp,
        lineHeight = DisplayMetrics.expressionLineHeight(typography, size.sp),
    )
    val minSize = DisplayMetrics.MinExpressionFontSize.value
    var size = base.fontSize.value
    while (size >= minSize) {
        val style = styleAt(size)
        val fitsHeight = with(density) { style.lineHeight.toPx() } <= maxHeightPx
        if (fitsHeight) {
            val width = measurer.measure(text, style = style, softWrap = false, maxLines = 1).size.width
            if (width <= maxWidthPx) return style
        }
        size -= DisplayMetrics.EXPRESSION_FONT_STEP
    }
    return styleAt(minSize)
}

/**
 * Live result (grouped) or error message, right-aligned; [leading] holds the caption on short
 * screens. The font steps down from displaySmall to [DisplayMetrics.MinResultFontSize] until the
 * line fits (large font scales, compact mode); a result that still does not fit is elided in the
 * middle, so the exponent of scientific notation ("…E-300") always stays visible.
 */
@Composable
private fun ResultLine(preview: String?, errorMessage: String?, leading: (@Composable () -> Unit)?) {
    val colors = FluentTheme.colors
    val baseStyle = FluentTheme.typography.displaySmall.copy(textDirection = TextDirection.Ltr)
    val lineHeight = with(LocalDensity.current) { baseStyle.lineHeight.toDp() }
    val measurer = rememberTextMeasurer()
    Row(Modifier.fillMaxWidth().height(lineHeight), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        BoxWithConstraints(
            Modifier.weight(1f).padding(start = if (leading != null) 8.dp else 0.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val text = errorMessage ?: preview?.let(NumberFormatting::groupDigits)
            if (text != null) {
                val maxWidthPx = constraints.maxWidth
                val style = remember(text, measurer, baseStyle, maxWidthPx) {
                    fitResultStyle(measurer, text, baseStyle, maxWidthPx)
                }
                val description = if (errorMessage != null) text else stringResource(R.string.calc_result_description, text)
                FluentText(
                    text = text,
                    modifier = Modifier.semantics { contentDescription = description },
                    style = style,
                    color = if (errorMessage != null) colors.error else colors.textSecondary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = if (errorMessage != null) TextOverflow.Ellipsis else TextOverflow.MiddleEllipsis,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * Largest result style (in [DisplayMetrics.RESULT_FONT_STEP] steps, line height scaled along so it
 * stays within the fixed row) whose single line fits [maxWidthPx].
 */
private fun fitResultStyle(measurer: TextMeasurer, text: String, base: TextStyle, maxWidthPx: Int): TextStyle {
    fun styleAt(size: Float) = base.copy(
        fontSize = size.sp,
        lineHeight = (size * base.lineHeight.value / base.fontSize.value).sp,
    )
    val minSize = DisplayMetrics.MinResultFontSize.value
    var size = base.fontSize.value
    while (size > minSize) {
        val style = styleAt(size)
        val width = measurer.measure(text, style = style, softWrap = false, maxLines = 1).size.width
        if (width <= maxWidthPx) return style
        size -= DisplayMetrics.RESULT_FONT_STEP
    }
    return styleAt(minSize)
}
