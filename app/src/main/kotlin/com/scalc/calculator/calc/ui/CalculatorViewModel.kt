package com.scalc.calculator.calc.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scalc.calculator.appContainer
import com.scalc.calculator.calc.engine.AngleMode
import com.scalc.calculator.calc.engine.BigMath
import com.scalc.calculator.calc.engine.CalcError
import com.scalc.calculator.calc.engine.CalculatorEngine
import com.scalc.calculator.calc.engine.Carry
import com.scalc.calculator.calc.engine.EditorState
import com.scalc.calculator.calc.engine.EvalResult
import com.scalc.calculator.calc.engine.ExpressionEditor
import com.scalc.calculator.calc.history.HistoryEntry
import com.scalc.calculator.calc.history.HistoryRepository
import com.scalc.calculator.common.MINUS_SIGN
import com.scalc.calculator.common.NumberFormatting
import com.scalc.calculator.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Activity-scoped state holder of the Calculator tab (the tab leaves composition when another
 * tab is selected, so the expression must outlive it).
 *
 * Key presses are applied synchronously through the pure [ExpressionEditor]. The engine never
 * runs on the main thread: the live result is recomputed on [evalDispatcher] whenever the
 * expression, the angle mode or the carried value changes, and a newer input cancels an older
 * evaluation (latest wins), so typing never waits for the engine. '=' and Copy reuse that
 * evaluation when it is current; otherwise they evaluate on [evalDispatcher] too, and '=' is
 * applied only if the input is still the same when the result arrives. Paste only checks the
 * syntax (tokenizer and parser), which is cheap.
 *
 * Persisted: the angle mode ([SettingsRepository]) and the history ([HistoryRepository]). The
 * expression, inverse mode and the open history panel survive process death through
 * [savedState] ([Factory] always supplies one; null keeps that state in memory only). All public
 * functions must be called on the main thread.
 */
