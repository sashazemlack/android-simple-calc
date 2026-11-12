package com.scalc.calculator.exchange.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scalc.calculator.common.AmountInput
import com.scalc.calculator.common.NumericKey
import com.scalc.calculator.exchange.data.ExchangeModule
import com.scalc.calculator.exchange.data.ExchangeRepository
import com.scalc.calculator.exchange.model.Currency
import com.scalc.calculator.exchange.model.CurrencyMath
import com.scalc.calculator.exchange.model.CurrencyNames
import com.scalc.calculator.exchange.model.CurrencySelection
import com.scalc.calculator.exchange.model.ExchangeState
import com.scalc.calculator.exchange.model.RateStatusFormatter
import com.scalc.calculator.exchange.model.RateTable
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal

/**
 * Activity-scoped state holder of the Exchange Rate tab (the tab leaves composition when another
 * tab is selected, so the typed amount must outlive it).
 *
 * - The typed amount lives in memory only. It is never persisted and never handed to the
 *   [repository]: every conversion is computed locally from the cached [RateTable].
 * - Rates, refresh state and the selected pair come from the process-wide [repository].
 * - [state] is recomputed on every amount, pair or table change, and on a once-per-minute tick
 *   while the screen observes it, so "Fresh" turns into "Stale" after 24 h without any network
 *   request. Ticking stops a few seconds after the screen stops observing.
 * - [state] holds no language-dependent text (see [ExchangeUiState]); this ViewModel outlives a
 *   change of the system language, so the screen renders texts with the current resources.
 *
 * @param clock wall clock used to evaluate the rate status.
 */
