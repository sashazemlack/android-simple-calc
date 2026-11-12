package com.scalc.calculator.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Compact flat top tabs with a restrained accent underline that slides to the selected tab.
 * Each tab is sized to its label (left aligned), like Windows 10 pivots.
 *
 * Every tab is as wide as its label in the selected (widest) style plus a side padding, so nothing
 * shifts when the selection changes and the underline position is known without a layout pass.
 * The padding shrinks from 12 dp to 6 dp to fit narrow screens; when even that does not fit
 * (large font scale) the strip scrolls horizontally instead of truncating labels, and the
 * selected tab is scrolled into view.
 */
@Composable
fun TabStrip(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val colors = FluentTheme.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val widestStyle = FluentTheme.typography.bodyStrong
    val labelWidths = remember(titles, widestStyle, density, measurer) {
        titles.map { title ->
            val px = measurer.measure(title, widestStyle, maxLines = 1, softWrap = false, density = density).size.width
            with(density) { px.toDp() }
        }
    }

    Row(modifier.fillMaxWidth().height(FluentDimens.tabHeight), verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.weight(1f).height(FluentDimens.tabHeight)) {
            // Whole pixels (and 1 px of slack) so rounding never makes the tabs overflow by a pixel.
            val padding = with(density) {
                val ideal = tabHorizontalPadding(maxWidth - 1.toDp(), labelWidths, startInset = TabStartInset)
                ideal.toPx().toInt().toDp()
            }
            val bounds = tabLabelBounds(labelWidths, padding, startInset = TabStartInset)
            val target = bounds.getOrNull(selectedIndex) ?: (0.dp to 0.dp)
            val memo = remember { TabStripMemo().apply { lastIndex = selectedIndex } }
            // Start at the target: no slide-in from the left edge on launch or after recreation.
            val indicatorX = remember { Animatable(target.first, Dp.VectorConverter) }
            val indicatorW = remember { Animatable(target.second, Dp.VectorConverter) }
            LaunchedEffect(target) {
                if (target.second == 0.dp) return@LaunchedEffect
                val selectionChanged = memo.lastIndex != selectedIndex
                memo.lastIndex = selectedIndex
                if (shouldSnapIndicator(indicatorW.value, target.second, selectionChanged)) {
                    indicatorX.snapTo(target.first)
                    indicatorW.snapTo(target.second)
                } else {
                    coroutineScope {
                        launch { indicatorX.animateTo(target.first, tween(durationMillis = 180)) }
                        launch { indicatorW.animateTo(target.second, tween(durationMillis = 180)) }
                    }
                }
            }

            val scroll = rememberScrollState()
            val viewportPx = constraints.maxWidth
            LaunchedEffect(selectedIndex, bounds, viewportPx) {
                // Jump on the first placement (e.g. a restored selection), animate afterwards.
                val firstPlacement = !memo.scrollPlaced
                memo.scrollPlaced = true
                val (x, w) = bounds.getOrNull(selectedIndex) ?: return@LaunchedEffect
                val startPx = with(density) { (x - padding).roundToPx() }
                val endPx = with(density) { (x + w + padding).roundToPx() }
                val to = tabScrollTarget(startPx, endPx, scroll.value, viewportPx) ?: return@LaunchedEffect
                if (firstPlacement) scroll.scrollTo(to) else scroll.animateScrollTo(to)
            }

            Box(Modifier.height(FluentDimens.tabHeight).horizontalScroll(scroll)) {
                Row(
                    Modifier.height(FluentDimens.tabHeight).padding(start = TabStartInset),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    titles.forEachIndexed { index, title ->
                        val interaction = remember { MutableInteractionSource() }
                        val focused by interaction.collectIsFocusedAsState()
                        val pressed by interaction.collectIsPressedAsState()
                        val selected = index == selectedIndex
                        Box(
                            Modifier
                                .height(FluentDimens.tabHeight)
                                .width(labelWidths[index] + padding * 2)
                                .then(if (focused) Modifier.border(2.dp, colors.accent) else Modifier)
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { onSelect(index) },
                                )
                                .semantics { this.selected = selected }
                                .padding(horizontal = padding),
                            contentAlignment = Alignment.Center,
                        ) {
                            FluentText(
                                text = title,
                                style = if (selected) FluentTheme.typography.bodyStrong else FluentTheme.typography.tab,
                                color = when {
                                    selected -> colors.textPrimary
                                    pressed -> colors.textPrimary
                                    else -> colors.textSecondary
                                },
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
                // Underline: inside the scrolled content, so it uses content coordinates; the
                // animated values are read only while drawing (no recomposition per frame).
                val accent = colors.accent
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            val width = indicatorW.value.toPx()
                            if (width > 0f) {
                                val height = FluentDimens.tabIndicator.toPx()
                                drawRect(
                                    color = accent,
                                    topLeft = Offset(indicatorX.value.toPx(), size.height - height),
                                    size = Size(width, height),
                                )
                            }
                        },
                )
            }
        }
        trailing()
    }
}

private val TabStartInset = 4.dp

/** Per-composition bookkeeping of [TabStrip] that must not trigger recomposition. */
private class TabStripMemo {
    var lastIndex = -1
    var scrollPlaced = false
}

/**
 * Side padding of each tab so that all labels ([labelWidths]) fit into [available] after
 * [startInset]: the share of the free space, clamped to [min]..[max]. At [min] the labels may
 * still not fit; the strip then scrolls.
 */