class CalculatorViewModel(
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val evalDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val savedState: SavedStateHandle? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(restoredState())
    val state: StateFlow<CalculatorUiState> = _state.asStateFlow()

    private val _events = Channel<CalculatorEvent>(Channel.BUFFERED)

    /** One-shot events such as a failed '='; each event is delivered to one collector. */
    val events: Flow<CalculatorEvent> = _events.receiveAsFlow()

    /**
     * Exact value of the last result while the expression still starts with it, so that
     * "1÷3 = ×3" yields 1 (see [Carry]). Not part of the UI state; main thread only.
     */
    private var carry: Carry? = null

    /** Set once the user toggled the angle mode, so a late settings read cannot override it. */
    private var angleModeChosen = false

    /** Most recent background evaluation, reused by '=' and copy when it is still current. */
    private var lastEvaluation: Evaluation? = null

    /** '=' waiting for its background evaluation, and the input it evaluates. */
    private var pendingEquals: Job? = null
    private var pendingEqualsRequest: EvalRequest? = null

    init {
        viewModelScope.launch { restoreAngleMode() }
        viewModelScope.launch { history.entries.collect { entries -> _state.update { it.copy(history = entries) } } }
        viewModelScope.launch { computeLivePreview() }
        savedState?.let { handle -> viewModelScope.launch { saveInputState(handle) } }
    }

    // ---------------------------------------------------------------- expression input

    fun digit(d: Char) = edit(startsNewExpression = true) { ExpressionEditor.digit(it, d) }

    fun decimal() = edit(startsNewExpression = true) { ExpressionEditor.decimalPoint(it) }

    /** [op] is one of '+', '−', '×', '÷', '^', '%' (ASCII '-', '*', '/' are accepted too). */
    fun operator(op: Char) = edit(startsNewExpression = false) { ExpressionEditor.operator(it, op) }

    /**
     * Inserts a function: sin, cos, tan, log, ln (mapped to asin, acos, atan, pow10 (10^x) and
     * exp (e^x) while [CalculatorUiState.inverse] is on), or any other [ExpressionEditor.function]
     * name such as asin or sqrt. Inverse mode stays on until it is toggled off.
     */
    fun function(name: String) {
        val effective = if (_state.value.inverse) INVERSE_FUNCTIONS[name] ?: name else name
        edit(startsNewExpression = true) { ExpressionEditor.function(it, effective) }
    }

    /** [c] is 'π' or 'e'. */
    fun constant(c: Char) = edit(startsNewExpression = true) { ExpressionEditor.constant(it, c) }

    fun openParenthesis() = edit(startsNewExpression = true) { ExpressionEditor.openParen(it) }

    fun closeParenthesis() = edit(startsNewExpression = false) { ExpressionEditor.closeParen(it) }

    fun factorial() = edit(startsNewExpression = false) { ExpressionEditor.factorial(it) }

    /** Square root is not affected by inverse mode. */
    fun sqrt() = edit(startsNewExpression = true) { ExpressionEditor.function(it, "sqrt") }

    fun percent() = operator('%')

    fun power() = operator('^')

    fun toggleSign() = edit(startsNewExpression = false) { ExpressionEditor.toggleSign(it) }

    fun backspace() = edit(startsNewExpression = false) { ExpressionEditor.backspace(it) }

    fun clear() {
        carry = null
        _state.update { it.copy(expression = "", justEvaluated = false, preview = null, previewError = null) }
    }

    /**
     * Evaluates the expression. A value is saved to the history and replaces the expression
     * (subsequent operators continue from it, digits start over); an error or an incomplete
     * expression emits [CalculatorEvent.EvaluationFailed] and keeps the input. Blank input and a
     * second '=' on a shown result do nothing, so no duplicate history entries are created.
     *
     * When the live evaluation of the current input is not available yet, the expression is
     * evaluated on [evalDispatcher] and the result is applied only if the input did not change
     * in the meantime; another '=' for the same input while it runs is ignored.
     */
    fun equals() {
        val current = _state.value
        if (current.expression.isBlank() || current.justEvaluated) return
        val request = requestFor(current)
        val cached = lastEvaluation
        if (cached != null && cached.request == request) {
            pendingEquals?.cancel()
            applyEquals(request, cached.result)
            return
        }
        if (pendingEquals?.isActive == true && pendingEqualsRequest == request) return
        pendingEquals?.cancel()
        pendingEqualsRequest = request
        pendingEquals = viewModelScope.launch {
            val result = withContext(evalDispatcher) { request.evaluate() }
            if (requestFor(_state.value) == request) {
                lastEvaluation = Evaluation(request, result)
                applyEquals(request, result)
            }
        }
    }

    // ---------------------------------------------------------------- modes and panels

    fun toggleInverse() = _state.update { it.copy(inverse = !it.inverse) }

    /** Selects degrees or radians (RAD / DEG keys) and persists the choice. */
    fun setAngleMode(mode: AngleMode) {
        angleModeChosen = true
        if (_state.value.angleMode != mode) _state.update { it.copy(angleMode = mode) }
        launchStorage(reportFailure = false) { settings.setAngleMode(mode) }
    }

    /** Switches between degrees and radians and persists the choice. */
    fun toggleAngleMode() = setAngleMode(if (_state.value.angleMode == AngleMode.DEG) AngleMode.RAD else AngleMode.DEG)

    fun setScientificExpanded(expanded: Boolean) = _state.update { it.copy(scientificExpanded = expanded) }

    fun toggleScientific() = _state.update { it.copy(scientificExpanded = !it.scientificExpanded) }

    fun openHistory() = _state.update { it.copy(historyOpen = true) }

    fun closeHistory() = _state.update { it.copy(historyOpen = false) }

    fun toggleHistory() = _state.update { it.copy(historyOpen = !it.historyOpen) }

    /**
     * Puts [entry]'s expression back into the input and returns to the keypad. Adds no history
     * record. When the entry was computed from the exact value of a previous result
     * ([HistoryEntry.carryValue]) that value is carried again, so the restored expression
     * reproduces the stored result.
     */
    fun restoreHistoryEntry(entry: HistoryEntry) {
        carry = entry.carryValue?.toBigDecimalOrNull()
            ?.let { Carry(CalculatorEngine.format(it), it) }
            ?.takeIf { CalculatorRules.continuesFrom(entry.expression, it) }
        _state.update { it.copy(expression = entry.expression, justEvaluated = false, historyOpen = false) }
    }

    fun clearHistory() {
        launchStorage(reportFailure = true) { history.clear() }
    }

    // ---------------------------------------------------------------- clipboard

    /**
     * Inserts clipboard text normalized by [CalculatorEngine.normalizePasted]. It is appended when
     * the expression waits for an operand (ends with a binary operator, '(' or '√', see
     * [CalculatorRules.pasteAppends]) and replaces the expression otherwise, including a result
     * shown after '='. A result longer than the editor's [ExpressionEditor.MAX_LENGTH] is
     * rejected as [PasteOutcome.TooLong], because no key could extend it afterwards. Only the
     * syntax is checked here; the value is computed by the background live evaluation.
     */
    fun paste(clipText: String?): PasteOutcome {
        if (clipText.isNullOrBlank()) return PasteOutcome.Empty
        val normalized = CalculatorEngine.normalizePasted(clipText) ?: return PasteOutcome.Invalid
        val current = _state.value
        val append = CalculatorRules.pasteAppends(current.expression, current.justEvaluated)
        val expression = if (append) current.expression + normalized else normalized
        if (append && !isWellFormed(expression)) return PasteOutcome.Invalid
        if (expression.length > ExpressionEditor.MAX_LENGTH) return PasteOutcome.TooLong
        carry = if (append) carry?.takeIf { CalculatorRules.continuesFrom(expression, it) } else null
        _state.update { it.copy(expression = expression, justEvaluated = false) }
        return PasteOutcome.Pasted
    }

    /**
     * The current value as plain text for other apps: no digit grouping, ASCII '-' for negative
     * numbers, '.' decimal separator. After '=' this is the result, otherwise the live value of
     * the expression (also for a single typed number). Null when there is no value. Suspends
     * on [evalDispatcher] when the live evaluation of the current input is not available yet.
     */
    suspend fun copyText(): String? {
        val current = _state.value
        if (current.expression.isBlank()) return null
        val request = requestFor(current)
        val cached = lastEvaluation
        val result = if (cached != null && cached.request == request) {
            cached.result
        } else {
            withContext(evalDispatcher) { request.evaluate() }
        }
        val value = (result as? EvalResult.Value)?.value ?: return null
        return NumberFormatting.format(value, CalculatorEngine.DISPLAY_DIGITS, negativeSign = "-")
    }

    // ---------------------------------------------------------------- internals

    /**
     * Applies an [ExpressionEditor] transition. [startsNewExpression] marks keys that begin a new
     * expression after '=' (digits, '.', constants, functions, '('); they drop the carried exact
     * value even when the new text happens to start with the same digits.
     */
    private inline fun edit(startsNewExpression: Boolean, transform: (EditorState) -> EditorState) {
        val current = _state.value
        val before = EditorState(current.expression, current.justEvaluated)
        val after = transform(before)
        if (after == before) return
        if (startsNewExpression && before.justEvaluated) carry = null
        carry = carry?.takeIf { CalculatorRules.continuesFrom(after.expression, it) }
        _state.update { it.copy(expression = after.expression, justEvaluated = after.justEvaluated) }
    }

    /** Applies an evaluated '=' for [request] (the input it was computed from). */
    private fun applyEquals(request: EvalRequest, result: EvalResult) {
        pendingEquals = null
        pendingEqualsRequest = null
        when (result) {
            is EvalResult.Value -> {
                val formatted = CalculatorEngine.format(result.value)
                val entry = HistoryEntry(
                    expression = CalculatorRules.closeParentheses(request.expression),
                    result = formatted,
                    timestamp = clock(),
                    carryValue = request.carry?.let(::hiddenExactValue),
                )
                carry = Carry(formatted, result.value)
                _state.update {
                    it.copy(expression = formatted, justEvaluated = true, preview = null, previewError = null)
                }
                launchStorage(reportFailure = true) { history.add(entry) }
            }
            is EvalResult.Error -> _events.trySend(CalculatorEvent.EvaluationFailed(result.error))
        }
    }

    private fun requestFor(state: CalculatorUiState) =
        EvalRequest(state.expression, state.justEvaluated, state.angleMode, carry)

    private suspend fun restoreAngleMode() {
        val saved = settings.settings.first().angleMode
        if (!angleModeChosen) _state.update { it.copy(angleMode = saved) }
    }

    /** Latest-wins live evaluation; results for outdated input are discarded. */
    private suspend fun computeLivePreview() {
        _state
            .map { requestFor(it) }
            .distinctUntilChanged()
            .collectLatest { request ->
                val result = withContext(evalDispatcher) { request.evaluate() }
                lastEvaluation = Evaluation(request, result)
                val preview = CalculatorRules.preview(request.expression, request.justEvaluated, result)
                _state.update { current ->
                    if (request.matches(current)) current.copy(preview = preview.text, previewError = preview.error) else current
                }
            }
    }

    /** Initial state: the input saved before process death, if any. */
    private fun restoredState(): CalculatorUiState {
        val saved = savedState ?: return CalculatorUiState()
        val expression = saved.get<String>(KEY_EXPRESSION)
            ?.takeIf { it.length <= CalculatorEngine.MAX_EXPRESSION_LENGTH }
            .orEmpty()
        return CalculatorUiState(
            expression = expression,
            justEvaluated = expression.isNotEmpty() && saved.get<Boolean>(KEY_JUST_EVALUATED) == true,
            inverse = saved.get<Boolean>(KEY_INVERSE) == true,
            historyOpen = saved.get<Boolean>(KEY_HISTORY_OPEN) == true,
        )
    }

    /**
     * Mirrors the input into [handle]. The carried exact value is not saved: it is matched
     * textually, so a restored result simply continues from its displayed digits.
     */
    private suspend fun saveInputState(handle: SavedStateHandle) {
        _state
            .map { SavedInput(it.expression, it.justEvaluated, it.inverse, it.historyOpen) }
            .distinctUntilChanged()
            .collect { input ->
                handle[KEY_EXPRESSION] = input.expression
                handle[KEY_JUST_EVALUATED] = input.justEvaluated
                handle[KEY_INVERSE] = input.inverse
                handle[KEY_HISTORY_OPEN] = input.historyOpen
            }
    }

    private fun isWellFormed(expression: String): Boolean =
        CalculatorEngine.checkSyntax(expression) != CalcError.Syntax

    /** Runs a storage write; IO failures are reported as an event (or ignored) instead of crashing. */
    private fun launchStorage(reportFailure: Boolean, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: IOException) {
                if (reportFailure) _events.trySend(CalculatorEvent.HistoryStorageFailed)
            }
        }
    }

    private data class EvalRequest(
        val expression: String,
        val justEvaluated: Boolean,
        val angleMode: AngleMode,
        val carry: Carry?,
    ) {
        fun evaluate(): EvalResult = CalculatorEngine.evaluate(expression, angleMode, carry)

        fun matches(state: CalculatorUiState): Boolean =
            state.expression == expression && state.justEvaluated == justEvaluated && state.angleMode == angleMode
    }

    private class Evaluation(val request: EvalRequest, val result: EvalResult)

    private data class SavedInput(
        val expression: String,
        val justEvaluated: Boolean,
        val inverse: Boolean,
        val historyOpen: Boolean,
    )

    companion object {
        /** Functions replaced while inverse mode is on (names understood by [ExpressionEditor.function]). */
        internal val INVERSE_FUNCTIONS = mapOf(
            "sin" to "asin",
            "cos" to "acos",
            "tan" to "atan",
            "log" to "pow10",
            "ln" to "exp",
        )

        internal const val KEY_EXPRESSION = "calc.expression"
        internal const val KEY_JUST_EVALUATED = "calc.justEvaluated"
        internal const val KEY_INVERSE = "calc.inverse"
        internal const val KEY_HISTORY_OPEN = "calc.historyOpen"

        /**
         * The exact value behind a carried result when it differs from the digits shown
         * (rounded to the engine's 34 digits so the history record stays small), else null.
         */
        private fun hiddenExactValue(carry: Carry): String? {
            val shown = carry.text.replace(MINUS_SIGN, '-').toBigDecimalOrNull()
            if (shown != null && shown.compareTo(carry.value) == 0) return null
            return carry.value.round(BigMath.MC).toString()
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                    "CalculatorViewModel requires the Application in its creation extras"
                }
                val container = application.appContainer
                CalculatorViewModel(
                    container.settingsRepository,
                    container.historyRepository,
                    savedState = createSavedStateHandle(),
                )
            }
        }
    }
}
