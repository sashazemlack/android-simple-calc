package com.scalc.calculator.calc.ui

import androidx.annotation.StringRes
import com.scalc.calculator.R
import com.scalc.calculator.calc.engine.CalcError

/** Readable message for an evaluation error, used by the live-result line and a failed '='. */
@StringRes
internal fun CalcError.messageRes(): Int = when (this) {
    CalcError.Incomplete -> R.string.calc_error_incomplete
    CalcError.Syntax -> R.string.calc_error_syntax
    CalcError.DivisionByZero -> R.string.calc_error_division_by_zero
    CalcError.Domain -> R.string.calc_error_domain
    CalcError.InvalidFactorial -> R.string.calc_error_invalid_factorial
    CalcError.Overflow -> R.string.calc_error_overflow
}
