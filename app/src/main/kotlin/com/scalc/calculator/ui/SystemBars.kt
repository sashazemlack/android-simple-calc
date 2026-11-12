package com.scalc.calculator.ui

import android.content.res.Configuration

/**
 * Implemented by the host activity so the Compose root can make the system bars (status bar icons,
 * navigation bar scrim and icons) follow the in-app theme instead of the system light/dark mode.
 */
fun interface SystemBarThemeHost {
    /** Applies the bar appearance for the resolved in-app theme; cheap to call repeatedly. */
    fun applyAppTheme(dark: Boolean)
}

/**
 * Whether the system bars should use their dark appearance: the in-app theme once it is known
 * ([appDark] non-null), otherwise the system night mode (before the settings are loaded).
 */
internal fun resolveBarsDark(appDark: Boolean?, systemNight: Boolean): Boolean = appDark ?: systemNight

/** True when [uiMode] (a [Configuration.uiMode] value) has the night bit set. */
internal fun isNightUiMode(uiMode: Int): Boolean =
    (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
