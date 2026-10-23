package io.github.imlemos.clock.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val appearance: Flow<AppearanceSettings> = dataStore.data
        .map { prefs ->
            AppearanceSettings(
                themeMode = prefs[Keys.THEME_MODE]
                    ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                    ?: ThemeMode.SYSTEM,
                accentArgb = prefs[Keys.ACCENT_ARGB],
                reduceTransparency = prefs[Keys.REDUCE_TRANSPARENCY] ?: false,
            )
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setAccent(argb: Int?) {
        dataStore.edit { prefs ->
            if (argb == null) prefs.remove(Keys.ACCENT_ARGB) else prefs[Keys.ACCENT_ARGB] = argb
        }
    }

    override suspend fun setReduceTransparency(enabled: Boolean) {
        dataStore.edit { it[Keys.REDUCE_TRANSPARENCY] = enabled }
    }

    override val reliabilityIntroShown: Flow<Boolean> = dataStore.data
        .map { it[Keys.RELIABILITY_INTRO_SHOWN] ?: false }
        .distinctUntilChanged()

    override suspend fun setReliabilityIntroShown() {
        dataStore.edit { it[Keys.RELIABILITY_INTRO_SHOWN] = true }
    }

    // Stored names are persisted on users' devices: never rename them.
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_ARGB = intPreferencesKey("accent_argb")
        val REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
        val RELIABILITY_INTRO_SHOWN = booleanPreferencesKey("reliability_intro_shown")
    }
}
