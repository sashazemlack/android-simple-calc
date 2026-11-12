package com.scalc.calculator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme

/** Plain text using the theme's system sans-serif typography. Replaces Material's Text. */
@Composable
fun FluentText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = FluentTheme.typography.body,
    color: Color = FluentTheme.colors.textPrimary,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign != null) style.copy(color = color, textAlign = textAlign) else style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
    )
}

/** 1 dp crisp horizontal rule. */
@Composable
fun HorizontalSeparator(modifier: Modifier = Modifier, color: Color = FluentTheme.colors.separator) {
    Spacer(modifier.fillMaxWidth().height(FluentDimens.hairline).background(color))
}

/** 1 dp crisp vertical rule. */
@Composable
fun VerticalSeparator(modifier: Modifier = Modifier, color: Color = FluentTheme.colors.separator) {
    Spacer(modifier.fillMaxHeight().width(FluentDimens.hairline).background(color))
}

enum class FlatButtonStyle { Standard, Accent, Subtle }

/**
 * Flat rectangular button with a thin border (Standard), solid accent fill (Accent) or no chrome (Subtle).
 * States: hover/pressed change the fill; keyboard focus draws a 2 dp accent outline.
 */
@Composable
fun FlatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FlatButtonStyle = FlatButtonStyle.Standard,
    enabled: Boolean = true,
    contentPadding: Dp = 12.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = FluentTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(FluentDimens.cornerSmall)
    val fill = when (style) {
        FlatButtonStyle.Accent -> when {
            !enabled -> colors.textDisabled
            pressed -> colors.accentPressed
            hovered -> colors.accentHover
            else -> colors.accent
        }
        FlatButtonStyle.Standard -> when {
            pressed && enabled -> colors.keyPressed
            hovered && enabled -> colors.keyHover
            else -> colors.keyFunction
        }
        FlatButtonStyle.Subtle -> when {
            pressed && enabled -> colors.keyPressed
            hovered && enabled -> colors.keyHover
            else -> Color.Transparent
        }
    }
    val borderColor = when {
        focused -> colors.accent
        style == FlatButtonStyle.Standard -> if (pressed) colors.borderStrong else colors.border
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .background(fill, shape)
            .border(if (focused) 2.dp else FluentDimens.hairline, borderColor, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = contentPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

/** Flat button showing a [Glyph] (plus optional label) using the current text color. */
@Composable
fun GlyphButton(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    tint: Color = if (enabled) FluentTheme.colors.textPrimary else FluentTheme.colors.textDisabled,
    style: FlatButtonStyle = FlatButtonStyle.Subtle,
) {
    FlatButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = FluentDimens.minTouch),
        style = style,
        enabled = enabled,
        contentPadding = if (label == null) 10.dp else 12.dp,
    ) {
        GlyphIcon(glyph = glyph, contentDescription = if (label == null) contentDescription else null, tint = tint, size = 20.dp)
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            FluentText(label, style = FluentTheme.typography.body, color = tint, maxLines = 1)
        }
    }
}

/** Visual role of a keypad key. */
enum class KeyKind { Number, Function, Operator, Accent, Toggle }

/**
 * Calculator keypad key: flat rectangle filling its grid cell. Hover/pressed states shift the fill;
 * [KeyKind.Accent] is the restrained blue "=" key; [KeyKind.Toggle] with [selected] shows an accent
 * underline (used for modes such as inv and RAD/DEG).
 */
@Composable
fun KeyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: KeyKind = KeyKind.Number,
    enabled: Boolean = true,
    selected: Boolean = false,
    contentDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = FluentTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val base = when (kind) {
        KeyKind.Number -> colors.keyNumber
        KeyKind.Function, KeyKind.Toggle -> if (selected) colors.accentSubtle else colors.keyFunction
        KeyKind.Operator -> colors.keyOperator
        KeyKind.Accent -> colors.accent
    }
    val fill = when {
        !enabled -> base
        kind == KeyKind.Accent && pressed -> colors.accentPressed
        kind == KeyKind.Accent && hovered -> colors.accentHover
        pressed -> colors.keyPressed
        hovered -> colors.keyHover
        else -> base
    }
    val shape = RoundedCornerShape(FluentDimens.cornerSmall)
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickableCompat(interaction, enabled, onClick, onLongClick)
    } else {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    }
    Box(
        modifier = modifier
            .background(fill, shape)
            .then(if (focused) Modifier.border(2.dp, colors.accent, shape) else Modifier)
            .then(clickModifier)
            .then(if (contentDescription != null) Modifier.keyDescription(contentDescription) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (kind == KeyKind.Toggle && selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(width = 18.dp, height = 2.dp)
                    .background(colors.accent),
            )
        }
    }
}

/** Convenience text key. Text color follows the key kind. */
@Composable
fun TextKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: KeyKind = KeyKind.Number,
    enabled: Boolean = true,
    selected: Boolean = false,
    style: TextStyle = FluentTheme.typography.key,
    contentDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = FluentTheme.colors
    val textColor = when {
        !enabled -> colors.textDisabled
        kind == KeyKind.Accent -> colors.onAccent
        kind == KeyKind.Toggle && selected -> colors.accentText
        else -> colors.textPrimary
    }
    KeyButton(
        onClick = onClick,
        modifier = modifier,
        kind = kind,
        enabled = enabled,
        selected = selected,
        contentDescription = contentDescription,
        onLongClick = onLongClick,
    ) {
        FluentText(text, style = style, color = textColor, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** Glyph key (e.g. backspace). */
@Composable
fun GlyphKey(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: KeyKind = KeyKind.Function,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = FluentTheme.colors
    KeyButton(
        onClick = onClick,
        modifier = modifier,
        kind = kind,
        enabled = enabled,
        contentDescription = contentDescription,
        onLongClick = onLongClick,
    ) {
        GlyphIcon(
            glyph = glyph,
            contentDescription = null,
            tint = if (!enabled) colors.textDisabled else if (kind == KeyKind.Accent) colors.onAccent else colors.textPrimary,
            size = 22.dp,
        )
    }
}

/** Focusable, flat clickable row/tile helper using the theme's flat indication. */
fun Modifier.flatClickable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = this.clickable(enabled = enabled, role = role, onClick = onClick)

/** Makes an element focusable without a visual (used by wrappers that draw focus themselves). */
fun Modifier.plainFocusable(): Modifier = this.focusable()
