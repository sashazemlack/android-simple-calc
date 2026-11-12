package com.scalc.calculator.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.scalc.calculator.calc.engine.AngleMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException

/** User-selected appearance. [SYSTEM] follows the Android system light/dark setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Persisted general preferences shared by both flavors (no personal information). */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val angleMode: AngleMode = AngleMode.DEG,
    /** Key of the last selected unit-converter category ([com.scalc.calculator.converter.engine.UnitCategory.key]). */
    val unitCategory: String? = null,
    /** Whether the conversion screen of [unitCategory] was open (true) or the category grid (false). */
    val unitScreenOpen: Boolean = false,
)

/** Last selected source/destination unit keys for one unit category. */
data class UnitSelection(val fromKey: String, val toKey: String)

/**
 * Preferences DataStore wrapper. All writes are suspend functions; reads are exposed as flows.
 * Unknown/corrupt enum values fall back to defaults instead of crashing.
 *
 * Storage failures never reach the callers: a failed read first shows the defaults and is then
 * retried with a back-off (the flows stay live, so later successful writes are still delivered);
 * a failed write returns false instead of throwing [IOException].
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val safeData: Flow<Preferences> = flow {
        var delivered = false
        emitAll(
            dataStore.data
                .onEach { delivered = true }
                .retryWhen { cause, attempt ->
                    if (cause !is IOException) return@retryWhen false
                    // Show the defaults rather than nothing; keep the last good values otherwise.
                    if (!delivered) {
                        delivered = true
                        emit(emptyPreferences())
                    }
                    delay(settingsReadRetryDelayMillis(attempt))
                    true
                },
        )
    }

    val settings: Flow<AppSettings> = safeData.map { prefs ->
        AppSettings(
            themeMode = prefs[THEME]?.let { enumValueOrNull<ThemeMode>(it) } ?: ThemeMode.SYSTEM,
            angleMode = prefs[ANGLE]?.let { enumValueOrNull<AngleMode>(it) } ?: AngleMode.DEG,
            unitCategory = prefs[UNIT_CATEGORY],
            unitScreenOpen = prefs[UNIT_SCREEN_OPEN] ?: false,
        )
    }.distinctUntilChanged()

    /**
     * Stores the theme preference.
     * Returns false when the value could not be stored (e.g. full storage); never throws [IOException].
     */
    suspend fun setThemeMode(mode: ThemeMode): Boolean = write { it[THEME] = mode.name }

    /**
     * Stores the calculator angle mode.
     * Returns false when the value could not be stored (e.g. full storage); never throws [IOException].
     */
    suspend fun setAngleMode(mode: AngleMode): Boolean = write { it[ANGLE] = mode.name }

    /**
     * Stores the last unit-converter category and whether its conversion screen was open.
     * Returns false when the value could not be stored (e.g. full storage); never throws [IOException].
     */
    suspend fun setUnitCategory(categoryKey: String?, screenOpen: Boolean): Boolean = write {
        if (categoryKey == null) it.remove(UNIT_CATEGORY) else it[UNIT_CATEGORY] = categoryKey
        it[UNIT_SCREEN_OPEN] = screenOpen && categoryKey != null
    }

    /** Last units chosen for [categoryKey], or null if the user never changed them. */
    fun unitSelection(categoryKey: String): Flow<UnitSelection?> = safeData.map { prefs ->
        val from = prefs[unitFromKey(categoryKey)]
        val to = prefs[unitToKey(categoryKey)]
        if (from != null && to != null) UnitSelection(from, to) else null
    }.distinctUntilChanged()

    /** All persisted unit selections keyed by category key. */
    val unitSelections: Flow<Map<String, UnitSelection>> = safeData.map { prefs ->
        prefs.asMap().keys
            .map { it.name }
            .filter { it.startsWith(UNIT_FROM_PREFIX) }
            .mapNotNull { name ->
                val category = name.removePrefix(UNIT_FROM_PREFIX)
                val from = prefs[unitFromKey(category)]
                val to = prefs[unitToKey(category)]
                if (from != null && to != null) category to UnitSelection(from, to) else null
            }
            .toMap()
    }.distinctUntilChanged()

    /**
     * Stores the units chosen for [categoryKey].
     * Returns false when the value could not be stored (e.g. full storage); never throws [IOException].
     */
    suspend fun setUnitSelection(categoryKey: String, selection: UnitSelection): Boolean = write {
        it[unitFromKey(categoryKey)] = selection.fromKey
        it[unitToKey(categoryKey)] = selection.toKey
    }

    private suspend fun write(block: (MutablePreferences) -> Unit): Boolean = try {
        dataStore.edit { block(it) }
        true
    } catch (e: IOException) {
        false
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val ANGLE = stringPreferencesKey("angle_mode")
        val UNIT_CATEGORY = stringPreferencesKey("unit_category")
        val UNIT_SCREEN_OPEN = booleanPreferencesKey("unit_screen_open")
        const val UNIT_FROM_PREFIX = "unit_from_"
        const val UNIT_TO_PREFIX = "unit_to_"
        fun unitFromKey(category: String) = stringPreferencesKey(UNIT_FROM_PREFIX + category)
        fun unitToKey(category: String) = stringPreferencesKey(UNIT_TO_PREFIX + category)
    }
}

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }

/** Back-off before re-reading the settings after the [attempt]-th (0-based) read failure: 0.5 s doubling up to 30 s. */
internal fun settingsReadRetryDelayMillis(attempt: Long): Long =
    minOf(30_000L, 500L shl attempt.coerceIn(0L, 6L).toInt())
