package com.scalc.calculator.converter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Unit Converter tab (both flavors, fully offline): a category grid, and per category a
 * conversion screen that converts automatically while the amount is typed. State lives in the
 * activity-scoped [ConverterViewModel] so it survives tab switches.
 */
@Composable
fun ConverterScreen(modifier: Modifier = Modifier) {
    val viewModel: ConverterViewModel = viewModel(factory = ConverterViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (!state.isLoaded) {
        // Persisted category/units are read asynchronously; avoid flashing the grid first.
        Box(modifier.fillMaxSize())
        return
    }

    BackHandler(enabled = state.openCategory != null, onBack = viewModel::closeCategory)

    AnimatedContent(
        targetState = state.openCategory,
        transitionSpec = { fadeIn(tween(FADE_IN_MILLIS)) togetherWith fadeOut(tween(FADE_OUT_MILLIS)) },
        modifier = modifier.fillMaxSize(),
        label = "converterContent",
    ) { category ->
        if (category == null) {
            CategoryGrid(lastCategory = state.lastCategory, onOpen = viewModel::openCategory)
        } else {
            ConversionPane(
                conversion = state.conversion(category),
                onBack = viewModel::closeCategory,
                onKey = viewModel::onKey,
                onSelectFrom = viewModel::selectFromUnit,
                onSelectTo = viewModel::selectToUnit,
                onSwap = viewModel::swapUnits,
            )
        }
    }
}

private const val FADE_IN_MILLIS = 180
private const val FADE_OUT_MILLIS = 120
