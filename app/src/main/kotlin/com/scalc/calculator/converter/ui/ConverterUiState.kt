package com.scalc.calculator.converter.ui

import androidx.compose.runtime.Immutable
import com.scalc.calculator.common.AmountInput
import com.scalc.calculator.converter.engine.ConversionText
import com.scalc.calculator.converter.engine.MeasureUnit
import com.scalc.calculator.converter.engine.UnitCatalog
import com.scalc.calculator.converter.engine.UnitCategory
import com.scalc.calculator.converter.engine.UnitConverter
import java.math.BigDecimal

/**
 * Input and output of one category's conversion. The derived values are computed when the
 * instance is created, so every change of [amount], [from] or [to] (always made through `copy`)
 * recomputes the result automatically; there is no "equals" step.
 */
@Immutable
data class UnitConversion(
    val category: UnitCategory,
    val amount: AmountInput,
    val from: MeasureUnit,
    val to: MeasureUnit,
) {
    init {
        require(from.category == category && to.category == category) { "Units must belong to $category" }
    }

    private val amountValue: BigDecimal = amount.value ?: BigDecimal.ZERO

    /** Exact converted value (34 significant digits). */
    val result: BigDecimal = UnitConverter.convert(amountValue, from, to)

    /** [result] formatted for display (12 significant digits; integer parts below 10^15 are shown in full; grouped). */
    val resultText: String = ConversionText.value(result)

    /** Rate or formula hint, e.g. "1 m = 3.28084 ft" or "°F = °C × 9/5 + 32". */
    val formula: String = ConversionText.formula(from, to)

    /** True for temperatures below 0 K, which the screen flags as physically impossible. */
    val isBelowAbsoluteZero: Boolean = UnitConverter.isBelowAbsoluteZero(amountValue, from)

    companion object {
        /** Fresh conversion of [category] with amount 0 and the given (or default) units. */
        fun initial(
            category: UnitCategory,
            units: Pair<MeasureUnit, MeasureUnit> = UnitCatalog.defaultPair(category),
        ): UnitConversion = UnitConversion(category, AmountInput(), units.first, units.second)
    }
}

/**
 * Whole unit-converter screen state.
 *
 * @property isLoaded false until the persisted category and units have been read; the screen
 *   renders nothing before that to avoid flashing the grid before a restored conversion.
 * @property openCategory category whose conversion screen is shown, or null for the category grid.
 * @property lastCategory most recently opened category (highlighted in the grid).
 * @property conversions one entry for every [UnitCategory]; amounts live in memory only.
 */
@Immutable
data class ConverterUiState(
    val isLoaded: Boolean = false,
    val openCategory: UnitCategory? = null,
    val lastCategory: UnitCategory? = null,
    val conversions: Map<UnitCategory, UnitConversion> = UnitCategory.entries.associateWith { UnitConversion.initial(it) },
) {
    fun conversion(category: UnitCategory): UnitConversion = conversions.getValue(category)
}
