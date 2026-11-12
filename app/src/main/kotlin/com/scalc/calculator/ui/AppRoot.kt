package com.scalc.calculator.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scalc.calculator.AppContainer
import com.scalc.calculator.R
import com.scalc.calculator.flavor.Flavor
import com.scalc.calculator.settings.SettingsDialog
import com.scalc.calculator.settings.ThemeMode
import com.scalc.calculator.ui.components.FeedbackHost
import com.scalc.calculator.ui.components.FeedbackState
import com.scalc.calculator.ui.components.Glyph
import com.scalc.calculator.ui.components.GlyphButton
import com.scalc.calculator.ui.components.HorizontalSeparator
import com.scalc.calculator.ui.components.LocalFeedback
import com.scalc.calculator.ui.components.TabStrip
import com.scalc.calculator.ui.theme.FluentTheme
import kotlinx.coroutines.launch

/**
 * Application shell: theme resolution, compact top tabs with an animated underline,
 * subtle tab-switch animation and per-tab saveable state.
 */
@Composable
fun AppRoot(container: AppContainer) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val systemDark = isSystemInDarkTheme()
    val loaded = settings ?: run {
        // Settings are read from disk asynchronously; avoid flashing the wrong theme.
        Box(Modifier.fillMaxSize())
        return
    }
    val dark = when (loaded.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // The host activity re-applies the status/navigation bar scrims and icon colors for the in-app
    // theme (the system-bar defaults otherwise follow the system light/dark mode on API 26-28).
    val activity = LocalActivity.current
    LaunchedEffect(dark, activity) {
        (activity as? SystemBarThemeHost)?.applyAppTheme(dark)
    }

    FluentTheme(darkTheme = dark) {
        val feedback = remember { FeedbackState() }
        val scope = rememberCoroutineScope()
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var themeSaveFailed by remember { mutableStateOf(false) }
        val tabs = Flavor.tabs
        var selected by rememberSaveable { mutableIntStateOf(0) }
        if (selected !in tabs.indices) selected = 0

        // The UI is English-only and laid out left-to-right (keypads, tab underline, expressions);
        // keep it LTR even before the view has resolved its direction under an RTL system locale.
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Ltr,
            LocalFeedback provides feedback,
        ) {
            Box(Modifier.fillMaxSize().background(FluentTheme.colors.background)) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    TabStrip(
                        titles = tabs.map { stringResource(it.titleRes) },
                        selectedIndex = selected,
                        onSelect = { selected = it },
                        trailing = {
                            GlyphButton(
                                glyph = Glyph.Settings,
                                contentDescription = stringResource(R.string.settings),
                                onClick = { showSettings = true },
                            )
                        },
                    )
                    HorizontalSeparator()
                    val stateHolder = rememberSaveableStateHolder()
                    AnimatedContent(
                        targetState = selected,
                        transitionSpec = {
                            val direction = if (targetState > initialState) 1 else -1
                            (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { full -> direction * full / 16 }) togetherWith
                                fadeOut(tween(90))
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        label = "tabContent",
                    ) { index ->
                        val tab = tabs[index]
                        stateHolder.SaveableStateProvider(tab.key) {
                            tab.content()
                        }
                    }
                }
                FeedbackHost(
                    state = feedback,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                )
            }
            if (showSettings) {
                SettingsDialog(
                    themeMode = loaded.themeMode,
                    onThemeModeChange = { mode ->
                        scope.launch { themeSaveFailed = !container.settingsRepository.setThemeMode(mode) }
                    },
                    aboutTextRes = Flavor.aboutTextRes,
                    onDismiss = {
                        showSettings = false
                        themeSaveFailed = false
                    },
                    errorText = if (themeSaveFailed) stringResource(R.string.settings_save_failed) else null,
                )
            }
        }
    }
}
