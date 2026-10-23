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
import android.util.Log
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

    override val volumeButtonAction: Flow<VolumeButtonAction> = dataStore.data
        .map { prefs ->
            prefs[Keys.VOLUME_BUTTON_ACTION]
                ?.let { name -> VolumeButtonAction.entries.firstOrNull { it.name == name } }
                ?: VolumeButtonAction.OFF
        }
        .distinctUntilChanged()

    override suspend fun setVolumeButtonAction(action: VolumeButtonAction) {
        dataStore.edit { it[Keys.VOLUME_BUTTON_ACTION] = action.name }
    }

    override val noticeMinutes: Flow<Set<Int>> = dataStore.data
        .map { prefs -> prefs[Keys.NOTICE_MINUTES]?.toIntSet() ?: DEFAULT_NOTICE_MINUTES }
        .distinctUntilChanged()

    override suspend fun setNoticeMinutes(minutes: Set<Int>) {
        dataStore.edit { it[Keys.NOTICE_MINUTES] = minutes.sorted().joinToString(",") }
    }

    override val pendingUndo: Flow<List<Long>> = dataStore.data
        .map { prefs -> prefs[Keys.PENDING_UNDO].orEmpty().split(',').mapNotNull(String::toLongOrNull) }
        .distinctUntilChanged()

    override suspend fun addPendingUndo(trashId: Long) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_UNDO].orEmpty().split(',').filter(String::isNotBlank)
            prefs[Keys.PENDING_UNDO] = (current + trashId.toString()).joinToString(",")
        }
        Log.i(TAG, "Pending undo added: $trashId")
    }

    override suspend fun removePendingUndo(trashId: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.PENDING_UNDO] = prefs[Keys.PENDING_UNDO].orEmpty().split(',')
                .filter { it.isNotBlank() && it != trashId.toString() }
                .joinToString(",")
        }
    }

    override suspend fun clearPendingUndo() {
        Log.i(TAG, "Pending undo cleared")
        dataStore.edit { it.remove(Keys.PENDING_UNDO) }
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
        val VOLUME_BUTTON_ACTION = stringPreferencesKey("volume_button_action")
        val NOTICE_MINUTES = stringPreferencesKey("notice_minutes")
        val PENDING_UNDO = stringPreferencesKey("pending_undo")
    }

    private fun String.toIntSet(): Set<Int> = split(',').mapNotNull(String::toIntOrNull).toSet()

    companion object {
        val DEFAULT_NOTICE_MINUTES = setOf(30)
        private const val TAG = "Settings"
    }
}
