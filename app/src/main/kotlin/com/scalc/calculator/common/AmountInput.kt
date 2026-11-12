package com.scalc.calculator.common

import androidx.compose.runtime.Immutable
import java.math.BigDecimal

/** Keys of the shared on-screen numeric keypad (unit converter, currency converter). */
enum class NumericKey(val digit: Char?) {
    D0('0'), D1('1'), D2('2'), D3('3'), D4('4'), D5('5'), D6('6'), D7('7'), D8('8'), D9('9'),
    DoubleZero(null), Decimal(null), ToggleSign(null), Backspace(null), Clear(null);

    companion object {
        fun digit(d: Int): NumericKey = entries[d]
    }
}

/**
 * Immutable amount being typed on a numeric keypad. [text] is always a plain ASCII decimal
 * ("0", "-12.5", "3.") without grouping, so it can be parsed and persisted directly.
 */
@Immutable
data class AmountInput(val text: String = "0") {

    val value: BigDecimal?
        get() = text.toBigDecimalOrNull()

    val isNegative: Boolean get() = text.startsWith("-")

    /** Display form: grouped integer part and a Unicode minus sign. */
    val display: String
        get() = NumberFormatting.groupDigits(text).replace('-', MINUS_SIGN)

    fun press(key: NumericKey): AmountInput = when (key) {
        NumericKey.Clear -> AmountInput("0")
        NumericKey.Backspace -> backspace()
        NumericKey.Decimal -> if ('.' in text) this else copy(text = "$text.")
        NumericKey.ToggleSign -> if (isNegative) copy(text = text.substring(1)) else copy(text = "-$text")
        NumericKey.DoubleZero -> appendDigits("00")
        else -> appendDigits(key.digit!!.toString())
    }

    private fun backspace(): AmountInput {
        val shortened = text.dropLast(1)
        return when (shortened) {
            "", "-" -> AmountInput(if (isNegative && text.length > 2) "-0" else "0")
            else -> AmountInput(shortened)
        }
    }

    private fun appendDigits(digits: String): AmountInput {
        val sign = if (isNegative) "-" else ""
        val unsigned = text.removePrefix("-")
        if (unsigned == "0") {
            // Replace the leading zero; "00" on zero stays zero.
            val trimmed = digits.trimStart('0').ifEmpty { return this }
            return AmountInput(sign + trimmed)
        }
        val digitCount = unsigned.count { it.isDigit() }
        if (digitCount + digits.length > MAX_DIGITS) return this
        return AmountInput(text + digits)
    }

    companion object {
        const val MAX_DIGITS = 15

        /** Restores an input from persisted/plain text, falling back to "0" for anything invalid. */
        fun fromText(raw: String?): AmountInput {
            val t = raw?.trim().orEmpty()
            if (t.isEmpty() || t == "-" || t.toBigDecimalOrNull() == null) return AmountInput("0")
            return AmountInput(t)
        }
    }
}
