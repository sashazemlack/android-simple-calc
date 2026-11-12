package com.scalc.calculator.flavor

import com.scalc.calculator.AppTab
import com.scalc.calculator.R
import com.scalc.calculator.calc.ui.CalculatorScreen
import com.scalc.calculator.converter.ui.ConverterScreen
import com.scalc.calculator.exchange.ui.ExchangeScreen

/**
 * Flavor root for "Calculator Exchange": the shared tabs plus the Exchange Rate tab,
 * which exists only in this source set.
 */
object Flavor {
    val tabs: List<AppTab> = listOf(
        AppTab(key = "calculator", titleRes = R.string.tab_calculator) { CalculatorScreen() },
        AppTab(key = "exchange", titleRes = R.string.tab_exchange) { ExchangeScreen() },
        AppTab(key = "converter", titleRes = R.string.tab_converter) { ConverterScreen() },
    )
}
