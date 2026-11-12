package com.scalc.calculator.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.scalc.calculator.calc.engine.AngleMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    /**
     * In-memory preferences store. The first [failingReads] collections of [data] fail with
     * [readError] (after emitting [emitBeforeFailure], if set); later ones follow [state].
     */
    private class FakeStore(
        initial: Preferences = emptyPreferences(),
        private var failingReads: Int = 0,
        private val readError: Throwable = IOException("Simulated read failure"),
        private val emitBeforeFailure: Preferences? = null,
    ) : DataStore<Preferences> {
        val state = MutableStateFlow(initial)
        var failWrites = false
        var collections = 0

        override val data: Flow<Preferences> = flow {
            collections++
            if (failingReads > 0) {
                failingReads--
                emitBeforeFailure?.let { emit(it) }
                throw readError
            }
            emitAll(state)
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (failWrites) throw IOException("Simulated storage failure (disk full)")
            return transform(state.value).also { state.value = it }
        }
    }

    @Test
    fun successfulWritesReturnTrueAndAreRead() = runTest {
        val repo = SettingsRepository(FakeStore())
        assertTrue(repo.setThemeMode(ThemeMode.DARK))
        assertTrue(repo.setAngleMode(AngleMode.RAD))
        assertTrue(repo.setUnitCategory("mass", screenOpen = true))
        assertTrue(repo.setUnitSelection("mass", UnitSelection("stone", "pound")))
        val settings = repo.settings.first()
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(AngleMode.RAD, settings.angleMode)
        assertEquals("mass", settings.unitCategory)
        assertTrue(settings.unitScreenOpen)
        assertEquals(UnitSelection("stone", "pound"), repo.unitSelection("mass").first())
    }

    @Test
    fun failedWritesReturnFalseInsteadOfThrowing() = runTest {
        val store = FakeStore().apply { failWrites = true }
        val repo = SettingsRepository(store)
        assertFalse(repo.setThemeMode(ThemeMode.DARK))
        assertFalse(repo.setAngleMode(AngleMode.RAD))
        assertFalse(repo.setUnitCategory("mass", screenOpen = true))
        assertFalse(repo.setUnitSelection("mass", UnitSelection("stone", "pound")))
        assertEquals(AppSettings(), repo.settings.first())

        store.failWrites = false
        assertTrue(repo.setThemeMode(ThemeMode.LIGHT))
        assertEquals(ThemeMode.LIGHT, repo.settings.first().themeMode)
    }

    @Test
    fun readFailureShowsDefaultsAndLaterWritesAreStillDelivered() = runTest {
        val store = FakeStore(failingReads = 1)
        val repo = SettingsRepository(store)
        val seen = mutableListOf<AppSettings>()
        val job = launch { repo.settings.collect { seen += it } }

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(AppSettings()), seen)
        assertEquals(2, store.collections) // retried after the failure

        assertTrue(repo.setThemeMode(ThemeMode.DARK))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(ThemeMode.DARK, seen.last().themeMode)
        assertEquals(listOf(ThemeMode.SYSTEM, ThemeMode.DARK), seen.map { it.themeMode })
        job.cancel()
    }

    @Test
    fun readFailureAfterAValueKeepsTheLastValues() = runTest {
        val dark = mutablePreferencesOf(stringPreferencesKey("theme_mode") to ThemeMode.DARK.name)
        val store = FakeStore(initial = dark, failingReads = 1, emitBeforeFailure = dark)
        val repo = SettingsRepository(store)
        val seen = mutableListOf<ThemeMode>()
        val job = launch { repo.settings.collect { seen += it.themeMode } }

        advanceTimeBy(5_000)
        runCurrent()
        // No flash of the default (System) theme between the failure and the retry.
        assertEquals(listOf(ThemeMode.DARK), seen)
        job.cancel()
    }

    @Test
    fun otherReadErrorsAreNotSwallowed() = runTest {
        val repo = SettingsRepository(FakeStore(failingReads = 1, readError = IllegalStateException("bug")))
        try {
            repo.settings.first()
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("bug", e.message)
        }
    }

    @Test
    fun readRetryBacksOffUpToThirtySeconds() {
        assertEquals(500L, settingsReadRetryDelayMillis(0))
        assertEquals(1_000L, settingsReadRetryDelayMillis(1))
        assertEquals(16_000L, settingsReadRetryDelayMillis(5))
        assertEquals(30_000L, settingsReadRetryDelayMillis(6))
        assertEquals(30_000L, settingsReadRetryDelayMillis(1_000))
    }
}
