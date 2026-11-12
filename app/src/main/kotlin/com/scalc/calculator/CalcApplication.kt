package com.scalc.calculator

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.scalc.calculator.calc.history.HistoryData
import com.scalc.calculator.calc.history.HistoryRepository
import com.scalc.calculator.calc.history.HistorySerializer
import com.scalc.calculator.settings.SettingsRepository

/** Application root shared by both flavors. Flavor-specific services live in the flavor source sets. */
class CalcApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * Minimal manual dependency container (no DI framework). DataStore instances must be singletons
 * per file, so they are created exactly once here.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { appContext.preferencesDataStoreFile("settings") },
            ),
        )
    }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(
            DataStoreFactory.create(
                serializer = HistorySerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { HistoryData() },
                produceFile = { appContext.dataStoreFile("history.bin") },
            ),
        )
    }
}

/** Convenience accessor for ViewModel factories. */
val Context.appContainer: AppContainer
    get() = (applicationContext as CalcApplication).container
