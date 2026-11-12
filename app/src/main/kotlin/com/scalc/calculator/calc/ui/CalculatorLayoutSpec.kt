package com.scalc.calculator.calc.ui

import kotlin.math.roundToInt

/**
 * Fixed pixel sizes of the calculator screen parts (see [CalculatorLayoutSpec]).
 *
 * @property actionRow height of the action row including its separators.
 * @property handle height of the scientific keypad expander.
 * @property gap keypad gap (also used as the keypad's outer padding).
 * @property rowMin smallest key row height; below it the screen scrolls instead.
 * @property rowComfort key row height the keys keep while the display can still shrink.
 * @property sciRowMax height of a scientific key row when space is plentiful.
 * @property displayNatural display height with the expression at its full font size.
 * @property displayMin compact display height with the expression at its minimum font size.
 */
internal data class CalculatorDimensions(
    val actionRow: Int,
    val handle: Int,
    val gap: Int,
    val rowMin: Int,
    val rowComfort: Int,
    val sciRowMax: Int,
    val displayNatural: Int,
    val displayMin: Int,
)

/** Heights in pixels for the display and the keypad area (keypads or history panel). */
internal data class CalculatorSplit(val display: Int, val keypadArea: Int)

/**
 * Vertical space distribution of the calculator for one viewport height.
 *
 * The scientific keypad has [scientificFull] pixels when fully expanded: three rows of up to
 * `sciRowMax`, shrinking towards `rowMin` on short screens so that all eight key rows stay
 * similar when space is tight. The keypad area contains the expander handle, the scientific
 * keypad scaled by the expansion fraction, and the five standard rows that fill the rest.
 *
 * [split] gives space away in this order, so the display shrinks before the keys do:
 * 1. Display at its natural height, keypad area fills the rest, as long as the standard rows
 *    keep at least `rowComfort`.
 * 2. Otherwise the display shrinks (down to `displayMin`) to keep the rows at `rowComfort`.
 * 3. Then the rows shrink down to `rowMin`.
 * 4. If even that does not fit, everything keeps its minimum and the screen scrolls.
 */
internal class CalculatorLayoutSpec(viewportHeight: Int, private val dims: CalculatorDimensions) {

    /** Space for display and keypad area together. */
    private val available = (viewportHeight - dims.actionRow).coerceAtLeast(0)

    private val scientificRow: Int =
        ((available - dims.displayMin - dims.handle - 9 * dims.gap) / 8).coerceIn(dims.rowMin, dims.sciRowMax)

    /** Height of the fully expanded scientific keypad (three rows plus their gaps). */
    val scientificFull: Int = 3 * scientificRow + 3 * dims.gap

    private val standardMin = 5 * dims.rowMin + 6 * dims.gap
    private val standardComfort = 5 * dims.rowComfort + 6 * dims.gap

    /** Visible scientific keypad height at expansion [fraction] (0 = collapsed, 1 = expanded). */
    fun scientificHeight(fraction: Float): Int = (scientificFull * fraction.coerceIn(0f, 1f)).roundToInt()

    fun split(fraction: Float): CalculatorSplit {
        val fixed = dims.handle + scientificHeight(fraction)
        val keypadMin = fixed + standardMin
        val keypadComfort = fixed + standardComfort
        return when {
            available - dims.displayNatural >= keypadComfort ->
                CalculatorSplit(dims.displayNatural, available - dims.displayNatural)
            available - dims.displayMin >= keypadComfort ->
                CalculatorSplit(available - keypadComfort, keypadComfort)
            available - dims.displayMin >= keypadMin ->
                CalculatorSplit(dims.displayMin, available - dims.displayMin)
            else -> CalculatorSplit(dims.displayMin, keypadMin)
        }
    }

    /** Total content height for [fraction]; larger than the viewport only when it must scroll. */
    fun contentHeight(fraction: Float): Int = split(fraction).let { dims.actionRow + it.display + it.keypadArea }
}

/**
 * When the calculator switches to its compact landscape layout: a phone turned sideways, where
 * the scientific keys sit permanently beside the standard keypad instead of above it.
 */
internal object CalculatorLandscape {

    /** Viewports (below the tab strip) at least this tall keep the portrait layout, e.g. tablets. */
    const val MAX_HEIGHT_DP = 600f

    /** Scientific key columns beside the standard keypad (3 x 5, one row per standard row). */
    const val SCIENTIFIC_COLUMNS = 3

    /** Standard keypad columns. */
    const val STANDARD_COLUMNS = 4

    fun applies(widthDp: Float, heightDp: Float): Boolean = widthDp > heightDp && heightDp < MAX_HEIGHT_DP
}

/**
 * Vertical split of the compact landscape layout: a band with the action buttons and the display
 * on top, the keypads (five rows) below a 1 px [separator].
 *
 * The keys keep `rowComfort` rows while the band can stay at least `displayMin` (and at least the
 * action buttons' height) tall; spare height goes to the band only up to [bandMax], the rest to
 * the keys. If even minimum sizes do not fit, both keep their minimum and the screen scrolls.
 */
internal class LandscapeLayoutSpec(
    viewportHeight: Int,
    dims: CalculatorDimensions,
    separator: Int,
    bandMax: Int,
) {
    private val keypadComfort = 5 * dims.rowComfort + 6 * dims.gap
    private val keypadMin = 5 * dims.rowMin + 6 * dims.gap
    private val bandMin = maxOf(dims.displayMin, dims.actionRow)

    /** Height of the band holding the action buttons and the display. */
    val band: Int = (viewportHeight - separator - keypadComfort).coerceIn(bandMin, maxOf(bandMin, bandMax))

    /** Height of the keypad area (keypads or history panel). */
    val keypad: Int = maxOf(viewportHeight - separator - band, keypadMin)
}
