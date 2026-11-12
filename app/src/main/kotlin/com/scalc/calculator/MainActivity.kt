package com.scalc.calculator

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scalc.calculator.ui.AppRoot
import com.scalc.calculator.ui.SystemBarThemeHost
import com.scalc.calculator.ui.isNightUiMode
import com.scalc.calculator.ui.resolveBarsDark

class MainActivity : ComponentActivity(), SystemBarThemeHost {

    /** Resolved in-app theme (null until AppRoot has loaded the settings: follow the system). */
    private var appDarkTheme: Boolean? = null

    /*
     * The same style objects must be passed to every enableEdgeToEdge call: the library keeps the
     * FIRST call's styles in a hidden decor view and re-applies them on configuration changes that
     * do not recreate the activity. Their dark-mode lambdas read [appDarkTheme], so those re-runs
     * follow the in-app theme too. The scrims equal the library defaults (API 26-28 draw the
     * navigation-bar scrim themselves; API 29+ use a transparent, contrast-enforced bar).
     */
    private val statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { res ->
        resolveBarsDark(appDarkTheme, isNightUiMode(res.configuration.uiMode))
    }
    private val navigationBarStyle = SystemBarStyle.auto(
        Color.argb(0xE6, 0xFF, 0xFF, 0xFF),
        Color.argb(0x80, 0x1B, 0x1B, 0x1B),
    ) { res ->
        resolveBarsDark(appDarkTheme, isNightUiMode(res.configuration.uiMode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge is mandatory when targeting API 35+; insets are handled in AppRoot.
        enableEdgeToEdge(statusBarStyle, navigationBarStyle)
        super.onCreate(savedInstanceState)
        setContent {
            AppRoot(container = appContainer)
        }
    }

    override fun applyAppTheme(dark: Boolean) {
        if (appDarkTheme == dark) return
        appDarkTheme = dark
        // Re-applies the scrims (API 26-28) and the light/dark bar icons (all API levels).
        enableEdgeToEdge(statusBarStyle, navigationBarStyle)
    }
}
