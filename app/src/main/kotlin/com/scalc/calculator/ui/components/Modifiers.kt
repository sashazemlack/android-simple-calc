package com.scalc.calculator.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Click + long-click without any default indication (callers draw their own flat state colors). */
internal fun Modifier.combinedClickableCompat(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = this.combinedClickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    role = Role.Button,
    onLongClick = onLongClick,
    onClick = onClick,
)

/** Accessibility label for keys whose visible text is a symbol (e.g. "÷" → "Divide"). */
internal fun Modifier.keyDescription(description: String): Modifier =
    this.semantics { contentDescription = description }
