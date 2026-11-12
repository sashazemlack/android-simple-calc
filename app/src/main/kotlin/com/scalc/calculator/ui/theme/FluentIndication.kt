package com.scalc.calculator.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Flat Windows-style press/hover/focus feedback: a plain rectangular overlay for press and hover,
 * and a 2 dp [focusColor] outline (drawn inside the bounds) while the element has keyboard/D-pad
 * focus. No ripple, no elevation.
 * Used as the default [androidx.compose.foundation.LocalIndication] for generic clickables
 * (list rows, grid tiles, segmented options). Keypad keys draw their own state colors.
 */
class FluentIndication(
    private val hoverColor: Color,
    private val pressedColor: Color,
    private val focusColor: Color = Color.Unspecified,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        FluentIndicationNode(interactionSource, hoverColor, pressedColor, focusColor)

    override fun equals(other: Any?): Boolean =
        other is FluentIndication &&
            other.hoverColor == hoverColor &&
            other.pressedColor == pressedColor &&
            other.focusColor == focusColor

    override fun hashCode(): Int = 31 * (31 * hoverColor.hashCode() + pressedColor.hashCode()) + focusColor.hashCode()
}

/** Counts the active press, hover and focus interactions of one interaction source. */
internal class IndicationCounters {
    var pressed = 0
        private set
    var hovered = 0
        private set
    var focused = 0
        private set

    /** Updates the counters; returns true when [interaction] is one this indication draws. */
    fun apply(interaction: Interaction): Boolean {
        when (interaction) {
            is PressInteraction.Press -> pressed++
            is PressInteraction.Release -> pressed = (pressed - 1).coerceAtLeast(0)
            is PressInteraction.Cancel -> pressed = (pressed - 1).coerceAtLeast(0)
            is HoverInteraction.Enter -> hovered++
            is HoverInteraction.Exit -> hovered = (hovered - 1).coerceAtLeast(0)
            is FocusInteraction.Focus -> focused++
            is FocusInteraction.Unfocus -> focused = (focused - 1).coerceAtLeast(0)
            else -> return false
        }
        return true
    }
}

private class FluentIndicationNode(
    private val interactionSource: InteractionSource,
    private val hoverColor: Color,
    private val pressedColor: Color,
    private val focusColor: Color,
) : Modifier.Node(), DrawModifierNode {

    private val counters = IndicationCounters()

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                if (counters.apply(interaction)) invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        // Overlay sits between the element background (applied before clickable) and its content,
        // so text and glyphs stay crisp while the surface darkens/lightens.
        when {
            counters.pressed > 0 -> drawRect(pressedColor)
            counters.hovered > 0 -> drawRect(hoverColor)
        }
        drawContent()
        if (counters.focused > 0 && focusColor.isSpecified) {
            val stroke = 2.dp.toPx()
            val inset = stroke / 2f
            drawRect(
                color = focusColor,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke),
            )
        }
    }
}
