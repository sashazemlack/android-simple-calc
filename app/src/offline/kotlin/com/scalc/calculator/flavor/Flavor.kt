package com.scalc.calculator.flavor

import androidx.annotation.StringRes
import com.scalc.calculator.AppTab
import com.scalc.calculator.R
import com.scalc.calculator.calc.ui.CalculatorScreen
import com.scalc.calculator.converter.ui.ConverterScreen

/**
 * Flavor root for "Calculator Offline". This source set has no exchange-rate code, resources,
 * dependencies or permissions; the shared shell only sees the tabs listed here.
 */
object Flavor {
    val tabs: List<AppTab> = listOf(
        AppTab(key = "calculator", titleRes = R.string.tab_calculator) { CalculatorScreen() },
        AppTab(key = "converter", titleRes = R.string.tab_converter) { ConverterScreen() },
    )

    @StringRes
    val aboutTextRes: Int = R.string.about_flavor
}
