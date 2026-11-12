package com.scalc.calculator.calc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scalc.calculator.R
import com.scalc.calculator.calc.engine.CalcError
import com.scalc.calculator.ui.components.FeedbackKind
import com.scalc.calculator.ui.components.FeedbackState
import com.scalc.calculator.ui.components.LocalFeedback
import kotlinx.coroutines.launch

/** Cross-fade between keypads and history panel. */
private const val PANEL_FADE_MILLIS = 180

/**
 * Calculator tab (both flavors): display with live result, History / Paste / Copy row, the
 * expandable scientific keypad and the standard keypad, or the history panel in their place.
 * State lives in the activity-scoped [CalculatorViewModel], so it survives tab switches.
 *
 * The calculator is always laid out left to right, like any calculator: in RTL locales the
 * keypads keep the operator column on the right and expressions are not reordered.
 */
@Composable
fun CalculatorScreen(modifier: Modifier = Modifier) {
    val viewModel: CalculatorViewModel = viewModel(factory = CalculatorViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current
    val context = LocalContext.current
    val clipboard = remember(context) { ClipboardAccess(context) }
    val texts = feedbackTexts()
    val scope = rememberCoroutineScope()

    CalculatorEventFeedback(viewModel, feedback, texts)
    BackHandler(enabled = state.historyOpen, onBack = viewModel::closeHistory)

    val onKey: (CalcKey) -> Unit = { key -> viewModel.press(key) }
    val onCopy: () -> Unit = {
        // The value may still have to be computed (off the main thread) when Copy follows a key.
        scope.launch {
            val text = viewModel.copyText()
            when {
                text == null -> feedback.show(texts.nothingToCopy, FeedbackKind.Error)
                clipboard.writeText(texts.clipLabel, text) -> feedback.show(texts.copied, FeedbackKind.Success)
                else -> feedback.show(texts.clipboardUnavailable, FeedbackKind.Error)
            }
        }
    }
    val onPaste: () -> Unit = {
        when (viewModel.paste(clipboard.readText())) {
            PasteOutcome.Pasted -> feedback.show(texts.pasted, FeedbackKind.Success)
            PasteOutcome.Empty -> feedback.show(texts.clipboardEmpty, FeedbackKind.Error)
            PasteOutcome.Invalid -> feedback.show(texts.clipboardInvalid, FeedbackKind.Error)
            PasteOutcome.TooLong -> feedback.show(texts.clipboardTooLong, FeedbackKind.Error)
        }
    }
    val onClearHistory: () -> Unit = {
        viewModel.clearHistory()
        feedback.show(texts.historyCleared, FeedbackKind.Info)
    }

    val panel = remember { ScientificPanelState(state.scientificExpanded) }
    LaunchedEffect(panel, state.scientificExpanded) { panel.animateTo(state.scientificExpanded) }

    val errorMessage = state.previewError?.let { texts.errors.getValue(it) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        CalculatorLayout(
            dimensions = rememberCalculatorDimensions(),
            scientificFraction = { panel.fraction },
            display = {
                // The display is the keyboard focus target, so hardware keys work without touching
                // a key first; it also reads as one element (expression + result) to TalkBack. Right
                // after '=' it is a polite live region, so the result replacing the expression is
                // announced; while typing it is not, so keystroke previews stay silent.
                val focusRequester = remember { FocusRequester() }
                val announceResult = state.justEvaluated
                CalculatorDisplay(
                    expression = state.expression,
                    preview = state.preview,
                    errorMessage = errorMessage,
                    angleMode = state.angleMode,
                    inverse = state.inverse,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .focusable()
                        .semantics(mergeDescendants = true) {
                            if (announceResult) liveRegion = LiveRegionMode.Polite
                        },
                )
                LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
            },
            actionRow = {
                ActionRow(
                    historyOpen = state.historyOpen,
                    onToggleHistory = viewModel::toggleHistory,
                    onPaste = onPaste,
                    onCopy = onCopy,
                )
            },
            keypadArea = { spec ->
                Crossfade(
                    targetState = state.historyOpen,
                    modifier = Modifier.fillMaxSize(),
                    animationSpec = tween(PANEL_FADE_MILLIS),
                    label = "calculatorKeypadArea",
                ) { historyOpen ->
                    if (historyOpen) {
                        HistoryPanel(
                            entries = state.history,
                            onRestore = viewModel::restoreHistoryEntry,
                            onClear = onClearHistory,
                            onClose = viewModel::closeHistory,
                        )
                    } else {
                        Keypads(
                            state = state,
                            panel = panel,
                            scientificFullPx = spec.scientificFull,
                            onKey = onKey,
                            onToggleScientific = viewModel::toggleScientific,
                            onScientificSettled = viewModel::setScientificExpanded,
                        )
                    }
                }
            },
            modifier = modifier
                .fillMaxSize()
                .onKeyEvent { event -> HardwareKeys.handle(event, onKey, onCopy, onPaste) },
        )
    }
}

/** Scientific keypad (animated height), the expander handle below it and the standard keypad. */
@Composable
private fun Keypads(
    state: CalculatorUiState,
    panel: ScientificPanelState,
    scientificFullPx: Int,
    onKey: (CalcKey) -> Unit,
    onToggleScientific: () -> Unit,
    onScientificSettled: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // The handle sits below the scientific keypad, so it travels down with the panel's bottom
        // edge while the panel opens and always stays directly above the standard keypad.
        ScientificSlot(panel = panel, fullHeightPx = scientificFullPx) {
            ScientificKeypad(inverse = state.inverse, angleMode = state.angleMode, onKey = onKey)
        }
        ExpanderHandle(
            panel = panel,
            expanded = state.scientificExpanded,
            fullHeightPx = scientificFullPx,
            onToggle = onToggleScientific,
            onSettled = onScientificSettled,
        )
        StandardKeypad(onKey = onKey, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

/** Shows ViewModel events (failed '=', storage errors) as brief feedback while the tab is shown. */
@Composable
private fun CalculatorEventFeedback(viewModel: CalculatorViewModel, feedback: FeedbackState, texts: FeedbackTexts) {
    val currentTexts by rememberUpdatedState(texts)
    LaunchedEffect(viewModel, feedback) {
        viewModel.events.collect { event ->
            when (event) {
                is CalculatorEvent.EvaluationFailed ->
                    feedback.show(currentTexts.errors.getValue(event.error), FeedbackKind.Error)
                CalculatorEvent.HistoryStorageFailed ->
                    feedback.show(currentTexts.storageFailed, FeedbackKind.Error)
            }
        }
    }
}

/** User-visible feedback strings, resolved during composition. */
private class FeedbackTexts(
    val copied: String,
    val nothingToCopy: String,
    val pasted: String,
    val clipboardEmpty: String,
    val clipboardInvalid: String,
    val clipboardTooLong: String,
    val clipboardUnavailable: String,
    val clipLabel: String,
    val historyCleared: String,
    val storageFailed: String,
    val errors: Map<CalcError, String>,
)

@Composable
private fun feedbackTexts(): FeedbackTexts = FeedbackTexts(
    copied = stringResource(R.string.calc_copied),
    nothingToCopy = stringResource(R.string.calc_nothing_to_copy),
    pasted = stringResource(R.string.calc_pasted),
    clipboardEmpty = stringResource(R.string.calc_clipboard_empty),
    clipboardInvalid = stringResource(R.string.calc_clipboard_invalid),
    clipboardTooLong = stringResource(R.string.calc_clipboard_too_long),
    clipboardUnavailable = stringResource(R.string.calc_clipboard_unavailable),
    clipLabel = stringResource(R.string.calc_clip_label),
    historyCleared = stringResource(R.string.calc_history_cleared),
    storageFailed = stringResource(R.string.calc_history_storage_failed),
    errors = CalcError.entries.associateWith { stringResource(it.messageRes()) },
)
