package com.scalc.calculator.converter.ui

import com.scalc.calculator.common.NumericKey
import com.scalc.calculator.converter.engine.UnitCategory
import com.scalc.calculator.settings.SettingsRepository
import com.scalc.calculator.settings.UnitSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConverterViewModelTest {

    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = SettingsRepository(InMemoryPreferencesDataStore())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(): ConverterViewModel =
        ConverterViewModel(repository).also { advanceUntilIdle() }

    private fun ConverterViewModel.press(vararg keys: NumericKey) = keys.forEach(::onKey)

    private fun ConverterViewModel.openConversion(): UnitConversion {
        val state = state.value
        return state.conversion(requireNotNull(state.openCategory))
    }

    @Test
    fun firstStartShowsGridWithDefaults() = runTest {
        val vm = newViewModel()
        val state = vm.state.value
        assertTrue(state.isLoaded)
        assertNull(state.openCategory)
        assertNull(state.lastCategory)
        assertEquals(UnitCategory.entries.toSet(), state.conversions.keys)
        val length = state.conversion(UnitCategory.LENGTH)
        assertEquals("meter" to "foot", length.from.key to length.to.key)
        assertEquals("0", length.amount.text)
        assertEquals("0", length.resultText)
        assertEquals("1 m = 3.28084 ft", length.formula)
    }

    @Test
    fun openingAndClosingACategoryIsPersisted() = runTest {
        val vm = newViewModel()
        vm.openCategory(UnitCategory.SPEED)
        advanceUntilIdle()
        assertEquals(UnitCategory.SPEED, vm.state.value.openCategory)
        assertEquals(UnitCategory.SPEED, vm.state.value.lastCategory)
        repository.settings.first().let {
            assertEquals("speed", it.unitCategory)
            assertTrue(it.unitScreenOpen)
        }

        vm.closeCategory()
        advanceUntilIdle()
        assertNull(vm.state.value.openCategory)
        assertEquals(UnitCategory.SPEED, vm.state.value.lastCategory)
        repository.settings.first().let {
            assertEquals("speed", it.unitCategory)
            assertFalse(it.unitScreenOpen)
        }
    }

    @Test
    fun restoresOpenConversionScreen() = runTest {
        repository.setUnitCategory("pressure", screenOpen = true)
        val vm = newViewModel()
        assertEquals(UnitCategory.PRESSURE, vm.state.value.openCategory)
        assertEquals(UnitCategory.PRESSURE, vm.state.value.lastCategory)
    }

    @Test
    fun restoresGridWithLastUsedCategory() = runTest {
        repository.setUnitCategory("area", screenOpen = false)
        val vm = newViewModel()
        assertNull(vm.state.value.openCategory)
        assertEquals(UnitCategory.AREA, vm.state.value.lastCategory)
    }

    @Test
    fun unitSelectionsArePersistedAndRestored() = runTest {
        val vm = newViewModel()
        vm.openCategory(UnitCategory.LENGTH)
        vm.selectFromUnit("inch")
        vm.selectToUnit("centimeter")
        advanceUntilIdle()
        assertEquals(UnitSelection("inch", "centimeter"), repository.unitSelection("length").first())

        val restored = newViewModel()
        assertEquals(UnitCategory.LENGTH, restored.state.value.openCategory)
        val length = restored.openConversion()
        assertEquals("inch" to "centimeter", length.from.key to length.to.key)
        // Other categories keep their defaults.
        val mass = restored.state.value.conversion(UnitCategory.MASS)
        assertEquals("kilogram" to "pound", mass.from.key to mass.to.key)
    }

    @Test
    fun invalidSavedKeysFallBackToDefaults() = runTest {
        repository.setUnitCategory("currency", screenOpen = true)
        repository.setUnitSelection("mass", UnitSelection("stone", "pound"))
        repository.setUnitSelection("length", UnitSelection("meter", "parsec"))
        repository.setUnitSelection("temperature", UnitSelection("meter", "kelvin"))
        repository.setUnitSelection("power", UnitSelection("watt", "metric_horsepower"))

        val vm = newViewModel()
        val state = vm.state.value
        assertTrue(state.isLoaded)
        assertNull(state.openCategory)
        assertNull(state.lastCategory)
        assertEquals("kilogram" to "pound", state.conversion(UnitCategory.MASS).let { it.from.key to it.to.key })
        assertEquals("meter" to "foot", state.conversion(UnitCategory.LENGTH).let { it.from.key to it.to.key })
        assertEquals("celsius" to "fahrenheit", state.conversion(UnitCategory.TEMPERATURE).let { it.from.key to it.to.key })
        // A valid selection next to invalid ones is still restored.
        assertEquals("watt" to "metric_horsepower", state.conversion(UnitCategory.POWER).let { it.from.key to it.to.key })
    }

    @Test
    fun keypadInputUpdatesTheResultImmediately() = runTest {
        val vm = newViewModel()
        vm.openCategory(UnitCategory.LENGTH)
        vm.press(NumericKey.D1, NumericKey.D0)
        assertEquals("10", vm.openConversion().amount.text)
        assertEquals("32.8083989501", vm.openConversion().resultText)

        vm.press(NumericKey.Decimal, NumericKey.D5)
        assertEquals("10.5", vm.openConversion().amount.text)
        assertEquals("34.4488188976", vm.openConversion().resultText)

        vm.press(NumericKey.Backspace, NumericKey.Backspace)
        assertEquals("10", vm.openConversion().amount.text)

        vm.press(NumericKey.Clear)
        assertEquals("0", vm.openConversion().resultText)
    }

    @Test
    fun temperatureAcceptsNegativeInputOtherCategoriesDoNot() = runTest {
        val vm = newViewModel()
        vm.openCategory(UnitCategory.TEMPERATURE)
        vm.press(NumericKey.D4, NumericKey.D0, NumericKey.ToggleSign)
        assertEquals("-40", vm.openConversion().amount.text)
        assertEquals("−40", vm.openConversion().resultText)
        assertFalse(vm.openConversion().isBelowAbsoluteZero)

        vm.press(NumericKey.D0, NumericKey.D0)
        assertEquals("-4000", vm.openConversion().amount.text)
        assertTrue(vm.openConversion().isBelowAbsoluteZero)

        vm.closeCategory()
        vm.openCategory(UnitCategory.MASS)
        vm.press(NumericKey.D5, NumericKey.ToggleSign)
        assertEquals("5", vm.openConversion().amount.text)
        vm.press(NumericKey.DoubleZero)
        assertEquals("500", vm.openConversion().amount.text)
    }

    @Test
    fun swapExchangesUnitsKeepsAmountAndPersists() = runTest {
        val vm = newViewModel()
        vm.openCategory(UnitCategory.LENGTH)
        vm.press(NumericKey.D1, NumericKey.D0)
        vm.swapUnits()
        advanceUntilIdle()
        val swapped = vm.openConversion()
        assertEquals("foot" to "meter", swapped.from.key to swapped.to.key)
        assertEquals("10", swapped.amount.text)
        assertEquals("3.048", swapped.resultText)
        assertEquals(UnitSelection("foot", "meter"), repository.unitSelection("length").first())

        vm.swapUnits()
        advanceUntilIdle()
        assertEquals("meter" to "foot", vm.openConversion().let { it.from.key to it.to.key })
        assertEquals(UnitSelection("meter", "foot"), repository.unitSelection("length").first())
    }

    @Test
    fun pickingTheOppositeUnitSwapsInsteadOfDuplicating() = runTest {
        val vm = newViewModel()
        vm.openCategory(UnitCategory.TEMPERATURE)
        vm.selectFromUnit("fahrenheit")
        assertEquals("fahrenheit" to "celsius", vm.openConversion().let { it.from.key to it.to.key })
        vm.selectToUnit("fahrenheit")
        assertEquals("celsius" to "fahrenheit", vm.openConversion().let { it.from.key to it.to.key })
        vm.selectToUnit("kelvin")
        assertEquals("celsius" to "kelvin", vm.openConversion().let { it.from.key to it.to.key })
    }

    @Test
    fun unknownOrUnchangedUnitSelectionIsIgnoredAndNotPersisted() = runTest {
        val vm = newViewModel()
        vm.openCategory(UnitCategory.VOLUME)
        vm.selectFromUnit("parsec")
        vm.selectToUnit("us_gallon")
        advanceUntilIdle()
        assertEquals("liter" to "us_gallon", vm.openConversion().let { it.from.key to it.to.key })
        assertNull(repository.unitSelection("volume").first())
    }

    @Test
    fun inputIsIgnoredWhileTheGridIsShown() = runTest {
        val vm = newViewModel()
        val before = vm.state.value
        vm.onKey(NumericKey.D7)
        vm.swapUnits()
        vm.selectFromUnit("foot")
        vm.closeCategory()
        assertEquals(before, vm.state.value)
    }

    @Test
    fun amountsAreKeptPerCategoryWhileNavigating() = runTest {
        val vm = newViewModel()
        vm.openCategory(UnitCategory.LENGTH)
        vm.press(NumericKey.D3)
        vm.closeCategory()
        vm.openCategory(UnitCategory.MASS)
        assertEquals("0", vm.openConversion().amount.text)
        vm.press(NumericKey.D2)
        vm.closeCategory()
        vm.openCategory(UnitCategory.LENGTH)
        assertEquals("3", vm.openConversion().amount.text)
        assertEquals("2", vm.state.value.conversion(UnitCategory.MASS).amount.text)
    }

    @Test
    fun storageWriteFailureDoesNotCrashAndKeepsInMemoryState() = runTest {
        val vm = ConverterViewModel(SettingsRepository(FailingWritePreferencesDataStore()))
        advanceUntilIdle()
        assertTrue(vm.state.value.isLoaded)

        vm.openCategory(UnitCategory.LENGTH)
        vm.selectToUnit("mile")
        vm.swapUnits()
        vm.press(NumericKey.D2)
        advanceUntilIdle()
        val length = vm.openConversion()
        assertEquals("mile" to "meter", length.from.key to length.to.key)
        assertEquals("3,218.688", length.resultText)

        vm.closeCategory()
        advanceUntilIdle()
        assertNull(vm.state.value.openCategory)
        assertEquals(UnitCategory.LENGTH, vm.state.value.lastCategory)
    }

    @Test
    fun restoredUnitsRequireBothKeysOfTheSameCategory() {
        assertNull(ConverterViewModel.restoredUnits(UnitCategory.SPEED, null))
        assertNull(ConverterViewModel.restoredUnits(UnitCategory.SPEED, UnitSelection("knot", "furlong_per_fortnight")))
        assertNull(ConverterViewModel.restoredUnits(UnitCategory.SPEED, UnitSelection("meter", "knot")))
        val units = ConverterViewModel.restoredUnits(UnitCategory.SPEED, UnitSelection("knot", "meter_per_second"))
        assertEquals("knot" to "meter_per_second", units?.let { it.first.key to it.second.key })
    }
}
