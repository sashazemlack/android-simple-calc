package com.scalc.calculator.calc.ui

import androidx.compose.runtime.Immutable
import com.scalc.calculator.calc.engine.AngleMode
import com.scalc.calculator.calc.engine.CalcError
import com.scalc.calculator.calc.history.HistoryEntry

/**
 * Everything the Calculator tab renders.
 *
 * @property expression the expression line in display syntax (Unicode operators, '.' decimal
 *   separator, no digit grouping; grouping is applied only when it is drawn).
 * @property preview formatted live result of [expression], or null when none is shown (empty,
 *   incomplete or malformed input, a single plain number, or right after '=').
 * @property previewError evaluation error shown instead of a live result. Never
 *   [CalcError.Incomplete] or [CalcError.Syntax]: those are normal while typing.
 * @property justEvaluated true right after '=' replaced the expression with its result.
 * @property inverse when on, sin/cos/tan/log/ln insert asin/acos/atan/10^x/e^x.
 * @property scientificExpanded whether the scientific keypad is open (collapsed by default).
 * @property historyOpen whether the history panel replaces the keypads.
 * @property history persisted calculations, newest first.
 */
@Immutable
data class CalculatorUiState(
    val expression: String = "",
    val preview: String? = null,
    val previewError: CalcError? = null,
    val justEvaluated: Boolean = false,
    val angleMode: AngleMode = AngleMode.DEG,
    val inverse: Boolean = false,
    val scientificExpanded: Boolean = false,
    val historyOpen: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
)

/** Result of [CalculatorViewModel.paste]. */
enum class PasteOutcome {
    /** The clipboard expression replaced or extended the current expression. */
    Pasted,

    /** The clipboard had no text. */
    Empty,

    /** The clipboard text is not a calculator expression. */
    Invalid,

    /**
     * The clipboard expression is valid, but the resulting input would exceed the editor's
     * [com.scalc.calculator.calc.engine.ExpressionEditor.MAX_LENGTH] characters; the input is
     * left unchanged.
     */
    TooLong,
}

/** One-shot notifications for the UI (shown as brief feedback, never as persistent state). */
sealed interface CalculatorEvent {
    /** '=' was pressed but the expression has no value. */
    data class EvaluationFailed(val error: CalcError) : CalculatorEvent

    /** Reading or writing the persisted history failed (storage error). */
    data object HistoryStorageFailed : CalculatorEvent
}