internal fun tabHorizontalPadding(
    available: Dp,
    labelWidths: List<Dp>,
    startInset: Dp = 4.dp,
    max: Dp = 12.dp,
    min: Dp = 6.dp,
): Dp {
    if (labelWidths.isEmpty()) return max
    val free = available - startInset - labelWidths.fold(0.dp) { acc, w -> acc + w }
    return (free / (2 * labelWidths.size)).coerceIn(min, max)
}

/** Start x and width of every tab label (the underline extent), in strip content coordinates. */
internal fun tabLabelBounds(labelWidths: List<Dp>, padding: Dp, startInset: Dp = 4.dp): List<Pair<Dp, Dp>> {
    var x = startInset
    return labelWidths.map { w ->
        val bounds = (x + padding) to w
        x += w + padding * 2
        bounds
    }
}

/**
 * Whether the underline jumps to its target instead of sliding: on its first real placement
 * ([currentWidth] still 0) and when only the layout changed, not the selection.
 */
internal fun shouldSnapIndicator(currentWidth: Dp, targetWidth: Dp, selectionChanged: Boolean = true): Boolean =
    targetWidth > 0.dp && (!selectionChanged || currentWidth == 0.dp)

/**
 * Scroll position that brings the tab spanning [tabStart]..[tabEnd] (pixels, content coordinates)
 * fully into a [viewport]-wide window currently scrolled to [scroll], or null when it is visible.
 */
internal fun tabScrollTarget(tabStart: Int, tabEnd: Int, scroll: Int, viewport: Int): Int? = when {
    tabStart < scroll -> tabStart.coerceAtLeast(0)
    tabEnd > scroll + viewport -> if (tabEnd - tabStart >= viewport) tabStart else tabEnd - viewport
    else -> null
}

/**
 * Flat selector field (label above, value + chevron in a thin bordered rectangle).
 * Used for unit and currency selection.
 */
@Composable
fun SelectorField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    val colors = FluentTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(FluentDimens.cornerSmall)
    Column(modifier) {
        FluentText(label, style = FluentTheme.typography.caption, color = colors.textSecondary, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = FluentDimens.minTouch)
                .background(if (pressed) colors.keyPressed else colors.inputBackground, shape)
                .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else colors.border, shape)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.DropdownList, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                FluentText(value, style = FluentTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (supporting != null) {
                    FluentText(supporting, style = FluentTheme.typography.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            GlyphIcon(Glyph.ChevronDown, contentDescription = null, tint = colors.textSecondary, size = 16.dp)
        }
    }
}

/* ---------------------------------------------------------------------------------------------
 * Brief, flat feedback messages ("Copied", "Clipboard is empty"...). Not a Material snackbar:
 * a small bordered rectangle that fades in/out above the bottom edge.
 * ------------------------------------------------------------------------------------------- */

enum class FeedbackKind { Info, Success, Error }

data class FeedbackMessage(val id: Long, val text: String, val kind: FeedbackKind)

@Stable
class FeedbackState {
    var current by mutableStateOf<FeedbackMessage?>(null)
        private set
    private var counter = 0L

    fun show(text: String, kind: FeedbackKind = FeedbackKind.Info) {
        current = FeedbackMessage(++counter, text, kind)
    }

    internal fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

val LocalFeedback = staticCompositionLocalOf { FeedbackState() }

@Composable
fun FeedbackHost(state: FeedbackState, modifier: Modifier = Modifier, durationMillis: Long = 1800) {
    val colors = FluentTheme.colors
    val message = state.current
    if (message != null) {
        androidx.compose.runtime.LaunchedEffect(message.id) {
            delay(durationMillis)
            state.dismiss(message.id)
        }
    }
    // Keep the last text while fading out.
    var lastShown by remember { mutableStateOf<FeedbackMessage?>(null) }
    if (message != null) lastShown = message
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(200)),
    ) {
        val shown = lastShown ?: return@AnimatedVisibility
        val accentBar = when (shown.kind) {
            FeedbackKind.Info -> colors.accent
            FeedbackKind.Success -> colors.success
            FeedbackKind.Error -> colors.error
        }
        Row(
            Modifier
                .background(colors.surface, RoundedCornerShape(FluentDimens.cornerSmall))
                .border(1.dp, colors.borderStrong, RoundedCornerShape(FluentDimens.cornerSmall))
                .semantics { liveRegion = LiveRegionMode.Polite }
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).heightIn(min = 36.dp).background(accentBar))
            FluentText(
                shown.text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = FluentTheme.typography.body,
                maxLines = 2,
            )
        }
    }
}

/** Small section caption used above groups of controls. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    FluentText(
        text,
        modifier = modifier,
        style = FluentTheme.typography.caption,
        color = FluentTheme.colors.textSecondary,
        maxLines = 1,
    )
}

/** Horizontal group of mutually exclusive flat options (e.g. theme System/Light/Dark). */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FluentTheme.colors
    val shape = RoundedCornerShape(FluentDimens.cornerSmall)
    Row(
        modifier
            .border(1.dp, colors.border, shape)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            if (index > 0) VerticalSeparator(color = colors.border)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = FluentDimens.minTouch)
                    .background(if (isSelected) colors.accentSubtle else colors.inputBackground)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .semantics { this.selected = isSelected },
                contentAlignment = Alignment.Center,
            ) {
                FluentText(
                    label(option),
                    style = if (isSelected) FluentTheme.typography.bodyStrong else FluentTheme.typography.body,
                    color = if (isSelected) colors.accentText else colors.textPrimary,
                    maxLines = 1,
                )
                if (isSelected) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(colors.accent))
                }
            }
        }
    }
}
