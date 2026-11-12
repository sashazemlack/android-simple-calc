package com.scalc.calculator.converter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scalc.calculator.R
import com.scalc.calculator.converter.engine.UnitCatalog
import com.scalc.calculator.converter.engine.UnitCategory
import com.scalc.calculator.ui.components.FluentText
import com.scalc.calculator.ui.components.GlyphIcon
import com.scalc.calculator.ui.components.flatClickable
import com.scalc.calculator.ui.theme.FluentDimens
import com.scalc.calculator.ui.theme.FluentTheme
import kotlin.math.max

private val TileMinWidth = 180.dp
private val TileMinHeight = 92.dp
private val TileGap = 8.dp
private val LastUsedBarWidth = 3.dp
private val TilePaddingHorizontal = 12.dp
private val TileIconSize = 24.dp
private val TileIconGap = 8.dp

/** Smallest title size the grid shrinks to so that no title word has to break mid-word. */
private val TitleMinFontSize = 12.sp

/** Rounding slack (px) between the computed title width and the width the Row layout assigns. */
private const val TitleWidthSlackPx = 2

/**
 * Responsive grid of flat category tiles: two columns on typical phones, one more column for
 * every additional 180 dp of width. Scrolls when the tiles do not fit (landscape, large fonts).
 */
@Composable
internal fun CategoryGrid(
    lastCategory: UnitCategory?,
    onOpen: (UnitCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = categoryColumns(maxWidth)
        val titles = UnitCategory.entries.map { stringResource(it.titleRes) }
        val titleStyle = fittedTitleStyle(titles, categoryTitleWidth(maxWidth, columns))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(FluentDimens.gutter),
            verticalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            UnitCategory.entries.chunked(columns).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    row.forEach { category ->
                        CategoryTile(
                            category = category,
                            titleStyle = titleStyle,
                            isLastUsed = category == lastCategory,
                            onClick = { onOpen(category) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Number of tile columns: two on typical phones, one more for every further [TileMinWidth]. */
internal fun categoryColumns(gridWidth: Dp): Int = max(2, (gridWidth / TileMinWidth).toInt())

/** Width of a tile's title column for a grid [gridWidth] wide with [columns] columns. */
internal fun categoryTitleWidth(gridWidth: Dp, columns: Int): Dp {
    val tileWidth = (gridWidth - FluentDimens.gutter * 2 - TileGap * (columns - 1)) / columns
    val chrome = LastUsedBarWidth + TilePaddingHorizontal * 2 + TileIconSize + TileIconGap
    return (tileWidth - chrome).coerceAtLeast(0.dp)
}

/** Distinct whitespace-separated words of [titles]; a title may wrap between them but never inside one. */
internal fun titleWords(titles: Iterable<String>): List<String> =
    titles.flatMap { it.split(WhitespaceRegex) }.filter { it.isNotEmpty() }.distinct()

private val WhitespaceRegex = Regex("\\s+")

/**
 * Subtitle style shared by all tiles, shrunk (down to [TitleMinFontSize]) until the widest word of
 * any title fits [titleWidth] on one line. Without it "Temperature" breaks into "Temperatur" / "e"
 * on 360 dp phones at font scale 1.15 and above. One size for every tile keeps the grid uniform.
 */
@Composable
private fun fittedTitleStyle(titles: List<String>, titleWidth: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val base = FluentTheme.typography.subtitle
    val widthPx = with(LocalDensity.current) { titleWidth.toPx().toInt() } - TitleWidthSlackPx
    // The measurer is re-created when density or font scale change, so it is a key too.
    return remember(measurer, titles, base, widthPx) {
        if (widthPx <= 0) base else fitStyle(measurer, titleWords(titles), base, widthPx, TitleMinFontSize)
    }
}

/** Flat bordered tile: line icon, category name and a few unit symbols; accent bar when last used. */
@Composable
private fun CategoryTile(
    category: UnitCategory,
    titleStyle: TextStyle,
    isLastUsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FluentTheme.colors
    val shape = RoundedCornerShape(FluentDimens.cornerSmall)
    // No-break spaces inside symbols ("fl oz") and before each dot, and a word joiner after '/'
    // ("ft/s"), so a line only breaks after "· ".
    val symbols = UnitCatalog.units(category).joinToString("\u00A0· ") {
        it.symbol.replace(' ', '\u00A0').replace("/", "/\u2060")
    }
    val lastUsedText = stringResource(R.string.conv_last_used)
    Row(
        modifier
            .heightIn(min = TileMinHeight)
            .clip(shape)
            .background(colors.surface)
            .border(FluentDimens.hairline, colors.border, shape)
            .flatClickable(onClick = onClick)
            .drawWithContent {
                drawContent()
                if (isLastUsed) drawRect(colors.accent, size = Size(LastUsedBarWidth.toPx(), size.height))
            }
            .semantics { if (isLastUsed) stateDescription = lastUsedText }
            .padding(
                start = LastUsedBarWidth + TilePaddingHorizontal,
                end = TilePaddingHorizontal,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(category.glyph, contentDescription = null, tint = colors.accentText, size = TileIconSize)
        Spacer(Modifier.width(TileIconGap))
        Column(Modifier.weight(1f)) {
            FluentText(
                stringResource(category.titleRes),
                style = titleStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FluentText(
                symbols,
                modifier = Modifier.padding(top = 2.dp),
                style = FluentTheme.typography.caption,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
