package com.scalc.calculator.exchange.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scalc.calculator.R
import com.scalc.calculator.common.NumericKey
import com.scalc.calculator.ui.components.FeedbackKind
import com.scalc.calculator.ui.components.FillOrScrollColumn
import com.scalc.calculator.ui.components.LocalFeedback
import com.scalc.calculator.ui.components.NumericKeypad
import com.scalc.calculator.ui.components.PickerDialog
import com.scalc.calculator.ui.components.PickerItem
import com.scalc.calculator.ui.components.VerticalSeparator

/** Height of one keypad row when the regular layout has to be compressed (large fonts). */
private val MinKeyRowHeight = 52.dp

/**
 * Height of one keypad row when the compact or side-by-side layout has to be compressed; still
 * above the 44 dp minimum touch target.
 */
private val CompactKeyRowHeight = 48.dp

/** Width share of the fields pane in the side-by-side layout (the keypad pane has 1). */
private const val FIELDS_PANE_WEIGHT = 1.2f

/** Which currency selector opened the picker dialog. */
private enum class CurrencySide { From, To }

/**
 * Exchange Rate tab (Exchange flavor only): converts the typed amount locally with the cached
 * ECB reference rates, shows the rate status directly above the keypad and supports a custom
 * pull-down-to-refresh gesture anywhere on the screen while it is scrolled to the top.
 *
 * The arrangement adapts to the available size ([ExchangeLayout]): stacked fields on tall
 * portrait screens, selectors beside the amounts on short ones, and fields beside the status
 * block and keypad in landscape, so the amounts and the whole keypad stay visible together.
 *
 * State lives in the activity-scoped [ExchangeViewModel], so the amount survives tab switches.
 */
@Composable
fun ExchangeScreen(modifier: Modifier = Modifier) {
    val viewModel: ExchangeViewModel = viewModel(factory = ExchangeViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Scrolls the whole screen (stacked layouts) or the fields pane (side-by-side layout).
    val scrollState = rememberScrollState()
    // Scrolls the status block and keypad pane of the side-by-side layout only.
    val keypadScrollState = rememberScrollState()
    var pickerSide by rememberSaveable { mutableStateOf<CurrencySide?>(null) }

    // Runs when the tab enters composition (the observer receives ON_START immediately while the
    // app is started) and every time the app returns to the foreground with this tab visible.
    // It only applies the 24 h automatic-refresh policy; nothing polls.
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenActive()
        onStopOrDispose { }
    }

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val mode = ExchangeLayout.mode(maxWidth.value, maxHeight.value)
        val pullState = rememberPullRefreshState(
            // Side by side, both panes scroll independently and both must be at their top. The
            // keypad pane's state is ignored otherwise (it may keep a stale value after rotation).
            canStartPull = {
                scrollState.value == 0 &&
                    (mode != ExchangeLayoutMode.SideBySide || keypadScrollState.value == 0)
            },
            onRefresh = viewModel::refresh,
        )
        RefreshOutcomeFeedback(viewModel, pullState)

        val fields: @Composable (compact: Boolean) -> Unit = { compact ->
            ExchangeFields(
                state = state,
                onPickFrom = { pickerSide = CurrencySide.From },
                onPickTo = { pickerSide = CurrencySide.To },
                onSwap = viewModel::swap,
                compact = compact,
            )
        }
        val status: @Composable () -> Unit = {
            RateStatusBlock(status = state.status, onRefresh = viewModel::refresh)
        }
        val keypad: @Composable () -> Unit = {
            NumericKeypad(onKey = viewModel::onKey, bottomLeftKey = NumericKey.DoubleZero)
        }

        Box(Modifier.fillMaxSize().nestedScroll(pullState.nestedScrollConnection)) {
            when (mode) {
                ExchangeLayoutMode.Regular, ExchangeLayoutMode.Compact -> {
                    val compact = mode == ExchangeLayoutMode.Compact
                    // The scroll container spans the whole screen including the keypad, so a
                    // downward swipe anywhere reaches the pull-to-refresh connection; taps on keys
                    // work as usual.
                    FillOrScrollColumn(
                        minBottomHeight = (if (compact) CompactKeyRowHeight else MinKeyRowHeight) * 4,
                        modifier = Modifier.fillMaxSize(),
                        scrollState = scrollState,
                        top = {
                            Column(Modifier.fillMaxWidth()) {
                                fields(compact)
                                status()
                            }
                        },
                        bottom = keypad,
                    )
                }
                ExchangeLayoutMode.SideBySide -> Row(Modifier.fillMaxSize()) {
                    // fillMaxHeight before verticalScroll: the whole pane is the scroll container,
                    // so a pull also starts below short content.
                    Column(
                        Modifier
                            .weight(FIELDS_PANE_WEIGHT)
                            .fillMaxHeight()
                            .verticalScroll(scrollState),
                    ) {
                        fields(true)
                    }
                    VerticalSeparator()
                    // The status block stays directly above the keypad.
                    FillOrScrollColumn(
                        minBottomHeight = CompactKeyRowHeight * 4,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        scrollState = keypadScrollState,
                        top = status,
                        bottom = keypad,
                    )
                }
            }
            PullRefreshIndicator(pullState, Modifier.align(Alignment.TopCenter))
        }
    }

    pickerSide?.takeIf { state.ratesAvailable }?.let { side ->
        CurrencyPicker(
            title = stringResource(if (side == CurrencySide.From) R.string.exch_pick_from else R.string.exch_pick_to),
            state = state,
            selectedCode = if (side == CurrencySide.From) state.from.code else state.to.code,
            onSelect = { code ->
                if (side == CurrencySide.From) viewModel.selectFrom(code) else viewModel.selectTo(code)
                pickerSide = null
            },
            onDismiss = { pickerSide = null },
        )
    }
}

/**
 * Shows brief feedback after a manual refresh and releases the parked pull indicator. Outcomes are
 * only delivered while this screen is composed.
 */
@Composable
private fun RefreshOutcomeFeedback(viewModel: ExchangeViewModel, pullState: PullRefreshState) {
    val feedback = LocalFeedback.current
    val updatedText by rememberUpdatedState(stringResource(R.string.exch_feedback_updated))
    val failedText by rememberUpdatedState(stringResource(R.string.exch_feedback_failed))
    LaunchedEffect(viewModel, pullState, feedback) {
        viewModel.refreshOutcomes.collect { outcome ->
            pullState.endRefresh()
            when (outcome) {
                RefreshOutcome.Updated -> feedback.show(updatedText, FeedbackKind.Success)
                RefreshOutcome.Failed -> feedback.show(failedText, FeedbackKind.Error)
            }
        }
    }
}

/** Searchable currency list: code as title, name as subtitle, common currencies first. */
@Composable
private fun CurrencyPicker(
    title: String,
    state: ExchangeUiState,
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = remember(state.currencies) {
        state.currencies.map { PickerItem(key = it.code, title = it.code, subtitle = it.name) }
    }
    PickerDialog(
        title = title,
        items = items,
        selectedKey = selectedCode,
        onSelect = { item -> onSelect(item.key) },
        onDismiss = onDismiss,
        searchable = true,
        searchPlaceholder = stringResource(R.string.exch_search_placeholder),
        emptyText = stringResource(R.string.exch_search_empty),
    )
}
