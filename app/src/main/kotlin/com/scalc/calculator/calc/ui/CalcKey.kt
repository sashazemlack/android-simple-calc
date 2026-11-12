package com.scalc.calculator.calc.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

/** A calculator key, shared by the on-screen keypads and the hardware keyboard. */
internal sealed interface CalcKey {
    data class Digit(val digit: Char) : CalcKey
    data object Decimal : CalcKey

    /** '+', '−', '×', '÷' or '^'. */
    data class Operator(val symbol: Char) : CalcKey

    /** sin, cos, tan, log or ln; the ViewModel applies inverse mode. */
    data class ScientificFunction(val name: String) : CalcKey

    /** 'π' or 'e'. */
    data class Constant(val symbol: Char) : CalcKey
    data object OpenParenthesis : CalcKey
    data object CloseParenthesis : CalcKey
    data object SquareRoot : CalcKey
    data object Factorial : CalcKey
    data object Percent : CalcKey
    data object ToggleSign : CalcKey
    data object Backspace : CalcKey
    data object Clear : CalcKey
    data object Equals : CalcKey
    data object Inverse : CalcKey

    /** Selects degrees or radians (the RAD and DEG keys). */
    data class Angle(val mode: com.scalc.calculator.calc.engine.AngleMode) : CalcKey
}

/** Routes a [key] to the matching [CalculatorViewModel] action. */
internal fun CalculatorViewModel.press(key: CalcKey) {
    when (key) {
        is CalcKey.Digit -> digit(key.digit)
        CalcKey.Decimal -> decimal()
        is CalcKey.Operator -> operator(key.symbol)
        is CalcKey.ScientificFunction -> function(key.name)
        is CalcKey.Constant -> constant(key.symbol)
        CalcKey.OpenParenthesis -> openParenthesis()
        CalcKey.CloseParenthesis -> closeParenthesis()
        CalcKey.SquareRoot -> sqrt()
        CalcKey.Factorial -> factorial()
        CalcKey.Percent -> percent()
        CalcKey.ToggleSign -> toggleSign()
        CalcKey.Backspace -> backspace()
        CalcKey.Clear -> clear()
        CalcKey.Equals -> equals()
        CalcKey.Inverse -> toggleInverse()
        is CalcKey.Angle -> setAngleMode(key.mode)
    }
}

/**
 * Hardware keyboard input: digits, '.', ',', + - * / ^ ( ) % ! and '=' by the character they
 * produce (so Shift and the numeric keypad work), Enter for '=', Backspace, Escape/Delete to
 * clear, and Ctrl+C / Ctrl+V for copy and paste.
 */
internal object HardwareKeys {

    fun forCharacter(c: Char): CalcKey? = when (c) {
        in '0'..'9' -> CalcKey.Digit(c)
        '.', ',' -> CalcKey.Decimal
        '+' -> CalcKey.Operator('+')
        '-', '−' -> CalcKey.Operator('−')
        '*', '×' -> CalcKey.Operator('×')
        '/', '÷' -> CalcKey.Operator('÷')
        '^' -> CalcKey.Operator('^')
        '%' -> CalcKey.Percent
        '!' -> CalcKey.Factorial
        '(' -> CalcKey.OpenParenthesis
        ')' -> CalcKey.CloseParenthesis
        '=' -> CalcKey.Equals
        'π' -> CalcKey.Constant('π')
        '√' -> CalcKey.SquareRoot
        else -> null
    }

    /** Handles a key-down [event]; returns true when it was consumed. */
    fun handle(event: KeyEvent, onKey: (CalcKey) -> Unit, onCopy: () -> Unit, onPaste: () -> Unit): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (event.isCtrlPressed || event.isMetaPressed) {
            return when (event.key) {
                Key.C -> true.also { onCopy() }
                Key.V -> true.also { onPaste() }
                else -> false
            }
        }
        if (event.isAltPressed) return false
        val special = when (event.key) {
            Key.Enter, Key.NumPadEnter -> CalcKey.Equals
            Key.Backspace -> CalcKey.Backspace
            Key.Delete, Key.Escape -> CalcKey.Clear
            else -> null
        }
        val key = special ?: event.utf16CodePoint.takeIf { it > 0 }?.let { forCharacter(it.toChar()) } ?: return false
        onKey(key)
        return true
    }
}
