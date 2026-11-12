package com.scalc.calculator.exchange.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scalc.calculator.common.NumericKey
import com.scalc.calculator.exchange.data.ExchangeCache
import com.scalc.calculator.exchange.data.ExchangeCacheSerializer
import com.scalc.calculator.exchange.data.ExchangeRepository
import com.scalc.calculator.exchange.data.FakeRatesClient
import com.scalc.calculator.exchange.data.FetchResult
import com.scalc.calculator.exchange.data.InMemoryDataStore
import com.scalc.calculator.exchange.model.Currency
import com.scalc.calculator.exchange.model.RateSourceKind
import com.scalc.calculator.exchange.model.RateStatusInfo
import com.scalc.calculator.exchange.model.RateTable
import com.scalc.calculator.exchange.model.SampleRates
import com.scalc.calculator.exchange.model.StatusKind
import com.scalc.calculator.exchange.model.TestStrings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.util.Locale
import java.util.TimeZone

/**
 * [ExchangeViewModel] on a real [ExchangeRepository] (in-memory DataStore, fake rates client).
 *
 * The repository runs on the [TestScope] and the ViewModel on the test Main dispatcher, both on
 * one virtual-time scheduler. While the screen state is observed, the ViewModel ticks once a
 * minute forever, so tests drive time with runCurrent/advanceTimeBy (never advanceUntilIdle).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeViewModelTest {

    private val t0 = 1_794_000_000_000L
    private val minute = 60L * 1000L
    private val hour = 60L * minute
    private val day = 24L * hour

    private val store = InMemoryDataStore(ExchangeCache())
    private val client = FakeRatesClient()
    private val viewModels = ViewModelStore()
    private var created = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    /** Wall clock that follows virtual time, starting at [t0]. */
    private fun TestScope.clock(): () -> Long = { t0 + testScheduler.currentTime }

    private fun TestScope.newRepository(): ExchangeRepository = ExchangeRepository(store, client, this, clock())

    /**
     * Creates the ViewModel through a [ViewModelStore] (so it can be cleared), observes its state
     * like the screen does unless [observe] is false and, unless [load] is false, lets the cache load.
     */
    private fun TestScope.newViewModel(
        repository: ExchangeRepository = newRepository(),
        load: Boolean = true,
        observe: Boolean = true,
    ): ExchangeViewModel {
        val factory = viewModelFactory { initializer { ExchangeViewModel(repository, clock()) } }
        val viewModel = ViewModelProvider(viewModels, factory)["vm${created++}", ExchangeViewModel::class.java]
        if (observe) observe(viewModel)
        if (load) runCurrent()
        return viewModel
    }

    /** Observes the screen state like the composed screen does, until the returned job is cancelled. */
    private fun TestScope.observe(viewModel: ExchangeViewModel): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

    private fun TestScope.newViewModelWithRates(): ExchangeViewModel =
        newViewModel().also {
            it.refresh()
            runCurrent()
        }

    private fun TestScope.collectOutcomes(viewModel: ExchangeViewModel): List<RefreshOutcome> {
        val outcomes = mutableListOf<RefreshOutcome>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.refreshOutcomes.toList(outcomes) }
        return outcomes
    }

    private fun ExchangeViewModel.press(vararg keys: NumericKey) = keys.forEach(::onKey)

    private val ExchangeViewModel.ui: ExchangeUiState get() = state.value

    private val english = ExchangeLocalization(Locale.ENGLISH, TestStrings.ENGLISH)
    private val german = ExchangeLocalization(Locale.GERMAN, TestStrings.GERMAN)

    /** The texts the screen shows for the current state in [localization] (UTC times). */
    private fun ExchangeViewModel.texts(localization: ExchangeLocalization = english): LocalizedExchangeTexts =
        ui.localize(localization, TimeZone.getTimeZone("UTC"))

    // --- Initial state and status --------------------------------------------------------------

    @Test
    fun loadingStateBeforeTheCacheIsRead() = runTest {
        val viewModel = newViewModel(load = false)
        assertFalse(viewModel.ui.loaded)
        assertEquals(StatusKind.Refreshing, viewModel.ui.status.kind)

        runCurrent()
        assertTrue(viewModel.ui.loaded)
    }

    @Test
    fun withoutCacheRatesAreUnavailableAndSelectorsShowPersistedCodes() = runTest {
        val viewModel = newViewModel()
        val ui = viewModel.ui

        assertEquals(StatusKind.Unavailable, ui.status.kind)
        assertFalse(ui.ratesAvailable)
        assertTrue(ui.currencies.isEmpty())
        assertEquals("1", ui.amountText)
        assertEquals(ExchangeFormatting.UNAVAILABLE, ui.convertedText)
        assertFalse(ui.conversionAvailable)
        assertNull(ui.unitRateText)
        assertNull(ui.inverseRateText)
        assertEquals(Currency("EUR", "Euro"), ui.from)
        assertEquals("USD", ui.to.code)
        assertEquals(0, client.calls)

        // Nothing can be selected without rates.
        viewModel.selectFrom("GBP")
        runCurrent()
        assertEquals("EUR", viewModel.ui.from.code)
    }

    @Test
    fun statusIsFreshAfterRefreshAndCachedAfterAFailedRefresh() = runTest {
        val viewModel = newViewModelWithRates()
        assertEquals(StatusKind.Fresh, viewModel.ui.status.kind)
        assertTrue(viewModel.texts().status.text.startsWith("Rates last updated: "))
        assertTrue(viewModel.ui.ratesAvailable)

        client.handler = { throw IOException("offline") }
        advanceTimeBy(hour)
        viewModel.refresh()
        runCurrent()

        val status = viewModel.texts().status
        assertEquals(StatusKind.Cached, status.kind)
        assertTrue(status.text.startsWith("Using cached rates"))
        assertTrue(status.detail!!.endsWith(TestStrings.ENGLISH.updateFailed))
        // Cached rates keep converting.
        assertEquals("1.11", viewModel.ui.convertedText)
    }

    @Test
    fun failedRefreshWithoutCacheShowsTheReason() = runTest {
        client.handler = { throw IOException("offline") }
        val viewModel = newViewModel()
        viewModel.refresh()
        runCurrent()

        assertEquals(StatusKind.Unavailable, viewModel.ui.status.kind)
        assertEquals("No internet connection.", viewModel.texts().status.detail)
        assertEquals(ExchangeFormatting.UNAVAILABLE, viewModel.ui.convertedText)
    }

    @Test
    fun refreshingFlagPassesThrough() = runTest {
        val gate = CompletableDeferred<FetchResult>()
        client.handler = { gate.await() }
        val viewModel = newViewModel()

        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.ui.refreshing)
        assertEquals(StatusKind.Refreshing, viewModel.ui.status.kind)

        gate.complete(FetchResult.Downloaded(SampleRates.table(), "\"v1\""))
        runCurrent()
        assertFalse(viewModel.ui.refreshing)
        assertEquals(StatusKind.Fresh, viewModel.ui.status.kind)
    }

    @Test
    fun freshTurnsStaleAfter24HoursWhileOpenWithoutNetworkAccess() = runTest {
        val viewModel = newViewModelWithRates()
        assertEquals(1, client.calls)

        advanceTimeBy(day - minute)
        runCurrent()
        assertEquals(StatusKind.Fresh, viewModel.ui.status.kind)

        advanceTimeBy(minute)
        runCurrent()
        assertEquals(StatusKind.Stale, viewModel.ui.status.kind)
        assertEquals(1, client.calls)
    }

    // --- Automatic conversion ------------------------------------------------------------------

    @Test
    fun typingUpdatesTheConvertedValueAutomatically() = runTest {
        val viewModel = newViewModelWithRates()
        assertEquals("1", viewModel.ui.amountText)
        assertEquals("1.11", viewModel.ui.convertedText)
        assertTrue(viewModel.ui.conversionAvailable)

        // The initial "1" is only a default: the first typed digit replaces it.
        viewModel.press(NumericKey.D1, NumericKey.D2)
        assertEquals("12", viewModel.ui.amountText)
        assertEquals("13.35", viewModel.ui.convertedText)

        viewModel.press(NumericKey.D5, NumericKey.DoubleZero, NumericKey.Decimal, NumericKey.D5)
        assertEquals("12,500.5", viewModel.ui.amountText)
        assertEquals("13,909.31", viewModel.ui.convertedText)

        viewModel.press(NumericKey.Backspace, NumericKey.Backspace)
        assertEquals("12,500", viewModel.ui.amountText)
        assertEquals("13,908.75", viewModel.ui.convertedText)

        viewModel.press(NumericKey.Clear)
        assertEquals("0", viewModel.ui.amountText)
        assertEquals("0.00", viewModel.ui.convertedText)
    }

    @Test
    fun firstKeyReplacesTheDefaultAmount() = runTest {
        val viewModel = newViewModelWithRates()
        assertEquals("1", viewModel.ui.amountText)
        viewModel.press(NumericKey.D2, NumericKey.D5, NumericKey.D0)
        assertEquals("250", viewModel.ui.amountText)

        val decimalFirst = newViewModelWithRates()
        decimalFirst.press(NumericKey.Decimal, NumericKey.D5)
        assertEquals("0.5", decimalFirst.ui.amountText)

        val backspaceFirst = newViewModelWithRates()
        backspaceFirst.press(NumericKey.Backspace)
        assertEquals("0", backspaceFirst.ui.amountText)
    }

    @Test
    fun signToggleIsIgnored() = runTest {
        val viewModel = newViewModelWithRates()
        viewModel.press(NumericKey.ToggleSign)
        assertEquals("1", viewModel.ui.amountText)
        assertEquals("1.11", viewModel.ui.convertedText)
    }

    @Test
    fun unitRatesInBothDirections() = runTest {
        val viewModel = newViewModelWithRates()
        assertEquals("1 EUR = 1.1127 USD", viewModel.ui.unitRateText)
        assertEquals("1 USD = 0.898715 EUR", viewModel.ui.inverseRateText)
    }

    @Test
    fun sourceChangeRecomputes() = runTest {
        val viewModel = newViewModelWithRates()
        viewModel.selectFrom("GBP")
        runCurrent()

        val ui = viewModel.ui
        assertEquals(Currency("GBP", "British Pound"), ui.from)
        assertEquals("USD", ui.to.code)
        assertEquals("1.33", ui.convertedText)
        assertEquals("1 GBP = 1.33069 USD", ui.unitRateText)
        assertEquals("GBP", store.value.selectedFrom)
    }

    @Test
    fun destinationChangeRecomputes() = runTest {
        val viewModel = newViewModelWithRates()
        viewModel.press(NumericKey.D1, NumericKey.D0)
        viewModel.selectTo("CZK")
        runCurrent()

        val ui = viewModel.ui
        assertEquals("EUR", ui.from.code)
        assertEquals(Currency("CZK", "Czech Koruna"), ui.to)
        assertEquals("243.78", ui.convertedText)
        assertEquals("1 EUR = 24.378 CZK", ui.unitRateText)
        assertEquals("CZK", store.value.selectedTo)
    }

    @Test
    fun swapRecomputesAndKeepsTheAmount() = runTest {
        val viewModel = newViewModelWithRates()
        viewModel.press(NumericKey.D1, NumericKey.D0, NumericKey.D0)
        assertEquals("111.27", viewModel.ui.convertedText)

        viewModel.swap()
        runCurrent()
        val ui = viewModel.ui
        assertEquals("USD", ui.from.code)
        assertEquals("EUR", ui.to.code)
        assertEquals("100", ui.amountText)
        assertEquals("89.87", ui.convertedText)
        assertEquals("1 USD = 0.898715 EUR", ui.unitRateText)
        assertEquals("1 EUR = 1.1127 USD", ui.inverseRateText)
        assertEquals("USD" to "EUR", store.value.selectedFrom to store.value.selectedTo)
    }

    @Test
    fun smallResultsKeepSignificantDigits() = runTest {
        val viewModel = newViewModelWithRates()
        viewModel.selectFrom("JPY")
        viewModel.selectTo("EUR")
        runCurrent()
        // 1 / 177.18 = 0.0056439...
        assertEquals("0.005644", viewModel.ui.convertedText)
    }

    @Test
    fun selectingTheOtherSidesCurrencySwaps() = runTest {
        val viewModel = newViewModelWithRates()

        viewModel.selectFrom("USD")
        runCurrent()
        assertEquals("USD" to "EUR", viewModel.ui.from.code to viewModel.ui.to.code)

        viewModel.selectTo("USD")
        runCurrent()
        assertEquals("EUR" to "USD", viewModel.ui.from.code to viewModel.ui.to.code)

        // Re-selecting the current currency changes nothing.
        viewModel.selectFrom("EUR")
        runCurrent()
        assertEquals("EUR" to "USD", viewModel.ui.from.code to viewModel.ui.to.code)
    }

    @Test
    fun unknownCodesAreIgnored() = runTest {
        val viewModel = newViewModelWithRates()
        viewModel.selectFrom("XYZ")
        viewModel.selectTo("")
        runCurrent()
        assertEquals("EUR" to "USD", viewModel.ui.from.code to viewModel.ui.to.code)
    }

    @Test
    fun amountSurvivesWhenTheScreenStopsObserving() = runTest {
        val viewModel = newViewModel(observe = false)
        val firstVisit = observe(viewModel)
        viewModel.press(NumericKey.D1, NumericKey.D7)

        // Switching tabs disposes the screen, which only stops observing the activity-scoped ViewModel.
        firstVisit.cancel()
        advanceTimeBy(10 * minute)
        observe(viewModel)
        assertEquals("17", viewModel.ui.amountText)
    }

    // --- Language ------------------------------------------------------------------------------

    @Test
    fun stateHoldsNoTextSoASystemLanguageChangeTakesEffectImmediately() = runTest {
        val viewModel = newViewModelWithRates()
        client.handler = { throw IOException("offline") }
        viewModel.refresh()
        runCurrent()
        val ui = viewModel.ui
        // English names and a status without text: nothing language-dependent is cached.
        assertEquals(Currency("USD", "US Dollar"), ui.to)
        assertEquals(
            RateStatusInfo(StatusKind.Cached, fetchedAtMillis = t0, rateDate = SampleRates.RATE_DATE, source = RateSourceKind.FRANKFURTER_BLENDED, updateFailed = true),
            ui.status,
        )

        val before = viewModel.texts(english)
        assertEquals("Euro", before.fromName)
        assertEquals("US Dollar", before.toName)
        assertEquals("Using cached rates · Last updated: 6 Nov 2026, 21:20", before.status.text)
        assertEquals("Exchange rates of 10 Nov 2026 · Update failed. Swipe down to retry.", before.status.detail)
        assertEquals("Source: central bank and other official rates blended by frankfurter.dev", before.status.attribution)

        // The system language changes: the activity is recreated, this ViewModel survives and the
        // screen localizes the very same state with the new configuration.
        val after = viewModel.texts(german)
        assertEquals("US-Dollar", after.toName)
        assertEquals("Gespeicherte Kurse · Zuletzt aktualisiert: 6 Nov. 2026, 21:20", after.status.text)
        assertEquals("Wechselkurse vom 10 Nov. 2026 · Aktualisierung fehlgeschlagen.", after.status.detail)
        assertEquals(TestStrings.GERMAN.sourceFrankfurterBlended, after.status.attribution)
        assertEquals("米ドル", viewModel.texts(ExchangeLocalization(Locale.JAPANESE, TestStrings.ENGLISH)).toName)
        // The ViewModel itself did not change.
        assertEquals(ui, viewModel.ui)
    }

    @Test
    fun olderPairRatesCarryTheirOwnDate() = runTest {
        val table = SampleRates.table(rateDate = "2026-11-12", rateDates = mapOf("PLN" to "2026-11-11", "JPY" to "2026-11-10"))
        client.handler = { FetchResult.Downloaded(table, null) }
        val viewModel = newViewModelWithRates()
        // EUR -> USD: both of the newest date, only the status block shows it.
        assertNull(viewModel.ui.pairRateDate)
        assertNull(viewModel.texts().pairRateDate)
        assertEquals("Exchange rates of 12 Nov 2026", viewModel.texts().status.detail)

        viewModel.selectTo("PLN")
        runCurrent()
        assertEquals("2026-11-11", viewModel.ui.pairRateDate)
        assertEquals("11 Nov 2026", viewModel.texts().pairRateDate)
        assertEquals("11 Nov. 2026", viewModel.texts(german).pairRateDate)

        // The older of both dates.
        viewModel.selectFrom("JPY")
        runCurrent()
        assertEquals("2026-11-10", viewModel.ui.pairRateDate)
        // The status block keeps the newest date.
        assertEquals("Exchange rates of 12 Nov 2026", viewModel.texts().status.detail)
    }

    // --- Currency list -------------------------------------------------------------------------

    @Test
    fun commonCurrenciesArePinnedFirst() = runTest {
        val viewModel = newViewModelWithRates()
        assertEquals(
            listOf("EUR", "USD", "GBP", "CZK", "JPY", "CHF", "PLN", "AUD"),
            viewModel.ui.currencies.map { it.code },
        )
        assertEquals("Euro", viewModel.ui.currencies.first().name)
    }

    @Test
    fun onlyPublishedCurrenciesAreListed() = runTest {
        val rates = mapOf(
            "ZAR" to BigDecimal("20.1"),
            "AUD" to BigDecimal("1.6222"),
            "USD" to BigDecimal("1.1127"),
            "BRL" to BigDecimal("6.2"),
            "CHF" to BigDecimal("0.9234"),
        )
        val table = checkNotNull(RateTable.create(rates, "2026-11-10", emptyMap(), RateSourceKind.ECB_DIRECT))
        client.handler = { FetchResult.Downloaded(table, null) }
        val viewModel = newViewModelWithRates()

        assertEquals(listOf("EUR", "USD", "CHF", "AUD", "BRL", "ZAR"), viewModel.ui.currencies.map { it.code })
    }

    // --- Refresh policy ------------------------------------------------------------------------

    @Test
    fun manualRefreshBypassesTheDailyLimitWhileActivationDoesNot() = runTest {
        val viewModel = newViewModel()

        viewModel.onScreenActive()
        runCurrent()
        assertEquals(1, client.calls)
        assertEquals(StatusKind.Fresh, viewModel.ui.status.kind)

        advanceTimeBy(hour)
        viewModel.onScreenActive()
        runCurrent()
        assertEquals(1, client.calls)

        viewModel.refresh()
        runCurrent()
        assertEquals(2, client.calls)

        advanceTimeBy(day)
        viewModel.onScreenActive()
        runCurrent()
        assertEquals(3, client.calls)
    }

    @Test
    fun manualRefreshReportsEveryOutcome() = runTest {
        val viewModel = newViewModel()
        val outcomes = collectOutcomes(viewModel)

        viewModel.refresh()
        runCurrent()
        assertEquals(listOf(RefreshOutcome.Updated), outcomes)

        client.handler = { throw IOException("offline") }
        viewModel.refresh()
        runCurrent()
        // A second, identical failure must be reported again.
        viewModel.refresh()
        runCurrent()
        assertEquals(listOf(RefreshOutcome.Updated, RefreshOutcome.Failed, RefreshOutcome.Failed), outcomes)
    }

    @Test
    fun overlappingManualRefreshesReportOnce() = runTest {
        val gate = CompletableDeferred<FetchResult>()
        client.handler = { gate.await() }
        val viewModel = newViewModel()
        val outcomes = collectOutcomes(viewModel)

        viewModel.refresh()
        runCurrent()
        viewModel.refresh()
        runCurrent()
        assertEquals(1, client.calls)
        assertTrue(outcomes.isEmpty())

        gate.complete(FetchResult.Downloaded(SampleRates.table(), null))
        runCurrent()
        assertEquals(listOf(RefreshOutcome.Updated), outcomes)
    }

    @Test
    fun automaticRefreshProducesNoOutcome() = runTest {
        val viewModel = newViewModel()
        val outcomes = collectOutcomes(viewModel)
        viewModel.onScreenActive()
        runCurrent()
        assertEquals(1, client.calls)
        assertTrue(outcomes.isEmpty())
    }

    // --- Privacy -------------------------------------------------------------------------------

    @Test
    fun theAmountNeverLeavesTheDevice() = runTest {
        val viewModel = newViewModel()
        viewModel.press(NumericKey.Clear, NumericKey.D9, NumericKey.D8, NumericKey.D7, NumericKey.D6, NumericKey.D5)
        viewModel.onScreenActive()
        runCurrent()
        viewModel.refresh()
        runCurrent()

        assertEquals("98,765", viewModel.ui.amountText)
        assertEquals("109,895.82", viewModel.ui.convertedText)
        // The client API only carries the cache validator; nothing else is ever sent.
        assertEquals(listOf(null, "\"v1\""), client.etags)
        // Nor is the amount persisted.
        val bytes = ByteArrayOutputStream().also { ExchangeCacheSerializer.writeTo(store.value, it) }.toByteArray()
        assertFalse(String(bytes, Charsets.UTF_8).contains("98765"))
    }
}
