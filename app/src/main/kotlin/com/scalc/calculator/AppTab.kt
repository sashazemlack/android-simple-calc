package com.scalc.calculator

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable

/**
 * One top-level tab. The list of tabs is supplied by the flavor-specific
 * [com.scalc.calculator.flavor.Flavor] object, so shared code never references
 * flavor-only (Exchange) classes at compile time.
 */
class AppTab(
    val key: String,
    @StringRes val titleRes: Int,
    val content: @Composable () -> Unit,
)
