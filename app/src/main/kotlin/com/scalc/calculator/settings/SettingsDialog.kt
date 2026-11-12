package com.scalc.calculator.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scalc.calculator.R
import com.scalc.calculator.ui.components.FlatButton
import com.scalc.calculator.ui.components.FlatButtonStyle
import com.scalc.calculator.ui.components.FluentText
import com.scalc.calculator.ui.components.HorizontalSeparator
import com.scalc.calculator.ui.components.SectionLabel
import com.scalc.calculator.ui.components.SegmentedChoice
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme

/**
 * Flat settings dialog: theme selection (System / Light / Dark) and a short about section.
 * [errorText], when non-null, is shown under the theme choice (e.g. the choice could not be saved).
 */
@Composable
fun SettingsDialog(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    @StringRes aboutTextRes: Int,
    onDismiss: () -> Unit,
    errorText: String? = null,
) {
    val colors = FluentTheme.colors
    val context = LocalContext.current
    val themeLabels = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark),
    )
    val versionName = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(FluentDimens.cornerMedium))
                .border(1.dp, colors.borderStrong, RoundedCornerShape(FluentDimens.cornerMedium))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FluentText(stringResource(R.string.settings), style = FluentTheme.typography.title)
            SectionLabel(stringResource(R.string.settings_theme))
            SegmentedChoice(
                options = ThemeMode.entries,
                selected = themeMode,
                label = { mode -> themeLabels.getValue(mode) },
                onSelect = onThemeModeChange,
                modifier = Modifier.fillMaxWidth(),
            )
            FluentText(
                stringResource(R.string.settings_theme_hint),
                style = FluentTheme.typography.caption,
                color = colors.textSecondary,
            )
            if (errorText != null) {
                FluentText(
                    errorText,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = FluentTheme.typography.caption,
                    color = colors.error,
                )
            }
            HorizontalSeparator()
            SectionLabel(stringResource(R.string.settings_about))
            FluentText(
                stringResource(R.string.app_name) + if (versionName.isNotEmpty()) "  $versionName" else "",
                style = FluentTheme.typography.bodyStrong,
            )
            FluentText(stringResource(aboutTextRes), style = FluentTheme.typography.body, color = colors.textSecondary)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                FlatButton(onClick = onDismiss, style = FlatButtonStyle.Accent) {
                    FluentText(stringResource(R.string.close), color = colors.onAccent)
                }
            }
        }
    }
}
