package com.scalc.calculator.converter.engine

/**
 * Unit-converter categories in display order. [key] is the stable identifier persisted in the
 * settings; [displayName] is the English reference name (the UI shows a localized string resource).
 */
enum class UnitCategory(val key: String, val displayName: String) {
    LENGTH("length", "Length"),
    AREA("area", "Area"),
    VOLUME("volume", "Volume"),
    SPEED("speed", "Speed"),
    MASS("mass", "Weight / Mass"),
    TEMPERATURE("temperature", "Temperature"),
    POWER("power", "Power"),
    PRESSURE("pressure", "Pressure"),
    ;

    /**
     * Whether negative amounts are meaningful. Only temperatures can be negative (−40 °C);
     * a negative length, mass or pressure amount is not a valid input for this converter.
     */
    val allowsNegative: Boolean get() = this == TEMPERATURE

    companion object {
        /** Category for a persisted [key], or null when the key is missing or unknown. */
        fun fromKey(key: String?): UnitCategory? = entries.firstOrNull { it.key == key }
    }
}
