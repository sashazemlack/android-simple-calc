package com.scalc.calculator.converter.engine

import java.math.BigDecimal

/**
 * A unit of measure within a [category].
 *
 * Every unit maps a value to the category's canonical base unit (m, m², m³, m/s, kg, K, W, Pa)
 * with an exact affine relation:
 *
 * ```
 * base = (value + offset) × numerator / denominator
 * ```
 *
 * Linear units have `offset = 0`. The factor is kept as an exact decimal ratio so units whose
 * definition does not terminate in decimal (km/h = 1000/3600 m/s, °F step = 5/9 K) lose no
 * precision; [UnitConverter] performs a single rounded division per conversion.
 *
 * @property key stable identifier, unique across all categories and persisted in the settings.
 * @property name English reference name (the UI shows a localized string resource instead).
 * @property symbol international unit symbol shown next to values, e.g. "m", "ft²", "°C".
 */
data class MeasureUnit(
    val key: String,
    val category: UnitCategory,
    val name: String,
    val symbol: String,
    val numerator: BigDecimal,
    val denominator: BigDecimal = BigDecimal.ONE,
    val offset: BigDecimal = BigDecimal.ZERO,
) {
    init {
        require(numerator.signum() > 0 && denominator.signum() > 0) { "Unit $key needs a positive factor" }
    }
}
