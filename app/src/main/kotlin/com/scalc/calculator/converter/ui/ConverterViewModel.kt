package com.scalc.calculator.converter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scalc.calculator.appContainer
import com.scalc.calculator.common.NumericKey
import com.scalc.calculator.converter.engine.MeasureUnit
import com.scalc.calculator.converter.engine.UnitCatalog
import com.scalc.calculator.converter.engine.UnitCategory
import com.scalc.calculator.settings.SettingsRepository
import com.scalc.calculator.settings.UnitSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Activity-scoped state holder of the unit converter (the tab leaves composition when another
 * tab is selected, so typed amounts must outlive it).
 *
 * Persisted through [SettingsRepository]: the last category and whether its conversion screen
 * was open, and the from/to units of every category. Typed amounts are kept in memory only.
 */
class ConverterViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _state = MutableStateFlow(ConverterUiState())
    val state: StateFlow<ConverterUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { restore() }
    }

    /** Shows the conversion screen of [category] and remembers it as the last used category. */
    fun openCategory(category: UnitCategory) {
        if (!_state.value.isLoaded) return
        _state.update { it.copy(openCategory = category, lastCategory = category) }
        persist { setUnitCategory(category.key, screenOpen = true) }
    }

    /** Returns to the category grid (the category stays highlighted as last used). */
    fun closeCategory() {
        val category = _state.value.openCategory ?: return
        _state.update { it.copy(openCategory = null) }
        persist { setUnitCategory(category.key, screenOpen = false) }
    }

    /** Applies a keypad key to the amount of the open category. Negative input is temperature-only. */
    fun onKey(key: NumericKey) {
        updateOpenConversion { conversion ->
            if (key == NumericKey.ToggleSign && !conversion.category.allowsNegative) {
                conversion
            } else {
                conversion.copy(amount = conversion.amount.press(key))
            }
        }
    }

    /**
     * Sets the source unit. Picking the unit currently used as destination swaps the two instead
     * of producing a pointless identity conversion.
     */
    fun selectFromUnit(unitKey: String) {
        updateUnits { conversion ->
            val unit = UnitCatalog.find(conversion.category, unitKey) ?: return@updateUnits conversion
            when (unit.key) {
                conversion.from.key -> conversion
                conversion.to.key -> conversion.copy(from = conversion.to, to = conversion.from)
                else -> conversion.copy(from = unit)
            }
        }
    }

    /** Sets the destination unit; picking the current source unit swaps the two (see [selectFromUnit]). */
    fun selectToUnit(unitKey: String) {
        updateUnits { conversion ->
            val unit = UnitCatalog.find(conversion.category, unitKey) ?: return@updateUnits conversion
            when (unit.key) {
                conversion.to.key -> conversion
                conversion.from.key -> conversion.copy(from = conversion.to, to = conversion.from)
                else -> conversion.copy(to = unit)
            }
        }
    }

    /** Exchanges source and destination units; the typed amount is kept. */
    fun swapUnits() {
        updateUnits { it.copy(from = it.to, to = it.from) }
    }

    private suspend fun restore() {
        val settings = settingsRepository.settings.first()
        val selections = settingsRepository.unitSelections.first()
        val lastCategory = UnitCategory.fromKey(settings.unitCategory)
        _state.update { current ->
            current.copy(
                isLoaded = true,
                lastCategory = lastCategory,
                openCategory = if (settings.unitScreenOpen) lastCategory else null,
                conversions = current.conversions.mapValues { (category, conversion) ->
                    val units = restoredUnits(category, selections[category.key]) ?: return@mapValues conversion
                    conversion.copy(from = units.first, to = units.second)
                },
            )
        }
    }

    /** Updates the open category's conversion; returns the new value, or null if nothing changed. */
    private fun updateOpenConversion(transform: (UnitConversion) -> UnitConversion): UnitConversion? {
        val current = _state.value
        val category = current.openCategory ?: return null
        val before = current.conversion(category)
        val after = transform(before)
        if (after == before) return null
        _state.value = current.copy(conversions = current.conversions + (category to after))
        return after
    }

    private fun updateUnits(transform: (UnitConversion) -> UnitConversion) {
        val changed = updateOpenConversion(transform) ?: return
        val selection = UnitSelection(fromKey = changed.from.key, toKey = changed.to.key)
        persist { setUnitSelection(changed.category.key, selection) }
    }

    /**
     * Best-effort write of a unit/category choice. A storage failure (e.g. a full disk) must not
     * crash the app on an ordinary tap: the in-memory state stays authoritative for this session.
     * Only [IOException] is caught, so cancellation and programming errors still propagate.
     */
    private fun persist(write: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch {
            try {
                settingsRepository.write()
            } catch (e: IOException) {
                // Not persisted; the next successful write stores the then-current choice.
            }
        }
    }

    companion object {
        /**
         * Units persisted for [category], or null (use defaults) when nothing was saved or either
         * key is unknown, e.g. after a unit was renamed in a newer app version.
         */
        internal fun restoredUnits(category: UnitCategory, saved: UnitSelection?): Pair<MeasureUnit, MeasureUnit>? {
            if (saved == null) return null
            val from = UnitCatalog.find(category, saved.fromKey) ?: return null
            val to = UnitCatalog.find(category, saved.toKey) ?: return null
            return from to to
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                    "ConverterViewModel requires the Application in its creation extras"
                }
                ConverterViewModel(application.appContainer.settingsRepository)
            }
        }
    }
}