class ExchangeViewModel(
    private val repository: ExchangeRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val amount = MutableStateFlow(AmountInput(DEFAULT_AMOUNT))

    /** Serializes selection changes so each one sees the pair written by the previous one. */
    private val selectionMutex = Mutex()

    /** Waits for the result of the running manual refresh; null or completed when none is pending. */
    private var manualRefresh: Job? = null

    /** Picker order is derived once per table instance instead of on every amount change. */
    private var sortedFor: RateTable? = null
    private var sortedCurrencies: List<Currency> = emptyList()

    private val outcomes = MutableSharedFlow<RefreshOutcome>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * One event per finished manual refresh. Not replayed: an outcome that happens while the
     * screen is not observing is dropped (the status block still shows the resulting state).
     */
    val refreshOutcomes: SharedFlow<RefreshOutcome> = outcomes.asSharedFlow()

    val state: StateFlow<ExchangeUiState> =
        combine(repository.state, amount, statusTicks()) { exchange, input, _ -> buildState(exchange, input) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = buildState(repository.state.value, amount.value),
            )

    /**
     * True while the amount still shows the initial [DEFAULT_AMOUNT]. That default is only a
     * starting example: the first typed key replaces it ("1" then "2", "5", "0" gives 250, not 1250).
     */
    private var amountIsDefault = true

    /** Applies a keypad key to the amount. Currency amounts are never negative, so sign toggling is ignored. */
    fun onKey(key: NumericKey) {
        if (key == NumericKey.ToggleSign) return
        val replaceDefault = amountIsDefault
        amountIsDefault = false
        amount.update { current -> (if (replaceDefault) AmountInput() else current).press(key) }
    }

    /**
     * Sets the source currency. Picking the current destination swaps the pair instead of
     * producing an identity conversion. Ignored while no rates are available.
     */
    fun selectFrom(code: String) {
        changeSelection(code) { from, to, picked ->
            when (picked) {
                from -> null
                to -> to to from
                else -> picked to to
            }
        }
    }

    /** Sets the destination currency; picking the current source swaps the pair (see [selectFrom]). */
    fun selectTo(code: String) {
        changeSelection(code) { from, to, picked ->
            when (picked) {
                to -> null
                from -> to to from
                else -> from to picked
            }
        }
    }

    /** Exchanges source and destination currency; the typed amount is kept. */
    fun swap() {
        viewModelScope.launch { selectionMutex.withLock { repository.swap() } }
    }

    /**
     * The screen became visible (tab selected or app back in the foreground). Only applies the
     * 24 h automatic-refresh policy; it never polls.
     */
    fun onScreenActive() {
        repository.onScreenActive()
    }

    /**
     * Manual refresh (pull-to-refresh): downloads immediately, bypassing the 24 h policy, and
     * reports the result once through [refreshOutcomes]. While a refresh is already running, its
     * result is reported instead of starting another download.
     */
    fun refresh() {
        // Sets ExchangeState.refreshing synchronously, so the wait below cannot see a stale "idle".
        repository.refreshNow()
        if (manualRefresh?.isActive == true) return
        manualRefresh = viewModelScope.launch {
            val finished = repository.state.first { !it.refreshing }
            outcomes.tryEmit(if (finished.lastAttemptFailed) RefreshOutcome.Failed else RefreshOutcome.Updated)
        }
    }

    /**
     * Normalizes [code] against the cached table and stores the pair chosen by [choose]
     * (null = no change). Without rates nothing can be selected.
     */
    private fun changeSelection(
        code: String,
        choose: (from: String, to: String, picked: String) -> Pair<String, String>?,
    ) {
        viewModelScope.launch {
            selectionMutex.withLock {
                val current = repository.state.value
                val table = current.table ?: return@withLock
                val picked = CurrencySelection.normalize(code, table) ?: return@withLock
                val (from, to) = choose(current.selectedFrom, current.selectedTo, picked) ?: return@withLock
                repository.setSelection(from, to)
            }
        }
    }

    private fun buildState(exchange: ExchangeState, input: AmountInput): ExchangeUiState {
        val table = exchange.table
        val from = currencyOf(exchange.selectedFrom, table)
        val to = currencyOf(exchange.selectedTo, table)
        val converted = table?.let { CurrencyMath.convert(input.value ?: BigDecimal.ZERO, from.code, to.code, it) }
        return ExchangeUiState(
            loaded = exchange.loaded,
            amountText = input.display,
            from = from,
            to = to,
            convertedText = converted?.let(ExchangeFormatting::convertedAmount) ?: ExchangeFormatting.UNAVAILABLE,
            conversionAvailable = converted != null,
            unitRateText = table?.let { CurrencyMath.unitRateText(from.code, to.code, it) },
            inverseRateText = table?.takeIf { from.code != to.code }?.let { CurrencyMath.unitRateText(to.code, from.code, it) },
            pairRateDate = table?.pairRateDate(from.code, to.code)?.takeIf { it < table.rateDate },
            status = RateStatusFormatter.describe(exchange, clock()),
            refreshing = exchange.refreshing,
            currencies = currenciesOf(table),
        )
    }

    private fun currenciesOf(table: RateTable?): List<Currency> {
        if (table == null) return emptyList()
        if (table !== sortedFor) {
            sortedCurrencies = CurrencyOrdering.sort(table.currencies)
            sortedFor = table
        }
        return sortedCurrencies
    }

    /** Emits immediately and then once per [STATUS_TICK_MILLIS]; only runs while [state] is observed. */
    private fun statusTicks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(STATUS_TICK_MILLIS)
        }
    }

    companion object {
        /** Amount shown when the tab is opened for the first time in this process. */
        const val DEFAULT_AMOUNT = "1"

        /** Re-evaluation interval of the rate status (local only, never touches the network). */
        const val STATUS_TICK_MILLIS = 60_000L

        /** Keeps the state pipeline alive briefly across configuration changes. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * Persisted (or default) [code] as a [Currency]: from the table when possible, otherwise
         * with the platform's English name, so selectors stay meaningful without rates.
         */
        internal fun currencyOf(code: String, table: RateTable?): Currency =
            table?.currencyOf(code) ?: Currency(code, CurrencyNames.englishName(code))

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                    "ExchangeViewModel requires the Application in its creation extras"
                }
                ExchangeViewModel(repository = ExchangeModule.repository(application))
            }
        }
    }
}
