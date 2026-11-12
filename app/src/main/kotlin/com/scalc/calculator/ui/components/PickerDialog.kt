package com.scalc.calculator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scalc.calculator.R
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme
import java.text.Normalizer
import java.util.Locale

/**
 * One selectable row in a [PickerDialog]. [key] must be unique and stable. [keywords] are extra
 * search terms that are not displayed (e.g. a currency's English name next to its localized one).
 */
data class PickerItem(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val keywords: List<String> = emptyList(),
)

/**
 * Flat modal list picker with optional search (title, subtitle and keyword substring, case- and
 * accent-insensitive, see [filterPickerItems]).
 * Used for unit selection (no search) and currency selection (searchable).
 */
@Composable
fun PickerDialog(
    title: String,
    items: List<PickerItem>,
    selectedKey: String?,
    onSelect: (PickerItem) -> Unit,
    onDismiss: () -> Unit,
    searchable: Boolean = false,
    searchPlaceholder: String = stringResource(R.string.picker_search),
    emptyText: String = stringResource(R.string.picker_no_matches),
) {
    val colors = FluentTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(items, query) { filterPickerItems(items, query) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    // Bring the selected row into view once per opening. After a configuration change the saved
    // list position (and query) are restored instead, so this must not run again.
    var initialScrollDone by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!initialScrollDone) {
            initialScrollDone = true
            initialPickerScrollIndex(filtered, selectedKey)?.let { listState.scrollToItem(it) }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 24.dp, vertical = 48.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(FluentDimens.cornerMedium))
                .border(1.dp, colors.borderStrong, RoundedCornerShape(FluentDimens.cornerMedium)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FluentText(title, modifier = Modifier.weight(1f), style = FluentTheme.typography.title, maxLines = 1)
                GlyphButton(Glyph.Close, contentDescription = stringResource(R.string.close), onClick = onDismiss)
            }
            if (searchable) {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .heightIn(min = FluentDimens.minTouch)
                        .background(colors.inputBackground, RoundedCornerShape(FluentDimens.cornerSmall))
                        .border(1.dp, colors.border, RoundedCornerShape(FluentDimens.cornerSmall))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(Glyph.Search, contentDescription = null, tint = colors.textSecondary, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            FluentText(searchPlaceholder, style = FluentTheme.typography.body, color = colors.textSecondary, maxLines = 1)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = FluentTheme.typography.body.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                    }
                    if (query.isNotEmpty()) {
                        GlyphButton(Glyph.Close, contentDescription = stringResource(R.string.picker_clear_search), onClick = { query = "" })
                    }
                }
            }
            HorizontalSeparator(Modifier.padding(top = 8.dp))
            if (filtered.isEmpty()) {
                FluentText(
                    emptyText,
                    modifier = Modifier.padding(16.dp),
                    style = FluentTheme.typography.body,
                    color = colors.textSecondary,
                )
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(filtered, key = { it.key }) { item ->
                    val isSelected = item.key == selectedKey
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .background(if (isSelected) colors.accentSubtle else colors.surface)
                            .flatClickable(role = Role.RadioButton) { onSelect(item) }
                            .semantics { selected = isSelected },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .heightIn(min = 48.dp)
                                .fillMaxHeight()
                                .background(if (isSelected) colors.accent else colors.surface),
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 13.dp, vertical = 6.dp)) {
                            FluentText(item.title, style = FluentTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (item.subtitle != null) {
                                FluentText(
                                    item.subtitle,
                                    style = FluentTheme.typography.caption,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (isSelected) {
                            GlyphIcon(Glyph.Check, contentDescription = null, tint = colors.accentText, size = 18.dp, modifier = Modifier.padding(end = 12.dp))
                        }
                    }
                    HorizontalSeparator()
                }
            }
        }
    }
}

/**
 * Substring filter on title, subtitle and keywords that ignores case and accents (see
 * [foldForSearch]), so "zloty" finds "Polish Złoty" and "krona" finds "Icelandic Króna". A blank
 * query returns all items.
 */
fun filterPickerItems(items: List<PickerItem>, query: String): List<PickerItem> {
    val q = foldForSearch(query.trim())
    if (q.isEmpty()) return items
    return items.filter { item ->
        foldForSearch(item.title).contains(q) ||
            item.subtitle?.let { foldForSearch(it).contains(q) } == true ||
            item.keywords.any { foldForSearch(it).contains(q) }
    }
}

private val CombiningMarks = Regex("\\p{Mn}+")

/**
 * Search key for [text]: canonical decomposition with the combining marks removed (ó → o),
 * a few letters that do not decompose mapped to their usual Latin spelling (ł → l, ø → o, ß → ss...),
 * then lower-cased with the root locale.
 */
internal fun foldForSearch(text: String): String {
    if (text.isEmpty()) return text
    val stripped = CombiningMarks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
    val lower = stripped.lowercase(Locale.ROOT)
    if (lower.none { it in SpecialFolds }) return lower
    return buildString(lower.length + 2) {
        for (c in lower) append(SpecialFolds[c] ?: c.toString())
    }
}

private val SpecialFolds: Map<Char, String> = mapOf(
    'ł' to "l",
    'ø' to "o",
    'đ' to "d",
    'ð' to "d",
    'ħ' to "h",
    'ı' to "i",
    'ß' to "ss",
    'æ' to "ae",
    'œ' to "oe",
)

/**
 * Index to scroll [items] (the list actually shown, i.e. after filtering) to so that the row of
 * [selectedKey] appears with two rows of context above it, or null when no scroll is needed.
 */
internal fun initialPickerScrollIndex(items: List<PickerItem>, selectedKey: String?): Int? {
    val index = items.indexOfFirst { it.key == selectedKey }
    return if (index > 2) index - 2 else null
}
