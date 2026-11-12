package com.scalc.calculator.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Flat, Windows 10 (2016-2017) inspired palette. No Material color system is used anywhere.
 *
 * Key surfaces:
 *  - [keyNumber]   digit keys (brightest in light theme, slightly lifted in dark theme)
 *  - [keyFunction] secondary keys (C, %, backspace, scientific functions)
 *  - [keyOperator] right-hand operator column (visually distinct)
 *  - [accent]      restrained Windows blue, used for "=", tab indicator, focus, selection
 */
@Immutable
data class FluentColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val keypadGap: Color,
    val keyNumber: Color,
    val keyFunction: Color,
    val keyOperator: Color,
    val keyHover: Color,
    val keyPressed: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val separator: Color,
    val border: Color,
    val borderStrong: Color,
    val accent: Color,
    val accentHover: Color,
    val accentPressed: Color,
    val onAccent: Color,
    val accentText: Color,
    val accentSubtle: Color,
    val inputBackground: Color,
    val error: Color,
    val warning: Color,
    val success: Color,
    val scrim: Color,
)

/** Restrained Windows 10 default accent blue, shared by both themes. */
val FluentAccentBlue = Color(0xFF0078D7)

val LightFluentColors = FluentColors(
    isDark = false,
    background = Color(0xFFF2F2F2),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF7F7F7),
    // Keys are separated only by this gap: it must stay visibly darker than every key fill
    // (>= 1.12:1, see FluentPaletteTest) without reaching the hover fill.
    keypadGap = Color(0xFFD9D9D9),
    keyNumber = Color(0xFFFCFCFC),
    keyFunction = Color(0xFFF0F0F0),
    keyOperator = Color(0xFFE8E8E8),
    keyHover = Color(0xFFDDDDDD),
    keyPressed = Color(0xFFC8C8C8),
    textPrimary = Color(0xFF1F1F1F),
    textSecondary = Color(0xFF5F5F5F),
    textDisabled = Color(0xFFA6A6A6),
    separator = Color(0xFFE1E1E1),
    border = Color(0xFFCCCCCC),
    borderStrong = Color(0xFF8A8A8A),
    accent = FluentAccentBlue,
    accentHover = Color(0xFF1A86DC),
    accentPressed = Color(0xFF005A9E),
    onAccent = Color(0xFFFFFFFF),
    accentText = Color(0xFF0063B1),
    accentSubtle = Color(0xFFDDEBF7),
    inputBackground = Color(0xFFFFFFFF),
    error = Color(0xFFC42B1C),
    warning = Color(0xFF8A5300),
    success = Color(0xFF0F7B0F),
    scrim = Color(0x66000000),
)

val DarkFluentColors = FluentColors(
    isDark = true,
    background = Color(0xFF1F1F1F),
    surface = Color(0xFF2B2B2B),
    surfaceAlt = Color(0xFF262626),
    keypadGap = Color(0xFF1F1F1F),
    keyNumber = Color(0xFF333333),
    keyFunction = Color(0xFF2A2A2A),
    keyOperator = Color(0xFF3B3B3B),
    keyHover = Color(0xFF454545),
    keyPressed = Color(0xFF555555),
    textPrimary = Color(0xFFE8E8E8),
    textSecondary = Color(0xFFA8A8A8),
    textDisabled = Color(0xFF6E6E6E),
    separator = Color(0xFF383838),
    border = Color(0xFF4A4A4A),
    borderStrong = Color(0xFF7A7A7A),
    accent = FluentAccentBlue,
    accentHover = Color(0xFF1A86DC),
    accentPressed = Color(0xFF005A9E),
    onAccent = Color(0xFFFFFFFF),
    // >= 4.5:1 on accentSubtle (selected options and toggle keys) and on the backgrounds.
    accentText = Color(0xFF5AAEEE),
    accentSubtle = Color(0xFF1C3A52),
    inputBackground = Color(0xFF2B2B2B),
    error = Color(0xFFFF8A80),
    warning = Color(0xFFF2C94C),
    success = Color(0xFF6CCB5F),
    scrim = Color(0x99000000),
)

/** Simple typography based on the native Android system sans-serif family (no bundled fonts). */
@Immutable
data class FluentTypography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val caption: TextStyle,
    val key: TextStyle,
    val keySmall: TextStyle,
    val tab: TextStyle,
)

private val System = FontFamily.SansSerif

val DefaultFluentTypography = FluentTypography(
    displayLarge = TextStyle(fontFamily = System, fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 52.sp),
    displayMedium = TextStyle(fontFamily = System, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 40.sp),
    displaySmall = TextStyle(fontFamily = System, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
    title = TextStyle(fontFamily = System, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 26.sp),
    subtitle = TextStyle(fontFamily = System, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    body = TextStyle(fontFamily = System, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    bodyStrong = TextStyle(fontFamily = System, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    caption = TextStyle(fontFamily = System, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    key = TextStyle(fontFamily = System, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 26.sp),
    keySmall = TextStyle(fontFamily = System, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 20.sp),
    tab = TextStyle(fontFamily = System, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
)

/** Shape and spacing tokens. Corner radii are deliberately restrained (2-4 dp). */
object FluentDimens {
    val cornerSmall = 2.dp
    val cornerMedium = 4.dp
    val hairline = 1.dp
    val keyGap = 2.dp
    val gutter = 12.dp
    val tabHeight = 44.dp
    val tabIndicator = 2.dp
    val minTouch = 44.dp
}

private val LocalFluentColors = staticCompositionLocalOf { LightFluentColors }
private val LocalFluentTypography = staticCompositionLocalOf { DefaultFluentTypography }

object FluentTheme {
    val colors: FluentColors
        @Composable @ReadOnlyComposable get() = LocalFluentColors.current
    val typography: FluentTypography
        @Composable @ReadOnlyComposable get() = LocalFluentTypography.current
}

@Composable
fun FluentTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkFluentColors else LightFluentColors
    val selection = TextSelectionColors(
        handleColor = colors.accent,
        backgroundColor = colors.accent.copy(alpha = 0.35f),
    )
    CompositionLocalProvider(
        LocalFluentColors provides colors,
        LocalFluentTypography provides DefaultFluentTypography,
        LocalIndication provides FluentIndication(
            hoverColor = colors.keyHover.copy(alpha = 0.55f),
            pressedColor = colors.keyPressed.copy(alpha = 0.65f),
            focusColor = colors.accent,
        ),
        LocalTextSelectionColors provides selection,
        content = content,
    )
}
