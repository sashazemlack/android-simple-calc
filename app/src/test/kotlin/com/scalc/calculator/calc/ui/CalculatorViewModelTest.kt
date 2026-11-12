package com.scalc.calculator.calc.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.scalc.calculator.calc.engine.AngleMode
import com.scalc.calculator.calc.engine.CalcError
import com.scalc.calculator.calc.history.HistoryData
import com.scalc.calculator.calc.history.HistoryEntry
import com.scalc.calculator.calc.history.HistoryRepository
import com.scalc.calculator.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var settingsStore: InMemoryDataStore<Preferences>
    private lateinit var settings: SettingsRepository
    private lateinit var historyStore: InMemoryDataStore<HistoryData>
    private lateinit var history: HistoryRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        settingsStore = InMemoryDataStore(emptyPreferences())
        settings = SettingsRepository(settingsStore)
        historyStore = InMemoryDataStore(HistoryData())
        history = HistoryRepository(historyStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(
        evalDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler),
        settingsRepository: SettingsRepository = settings,
        savedState: SavedStateHandle = SavedStateHandle(),
    ): CalculatorViewModel =
        CalculatorViewModel(settingsRepository, history, clock = { now }, evalDispatcher = evalDispatcher, savedState = savedState)
            .also { advanceUntilIdle() }

    /** Types keys as on the keypad: digits . + − × ÷ ^ % ( ) ! π e and '=' (sin( etc. via [CalculatorViewModel.function]). */
    private fun CalculatorViewModel.type(keys: String) {
        keys.forEach { c ->
            when (c) {
                'e', 'π' -> constant(c)
                else -> press(requireNotNull(HardwareKeys.forCharacter(c)) { "no key for '$c'" })
            }
        }
    }

    private fun TestScope.collectEvents(vm: CalculatorViewModel): List<CalculatorEvent> {
        val events = mutableListOf<CalculatorEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        return events
    }

    private val CalculatorViewModel.ui get() = state.value

    // ------------------------------------------------------------------ live result

    @Test
    fun livePreviewUpdatesWhileTypingWithoutEquals() = runTest {
        val vm = newViewModel()
        vm.type("125+25×2")
        assertEquals("125+25×2", vm.ui.expression)
        assertEquals("175", vm.ui.preview)
        assertNull(vm.ui.previewError)
        assertFalse(vm.ui.justEvaluated)
        assertTrue(historyStore.current.entries.isEmpty())
    }

    @Test
    fun previewIsComputedOnTheEvalDispatcherAndLatestInputWins() = runTest {
        val vm = newViewModel(evalDispatcher = StandardTestDispatcher(testScheduler))
        vm.type("2+3")
        assertEquals("2+3", vm.ui.expression)
        assertNull("evaluation runs on the eval dispatcher, not inline", vm.ui.preview)
        vm.type("×4")
        advanceUntilIdle()
        assertEquals("2+3×4", vm.ui.expression)
        assertEquals("14", vm.ui.preview)
    }

    @Test
    fun incompleteExpressionsShowNoPreview() = runTest {
        val vm = newViewModel()
        vm.type("5+")
        assertNull(vm.ui.preview)
        assertNull(vm.ui.previewError)
        vm.clear()
        vm.function("sin")
        assertEquals("sin(", vm.ui.expression)
        assertNull(vm.ui.preview)
        assertNull(vm.ui.previewError)
        vm.clear()
        vm.type("(")
        assertNull(vm.ui.preview)
    }

    @Test
    fun singlePlainNumberShowsNoPreviewButConstantsDo() = runTest {
        val vm = newViewModel()
        vm.type("42")
        assertNull(vm.ui.preview)
        vm.toggleSign()
        assertEquals("−42", vm.ui.expression)
        assertNull(vm.ui.preview)
        vm.clear()
        vm.type("π")
        assertEquals("3.14159265358979", vm.ui.preview)
    }

    @Test
    fun divisionByZeroShowsAnErrorPreview() = runTest {
        val vm = newViewModel()
        vm.type("5÷0")
        assertNull(vm.ui.preview)
        assertEquals(CalcError.DivisionByZero, vm.ui.previewError)
        vm.backspace()
        vm.type("2")
        assertEquals("2.5", vm.ui.preview)
        assertNull(vm.ui.previewError)
    }

    @Test
    fun domainAndFactorialErrorsArePreviewed() = runTest {
        val vm = newViewModel()
        vm.function("ln")
        vm.type("0)")
        assertEquals(CalcError.Domain, vm.ui.previewError)
        vm.clear()
        vm.type("0.5!")
        assertEquals(CalcError.InvalidFactorial, vm.ui.previewError)
    }

    // ------------------------------------------------------------------ equals and history

    @Test
    fun equalsSavesExactlyOneEntryAndReplacesTheInput() = runTest {
        now = 1_794_000_000_000L
        val vm = newViewModel()
        vm.type("125+25×2")
        vm.equals()
        advanceUntilIdle()
        assertEquals("175", vm.ui.expression)
        assertTrue(vm.ui.justEvaluated)
        assertNull(vm.ui.preview)
        val expected = listOf(HistoryEntry("125+25×2", "175", 1_794_000_000_000L))
        assertEquals(expected, historyStore.current.entries)
        assertEquals(expected, vm.ui.history)
    }

    @Test
    fun equalsTwiceDoesNotDuplicateHistory() = runTest {
        val vm = newViewModel()
        vm.type("2+3")
        vm.equals()
        vm.equals()
        vm.type("=")
        advanceUntilIdle()
        assertEquals("5", vm.ui.expression)
        assertEquals(1, historyStore.current.entries.size)
    }

    @Test
    fun equalsOnBlankInputDoesNothing() = runTest {
        val vm = newViewModel()
        val events = collectEvents(vm)
        vm.equals()
        advanceUntilIdle()
        assertEquals("", vm.ui.expression)
        assertTrue(historyStore.current.entries.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun digitAfterEqualsStartsANewExpression() = runTest {
        val vm = newViewModel()
        vm.type("2+3=")
        vm.type("7")
        assertEquals("7", vm.ui.expression)
        assertFalse(vm.ui.justEvaluated)
        vm.type("+1")
        assertEquals("8", vm.ui.preview)
    }

    @Test
    fun operatorAfterEqualsContinuesFromTheResult() = runTest {
        now = 10L
        val vm = newViewModel()
        vm.type("2+3=")
        now = 20L
        vm.type("×4")
        assertEquals("5×4", vm.ui.expression)
        assertEquals("20", vm.ui.preview)
        vm.equals()
        advanceUntilIdle()
        assertEquals("20", vm.ui.expression)
        assertEquals(
            listOf(HistoryEntry("5×4", "20", 20L), HistoryEntry("2+3", "5", 10L)),
            vm.ui.history,
        )
    }

    @Test
    fun continuingFromAResultUsesItsExactValue() = runTest {
        val vm = newViewModel()
        vm.type("1÷3=")
        assertEquals("0.333333333333333", vm.ui.expression)
        vm.type("×3")
        assertEquals("1", vm.ui.preview)
        vm.equals()
        assertEquals("1", vm.ui.expression)
    }

    @Test
    fun negatedResultKeepsItsExactValue() = runTest {
        val vm = newViewModel()
        vm.type("1÷3=")
        vm.toggleSign()
        assertEquals("−0.333333333333333", vm.ui.expression)
        assertTrue(vm.ui.justEvaluated)
        vm.type("×3")
        assertEquals("−1", vm.ui.preview)
    }

    @Test
    fun retypedDigitsDoNotReuseTheHiddenExactValue() = runTest {
        val vm = newViewModel()
        vm.type("10÷3=")
        assertEquals("3.33333333333333", vm.ui.expression)
        vm.type("×3")
        assertEquals("exact carried value", "10", vm.ui.preview)

        vm.clear()
        vm.type("10÷3=")
        vm.type("3.33333333333333×3")
        assertEquals("typed digits are taken literally", "9.99999999999999", vm.ui.preview)

        vm.clear()
        vm.type("10÷3=")
        vm.backspace()
        vm.type("3×3")
        assertEquals("an edited result is taken literally", "9.99999999999999", vm.ui.preview)
    }

    @Test
    fun failedEqualsEmitsAnErrorAndKeepsTheExpression() = runTest {
        val vm = newViewModel()
        val events = collectEvents(vm)
        vm.type("5÷0")
        vm.equals()
        vm.clear()
        vm.type("5+")
        vm.equals()
        advanceUntilIdle()
        assertEquals("5+", vm.ui.expression)
        assertFalse(vm.ui.justEvaluated)
        assertEquals(
            listOf(CalculatorEvent.EvaluationFailed(CalcError.DivisionByZero), CalculatorEvent.EvaluationFailed(CalcError.Incomplete)),
            events,
        )
        assertTrue(historyStore.current.entries.isEmpty())
    }

    @Test
    fun equalsWorksBeforeTheBackgroundEvaluationFinished() = runTest {
        val vm = newViewModel(evalDispatcher = StandardTestDispatcher(testScheduler))
        vm.type("6×7")
        vm.equals()
        assertEquals("not evaluated on the calling thread", "6×7", vm.ui.expression)
        vm.equals()
        advanceUntilIdle()
        assertEquals("42", vm.ui.expression)
        assertTrue(vm.ui.justEvaluated)
        assertNull(vm.ui.preview)
        assertEquals("a repeated '=' while evaluating adds nothing", listOf(HistoryEntry("6×7", "42", now)), historyStore.current.entries)
    }

    @Test
    fun pendingEqualsIsDroppedWhenTheInputChanges() = runTest {
        val vm = newViewModel(evalDispatcher = StandardTestDispatcher(testScheduler))
        vm.type("6×7")
        vm.equals()
        vm.type("+1")
        advanceUntilIdle()
        assertEquals("6×7+1", vm.ui.expression)
        assertFalse(vm.ui.justEvaluated)
        assertEquals("43", vm.ui.preview)
        assertTrue(historyStore.current.entries.isEmpty())

        vm.equals()
        advanceUntilIdle()
        assertEquals("43", vm.ui.expression)
        assertEquals(listOf(HistoryEntry("6×7+1", "43", now)), historyStore.current.entries)
    }

    @Test
    fun continuingFromARoundedResultCancelsExactly() = runTest {
        val vm = newViewModel()
        vm.type("1÷3=")
        vm.type("×3=")
        assertEquals("1", vm.ui.expression)
        vm.type("−1")
        assertEquals("0", vm.ui.preview)
        vm.type("=")
        advanceUntilIdle()
        assertEquals("0", vm.ui.expression)
        val newest = vm.ui.history.first()
        assertEquals("1−1", newest.expression)
        assertEquals("0", newest.result)
    }

    @Test
    fun restoringAnEntryComputedFromACarriedResultReproducesItsResult() = runTest {
        val vm = newViewModel()
        vm.type("1÷3=")
        vm.type("×3=")
        val entry = vm.ui.history.first()
        assertEquals("0.333333333333333×3", entry.expression)
        assertEquals("1", entry.result)

        vm.type("7")
        vm.restoreHistoryEntry(entry)
        assertEquals("0.333333333333333×3", vm.ui.expression)
        assertEquals("1", vm.ui.preview)
        vm.equals()
        advanceUntilIdle()
        assertEquals("1", vm.ui.expression)
        assertEquals(3, vm.ui.history.size)
        assertEquals("1", vm.ui.history.first().result)

        vm.clear()
        vm.sqrt()
        vm.type("2=")
        assertEquals("1.4142135623731", vm.ui.expression)
        vm.power()
        vm.type("2=")
        assertEquals("2", vm.ui.expression)
        vm.restoreHistoryEntry(vm.ui.history.first())
        assertEquals("1.4142135623731^2", vm.ui.expression)
        assertEquals("2", vm.ui.preview)
    }

    @Test
    fun entriesWithoutAHiddenExactValueStoreNoCarry() = runTest {
        val vm = newViewModel()
        vm.type("2+3=")
        vm.type("×4=")
        advanceUntilIdle()
        assertTrue(historyStore.current.entries.all { it.carryValue == null })
    }

    @Test
    fun historyRecoversFromATransientReadError() = runTest {
        historyStore.failNextReads = 1
        val vm = newViewModel()
        assertTrue(vm.ui.history.isEmpty())
        vm.type("2+3=")
        advanceTimeBy(1_100)
        advanceUntilIdle()
        assertEquals(1, historyStore.current.entries.size)
        assertEquals(1, vm.ui.history.size)
        vm.type("×2=")
        advanceUntilIdle()
        assertEquals(2, vm.ui.history.size)
    }

    @Test
    fun inputIsRestoredAfterProcessDeath() = runTest {
        val saved = SavedStateHandle(
            mapOf(
                CalculatorViewModel.KEY_EXPRESSION to "125+25×2",
                CalculatorViewModel.KEY_INVERSE to true,
                CalculatorViewModel.KEY_HISTORY_OPEN to true,
            ),
        )
        val vm = newViewModel(savedState = saved)
        assertEquals("125+25×2", vm.ui.expression)
        assertEquals("175", vm.ui.preview)
        assertFalse(vm.ui.justEvaluated)
        assertTrue(vm.ui.inverse)
        assertTrue(vm.ui.historyOpen)
        assertFalse("the scientific keypad is not restored", vm.ui.scientificExpanded)
    }

    @Test
    fun inputIsSavedForProcessDeath() = runTest {
        val saved = SavedStateHandle()
        val vm = newViewModel(savedState = saved)
        vm.type("12+3")
        assertEquals("12+3", saved.get<String>(CalculatorViewModel.KEY_EXPRESSION))
        assertEquals(false, saved.get<Boolean>(CalculatorViewModel.KEY_JUST_EVALUATED))
        vm.type("=")
        vm.toggleInverse()
        vm.openHistory()
        assertEquals("15", saved.get<String>(CalculatorViewModel.KEY_EXPRESSION))
        assertEquals(true, saved.get<Boolean>(CalculatorViewModel.KEY_JUST_EVALUATED))
        assertEquals(true, saved.get<Boolean>(CalculatorViewModel.KEY_INVERSE))
        assertEquals(true, saved.get<Boolean>(CalculatorViewModel.KEY_HISTORY_OPEN))

        val restored = newViewModel(savedState = SavedStateHandle(mapOf(
            CalculatorViewModel.KEY_EXPRESSION to saved.get<String>(CalculatorViewModel.KEY_EXPRESSION),
            CalculatorViewModel.KEY_JUST_EVALUATED to saved.get<Boolean>(CalculatorViewModel.KEY_JUST_EVALUATED),
        )))
        assertEquals("15", restored.ui.expression)
        assertTrue(restored.ui.justEvaluated)
        restored.type("7")
        assertEquals("a digit after a restored result starts over", "7", restored.ui.expression)
    }

    @Test
    fun historyWriteFailureIsReportedWithoutLosingTheResult() = runTest {
        historyStore.failWrites = true
        val vm = newViewModel()
        val events = collectEvents(vm)
        vm.type("2+2=")
        advanceUntilIdle()
        assertEquals("4", vm.ui.expression)
        assertEquals(listOf<CalculatorEvent>(CalculatorEvent.HistoryStorageFailed), events)
    }

    @Test
    fun restoringAHistoryEntryDoesNotAddARecord() = runTest {
        val vm = newViewModel()
        vm.type("125+25×2=")
        vm.type("7")
        vm.openHistory()
        assertTrue(vm.ui.historyOpen)
        val entry = vm.ui.history.single()
        vm.restoreHistoryEntry(entry)
        advanceUntilIdle()
        assertEquals("125+25×2", vm.ui.expression)
        assertFalse(vm.ui.justEvaluated)
        assertFalse(vm.ui.historyOpen)
        assertEquals("175", vm.ui.preview)
        assertEquals(1, historyStore.current.entries.size)
    }

    @Test
    fun clearHistoryRemovesEveryEntry() = runTest {
        val vm = newViewModel()
        vm.type("1+1=")
        vm.type("+1=")
        assertEquals(2, vm.ui.history.size)
        vm.clearHistory()
        advanceUntilIdle()
        assertTrue(vm.ui.history.isEmpty())
        assertTrue(historyStore.current.entries.isEmpty())
        assertEquals("3", vm.ui.expression)
    }

    @Test
    fun historyPanelAndScientificKeypadToggle() = runTest {
        val vm = newViewModel()
        assertFalse(vm.ui.historyOpen)
        assertFalse("scientific keypad is collapsed by default", vm.ui.scientificExpanded)
        vm.toggleHistory()
        assertTrue(vm.ui.historyOpen)
        vm.closeHistory()
        assertFalse(vm.ui.historyOpen)
        vm.openHistory()
        vm.toggleHistory()
        assertFalse(vm.ui.historyOpen)
        vm.toggleScientific()
        assertTrue(vm.ui.scientificExpanded)
        vm.setScientificExpanded(false)
        assertFalse(vm.ui.scientificExpanded)
    }

    // ------------------------------------------------------------------ editing

    @Test
    fun backspaceClearAndToggleSign() = runTest {
        val vm = newViewModel()
        vm.type("12+3")
        vm.backspace()
        assertEquals("12+", vm.ui.expression)
        vm.backspace()
        assertEquals("12", vm.ui.expression)
        vm.type("+5")
        vm.toggleSign()
        assertEquals("12−5", vm.ui.expression)
        assertEquals("7", vm.ui.preview)
        vm.clear()
        assertEquals("", vm.ui.expression)
        assertNull(vm.ui.preview)
    }

    @Test
    fun scientificKeysBuildTheExpression() = runTest {
        val vm = newViewModel()
        vm.sqrt()
        vm.type("16)+3!−2")
        vm.power()
        vm.type("3")
        assertEquals("√(16)+3!−2^3", vm.ui.expression)
        assertEquals("2", vm.ui.preview)

        vm.clear()
        vm.type("200+10")
        vm.percent()
        assertEquals("200+10%", vm.ui.expression)
        assertEquals("220", vm.ui.preview)

        vm.clear()
        vm.openParenthesis()
        vm.type("3")
        vm.closeParenthesis()
        vm.factorial()
        assertEquals("(3)!", vm.ui.expression)
        assertEquals("6", vm.ui.preview)

        vm.clear()
        vm.constant('e')
        assertEquals("2.71828182845905", vm.ui.preview)
    }

    // ------------------------------------------------------------------ inverse and angle mode

    @Test
    fun inverseMapsFunctionsAndStaysOnUntilToggledOff() = runTest {
        val vm = newViewModel()
        vm.toggleInverse()
        assertTrue(vm.ui.inverse)
        vm.function("log")
        assertEquals("10^(", vm.ui.expression)
        vm.type("2)")
        assertEquals("100", vm.ui.preview)
        assertTrue("inverse stays on", vm.ui.inverse)

        vm.clear()
        vm.function("sin")
        assertEquals("asin(", vm.ui.expression)
        vm.clear()
        vm.function("cos")
        vm.clear()
        vm.function("tan")
        assertEquals("atan(", vm.ui.expression)
        vm.clear()
        vm.function("ln")
        assertEquals("e^(", vm.ui.expression)
        vm.clear()
        vm.sqrt()
        assertEquals("inverse does not change the square root", "√(", vm.ui.expression)

        vm.toggleInverse()
        assertFalse(vm.ui.inverse)
        vm.clear()
        vm.function("log")
        assertEquals("log(", vm.ui.expression)
    }

    @Test
    fun angleModeStartsFromTheSavedSetting() = runTest {
        settings.setAngleMode(AngleMode.RAD)
        val vm = newViewModel()
        assertEquals(AngleMode.RAD, vm.ui.angleMode)
        vm.function("sin")
        vm.type("90)")
        assertEquals("0.893996663600558", vm.ui.preview)
    }

    @Test
    fun separateRadAndDegKeysSelectTheirUnitAndPersistIt() = runTest {
        val vm = newViewModel()
        vm.function("sin")
        vm.type("90)")
        assertEquals("1", vm.ui.preview)

        vm.press(CalcKey.Angle(AngleMode.RAD))
        advanceUntilIdle()
        assertEquals(AngleMode.RAD, vm.ui.angleMode)
        assertEquals(AngleMode.RAD, settings.settings.first().angleMode)
        assertEquals("0.893996663600558", vm.ui.preview)

        // Tapping the already active unit keeps it (the keys select, they do not toggle).
        vm.press(CalcKey.Angle(AngleMode.RAD))
        advanceUntilIdle()
        assertEquals(AngleMode.RAD, vm.ui.angleMode)

        vm.press(CalcKey.Angle(AngleMode.DEG))
        advanceUntilIdle()
        assertEquals(AngleMode.DEG, vm.ui.angleMode)
        assertEquals(AngleMode.DEG, settings.settings.first().angleMode)
        assertEquals("1", vm.ui.preview)
    }

    @Test
    fun togglingTheAngleModePersistsItAndRecomputesThePreview() = runTest {
        val vm = newViewModel()
        assertEquals(AngleMode.DEG, vm.ui.angleMode)
        vm.function("sin")
        vm.type("90)")
        assertEquals("1", vm.ui.preview)

        vm.toggleAngleMode()
        advanceUntilIdle()
        assertEquals(AngleMode.RAD, vm.ui.angleMode)
        assertEquals(AngleMode.RAD, settings.settings.first().angleMode)
        assertEquals("0.893996663600558", vm.ui.preview)

        vm.toggleAngleMode()
        advanceUntilIdle()
        assertEquals(AngleMode.DEG, vm.ui.angleMode)
        assertEquals(AngleMode.DEG, settings.settings.first().angleMode)
        assertEquals("1", vm.ui.preview)
    }

    @Test
    fun angleModeSurvivesARestartWithAFileBackedSettingsStore() = runTest {
        val file = File(temporaryFolder.root, "settings.preferences_pb")
        val firstJob = Job()
        val firstRepository = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + firstJob), produceFile = { file }),
        )
        val first = newViewModel(settingsRepository = firstRepository)
        first.toggleAngleMode()
        assertEquals(AngleMode.RAD, firstRepository.settings.first { it.angleMode == AngleMode.RAD }.angleMode)
        firstJob.cancelAndJoin()

        val secondJob = Job()
        try {
            val secondRepository = SettingsRepository(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + secondJob), produceFile = { file }),
            )
            assertEquals(AngleMode.RAD, secondRepository.settings.first().angleMode)
            val restarted = newViewModel(settingsRepository = secondRepository)
            restarted.state.first { it.angleMode == AngleMode.RAD }
        } finally {
            secondJob.cancelAndJoin()
        }
    }

    // ------------------------------------------------------------------ clipboard

    @Test
    fun pasteReplacesTheExpression() = runTest {
        val vm = newViewModel()
        vm.type("12")
        assertEquals(PasteOutcome.Pasted, vm.paste(" 3 * (1.5 + 0.5) "))
        assertEquals("3×(1.5+0.5)", vm.ui.expression)
        assertEquals("6", vm.ui.preview)
    }

    @Test
    fun pasteAppendsWhenTheExpressionWaitsForAnOperand() = runTest {
        val vm = newViewModel()
        vm.type("5+")
        assertEquals(PasteOutcome.Pasted, vm.paste("2*3"))
        assertEquals("5+2×3", vm.ui.expression)
        assertEquals("11", vm.ui.preview)

        vm.clear()
        vm.function("sin")
        assertEquals(PasteOutcome.Pasted, vm.paste("30"))
        assertEquals("sin(30", vm.ui.expression)
        assertEquals("0.5", vm.ui.preview)

        vm.clear()
        vm.type("2×(")
        assertEquals(PasteOutcome.Pasted, vm.paste("1-4"))
        assertEquals("2×(1−4", vm.ui.expression)
        assertEquals("−6", vm.ui.preview)
    }

    @Test
    fun pasteReplacesAResultShownAfterEquals() = runTest {
        val vm = newViewModel()
        vm.type("2+3=")
        assertEquals(PasteOutcome.Pasted, vm.paste("7"))
        assertEquals("7", vm.ui.expression)
        assertFalse(vm.ui.justEvaluated)
    }

    @Test
    fun pasteLongerThanTheEditorLimitIsRejected() = runTest {
        val vm = newViewModel()
        vm.type("12")
        val tooLong = "1+".repeat(100) + "1"
        assertEquals(201, tooLong.length)
        assertEquals(PasteOutcome.TooLong, vm.paste(tooLong))
        assertEquals("12", vm.ui.expression)

        val longest = "1+".repeat(99) + "12"
        assertEquals(200, longest.length)
        assertEquals(PasteOutcome.Pasted, vm.paste(longest))
        assertEquals(longest, vm.ui.expression)

        vm.clear()
        vm.type("1+".repeat(60))
        assertEquals(120, vm.ui.expression.length)
        val appended = "1+".repeat(59) + "1"
        assertEquals(PasteOutcome.TooLong, vm.paste(appended))
        assertEquals(120, vm.ui.expression.length)
        assertEquals(PasteOutcome.Pasted, vm.paste("1+".repeat(39) + "1"))
        assertEquals(199, vm.ui.expression.length)
    }

    @Test
    fun pasteOnlyChecksTheSyntaxOnTheCallingThread() = runTest {
        val vm = newViewModel(evalDispatcher = StandardTestDispatcher(testScheduler))
        val text = "sin(−1E-99999)".repeat(14)
        repeat(3) {
            vm.clear()
            vm.paste(text)
        }
        vm.clear()
        val start = System.nanoTime()
        val outcome = vm.paste(text)
        val millis = (System.nanoTime() - start) / 1e6
        assertEquals(PasteOutcome.Pasted, outcome)
        assertNull("the preview is computed on the eval dispatcher", vm.ui.preview)
        assertTrue("paste took $millis ms", millis < 50.0)
        advanceUntilIdle()
        assertEquals("0", vm.ui.preview)
    }

    @Test
    fun pasteRejectsEmptyAndInvalidClipboardText() = runTest {
        val vm = newViewModel()
        vm.type("12")
        assertEquals(PasteOutcome.Empty, vm.paste(null))
        assertEquals(PasteOutcome.Empty, vm.paste(""))
        assertEquals(PasteOutcome.Empty, vm.paste("  \n "))
        assertEquals(PasteOutcome.Invalid, vm.paste("hello world"))
        assertEquals(PasteOutcome.Invalid, vm.paste("5+×3"))
        assertEquals(PasteOutcome.Invalid, vm.paste("1+".repeat(600)))
        assertEquals("12", vm.ui.expression)
    }

    @Test
    fun copyTextVariants() = runTest {
        val vm = newViewModel()
        assertNull("nothing typed", vm.copyText())
        vm.type("5+")
        assertNull("incomplete", vm.copyText())
        vm.clear()
        vm.type("5÷0")
        assertNull("error", vm.copyText())
        vm.clear()
        vm.type("2−5")
        assertEquals("live value with ASCII minus", "-3", vm.copyText())
        vm.clear()
        vm.type("1234×1000")
        assertEquals("no digit grouping", "1234000", vm.copyText())
        vm.clear()
        vm.type("42")
        assertEquals("42", vm.copyText())
        vm.clear()
        vm.type("2−5=")
        assertEquals("result after =", "-3", vm.copyText())
        vm.type("1÷3=")
        assertEquals("0.333333333333333", vm.copyText())
    }

    @Test
    fun copyTextIsAvailableBeforeTheBackgroundEvaluationFinished() = runTest {
        val vm = newViewModel(evalDispatcher = StandardTestDispatcher(testScheduler))
        vm.type("10÷4")
        assertNull("not evaluated yet", vm.ui.preview)
        assertEquals("2.5", vm.copyText())
    }
}
