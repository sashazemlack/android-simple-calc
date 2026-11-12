package com.scalc.calculator.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.core.net.toUri
import com.scalc.calculator.R
import com.scalc.calculator.ui.components.FlatButton
import com.scalc.calculator.ui.components.FlatButtonStyle
import com.scalc.calculator.ui.components.FluentText
import com.scalc.calculator.ui.components.HorizontalSeparator
import com.scalc.calculator.ui.components.SectionLabel
import com.scalc.calculator.ui.components.SegmentedChoice
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme

/** Public source repository, opened in the default browser by the "Git project" button. */
internal const val PROJECT_URL = "https://github.com/sashazemlack/android-simple-calc"

/**
 * Flat settings dialog: theme selection (System / Light / Dark) and a short about section with a
 * "Git project" button. [errorText], when non-null, is shown under the theme choice (e.g. the
 * choice could not be saved).
 */
@Composable
fun SettingsDialog(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
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
    var linkFailed by remember { mutableStateOf(false) }
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
            FluentText(
                stringResource(R.string.about_description),
                style = FluentTheme.typography.body,
                color = colors.textSecondary,
            )
            if (linkFailed) {
                FluentText(
                    stringResource(R.string.settings_link_failed),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = FluentTheme.typography.caption,
                    color = colors.error,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlatButton(onClick = { linkFailed = !openInBrowser(context, PROJECT_URL) }) {
                    FluentText(stringResource(R.string.settings_git_project))
                }
                Spacer(Modifier.weight(1f))
                FlatButton(onClick = onDismiss, style = FlatButtonStyle.Accent) {
                    FluentText(stringResource(R.string.close), color = colors.onAccent)
                }
            }
        }
    }
}

/**
 * Opens [url] in the user's default browser (the app itself makes no network request; the
 * Offline app has no network permission). Returns false when no app can handle the link.
 */
private fun openInBrowser(context: Context, url: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
