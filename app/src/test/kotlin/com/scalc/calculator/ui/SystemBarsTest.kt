package com.scalc.calculator.ui

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarsTest {

    @Test
    fun beforeTheAppThemeIsKnownTheBarsFollowTheSystem() {
        assertTrue(resolveBarsDark(appDark = null, systemNight = true))
        assertFalse(resolveBarsDark(appDark = null, systemNight = false))
    }

    @Test
    fun anExplicitAppThemeOverridesTheSystemMode() {
        // App Dark on a light system: dark scrim + light icons (API 26-28 kept the light scrim before).
        assertTrue(resolveBarsDark(appDark = true, systemNight = false))
        // App Light on a dark system: light scrim + dark icons.
        assertFalse(resolveBarsDark(appDark = false, systemNight = true))
        assertTrue(resolveBarsDark(appDark = true, systemNight = true))
        assertFalse(resolveBarsDark(appDark = false, systemNight = false))
    }

    @Test
    fun nightModeIsReadFromTheUiModeNightBits() {
        assertTrue(isNightUiMode(Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES))
        assertFalse(isNightUiMode(Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO))
        assertFalse(isNightUiMode(Configuration.UI_MODE_NIGHT_UNDEFINED))
    }
}
