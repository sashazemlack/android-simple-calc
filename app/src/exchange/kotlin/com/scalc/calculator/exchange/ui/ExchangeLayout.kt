package com.scalc.calculator.exchange.ui

/** How the Exchange screen arranges its fields, status block and keypad. */
internal enum class ExchangeLayoutMode {
    /** Stacked fields (selector above each amount), status block, keypad filling the rest. */
    Regular,

    /** Short portrait viewport: each selector sits beside its amount, unit rates share one line. */
    Compact,

    /** Landscape or wide viewport: fields on the left, status block and keypad on the right. */
    SideBySide,
}

/** Chooses the [ExchangeLayoutMode] from the size available to the Exchange screen. */
internal object ExchangeLayout {

    /**
     * Smallest viewport height (dp, below the tab strip) that fits the regular layout: about
     * 407 dp of stacked fields, rate lines and status block plus a 208 dp keypad, with a margin
     * for a two-line status. Shorter portrait viewports use [ExchangeLayoutMode.Compact].
     */
    const val REGULAR_MIN_HEIGHT_DP = 620f

    /** Layout for a viewport of [widthDp] x [heightDp]. */
    fun mode(widthDp: Float, heightDp: Float): ExchangeLayoutMode = when {
        widthDp > heightDp -> ExchangeLayoutMode.SideBySide
        heightDp < REGULAR_MIN_HEIGHT_DP -> ExchangeLayoutMode.Compact
        else -> ExchangeLayoutMode.Regular
    }
}
